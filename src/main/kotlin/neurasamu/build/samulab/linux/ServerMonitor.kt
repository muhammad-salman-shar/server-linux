package neurasamu.build.samulab.linux

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong

data class ServerLogEntry(
    val id: Long,
    val time: String,
    val level: String, // INFO, ACTIVE, OK, ERR
    val message: String
)

data class ServerTelemetry(
    val activeRequests: Int = 0,
    val totalRequests: Int = 0,
    val totalTokens: Long = 0L,
    val liveTokensPerSec: Double = 0.0,
    val currentStatus: String = "Idle",
    val logs: List<ServerLogEntry> = emptyList()
)

class ServerMonitor {
    private val _state = MutableStateFlow(ServerTelemetry())
    val state: StateFlow<ServerTelemetry> = _state.asStateFlow()

    private val activeCount = AtomicInteger(0)
    private val reqCounter = AtomicInteger(0)
    private val logIdGen = AtomicLong(0L)
    private val timeFmt = DateTimeFormatter.ofPattern("HH:mm:ss")

    fun log(level: String, message: String) {
        val entry = ServerLogEntry(
            id = logIdGen.incrementAndGet(),
            time = LocalTime.now().format(timeFmt),
            level = level,
            message = message
        )
        _state.update { s ->
            s.copy(logs = (listOf(entry) + s.logs).take(60))
        }
    }

    fun onRequestStart(endpoint: String, clientIp: String, stream: Boolean): Long {
        val active = activeCount.incrementAndGet()
        val total = reqCounter.incrementAndGet()
        _state.update { s ->
            s.copy(
                activeRequests = active,
                totalRequests = total,
                currentStatus = if (stream) "Streaming response..." else "Processing request..."
            )
        }
        log("ACTIVE", "$endpoint from $clientIp${if (stream) " (stream)" else ""}")
        return System.nanoTime()
    }

    fun onStreamProgress(tokensSoFar: Int, startNanos: Long, deltaTokens: Int) {
        val elapsedSec = ((System.nanoTime() - startNanos) / 1_000_000_000.0).coerceAtLeast(0.05)
        val tps = tokensSoFar / elapsedSec
        _state.update { s ->
            s.copy(
                totalTokens = s.totalTokens + deltaTokens,
                liveTokensPerSec = tps,
                currentStatus = "Generating • ${"%.1f".format(tps)} tok/s"
            )
        }
    }

    fun onRequestEnd(endpoint: String, startNanos: Long, ok: Boolean, detail: String = "") {
        val active = activeCount.decrementAndGet().coerceAtLeast(0)
        val elapsedMs = (System.nanoTime() - startNanos) / 1_000_000L
        _state.update { s ->
            s.copy(
                activeRequests = active,
                currentStatus = if (active > 0) "Processing ($active active)" else "Idle — Ready"
            )
        }
        val suffix = if (detail.isNotBlank()) " • $detail" else ""
        log(if (ok) "OK" else "ERR", "$endpoint done in ${elapsedMs}ms$suffix")
    }

    fun resetSession() {
        activeCount.set(0)
        _state.update { s ->
            s.copy(activeRequests = 0, liveTokensPerSec = 0.0, currentStatus = "Stopped")
        }
    }

    fun markReady() {
        _state.update { it.copy(currentStatus = "Idle — Ready") }
        log("OK", "Server ready and listening")
    }
}
