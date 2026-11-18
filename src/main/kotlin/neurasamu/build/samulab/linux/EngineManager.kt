package neurasamu.build.samulab.linux

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.net.InetSocketAddress
import java.net.Socket
import java.nio.file.Path
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

class EngineManager {
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    @Volatile private var process: Process? = null
    private val running = AtomicBoolean(false)

    val isRunning: Boolean
        get() = running.get() && process?.isAlive == true

    fun start(
        modelPath: Path,
        settings: AppSettings,
        onReady: (Boolean, String?) -> Unit = { _, _ -> }
    ) {
        if (isRunning) {
            onReady(true, null)
            return
        }

        val binary = SettingsStore.appDir.resolve("llama-server").toFile()
        if (!binary.exists() || !binary.canExecute()) {
            onReady(false, "llama-server binary missing or not executable at ${binary.absolutePath}")
            return
        }
        if (!modelPath.toFile().exists()) {
            onReady(false, "Model file not found: $modelPath")
            return
        }

        val cores = Runtime.getRuntime().availableProcessors()
        val threads = if (settings.threadsOverride > 0) settings.threadsOverride
        else (cores / 2).coerceAtLeast(1)

        val cmd = listOf(
            binary.absolutePath,
            "-m", modelPath.toAbsolutePath().toString(),
            "--host", "127.0.0.1",
            "--port", "8091",
            "-c", settings.contextSize.toString(),
            "-t", threads.toString(),
            "-tb", threads.toString()
        )

        try {
            val pb = ProcessBuilder(cmd)
            pb.directory(SettingsStore.appDir.toFile())
            pb.redirectErrorStream(true)
            pb.redirectOutput(ProcessBuilder.Redirect.appendTo(
                SettingsStore.appDir.resolve("engine.log").toFile()
            ))

            val proc = pb.start()
            process = proc
            running.set(true)

            scope.launch {
                val code = runCatching { proc.waitFor() }.getOrDefault(-1)
                if (running.get()) {
                    running.set(false)
                    onReady(false, "llama-server exited unexpectedly (code=$code). Check engine.log")
                }
            }

            scope.launch {
                val ok = waitForPort("127.0.0.1", 8091, 120_000)
                if (ok) {
                    onReady(true, null)
                } else {
                    if (running.get()) {
                        running.set(false)
                        runCatching { proc.destroy() }
                        process = null
                    }
                    onReady(false, "Engine did not respond on port 8091 within 120s")
                }
            }
        } catch (e: Exception) {
            running.set(false)
            process = null
            onReady(false, "Failed to start engine: ${e.message}")
        }
    }

    fun stop() {
        running.set(false)
        val p = process
        process = null
        if (p != null) {
            try {
                p.destroy()
                if (!p.waitFor(5, TimeUnit.SECONDS)) p.destroyForcibly()
            } catch (_: Exception) {
                runCatching { p.destroyForcibly() }
            }
        }
    }

    private suspend fun waitForPort(host: String, port: Int, timeoutMs: Long): Boolean {
        val deadline = System.currentTimeMillis() + timeoutMs
        while (System.currentTimeMillis() < deadline) {
            if (!running.get()) return false
            try {
                Socket().use { s ->
                    s.connect(InetSocketAddress(host, port), 1000)
                    return true
                }
            } catch (_: Exception) {
                delay(500)
            }
        }
        return false
    }

    fun shutdown() {
        stop()
        scope.cancel()
    }
}
