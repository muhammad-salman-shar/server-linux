package neurasamu.build.samulab.linux

import kotlinx.coroutines.CoroutineScope
import okhttp3.OkHttpClient
import okhttp3.Request
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


    fun downloadEngine(onProgress: (String) -> Unit, onDone: (Boolean, String?) -> Unit) {
        scope.launch {
            try {
                val arch = System.getProperty("os.arch").lowercase()
                val url = if (arch.contains("aarch64") || arch.contains("arm64")) {
                    "https://github.com/ggml-org/llama.cpp/releases/download/b12359/llama-b12359-bin-ubuntu-arm64.tar.gz"
                } else {
                    "https://github.com/ggml-org/llama.cpp/releases/download/b12359/llama-b12359-bin-ubuntu-x64.tar.gz"
                }

                onProgress("Downloading engine ($arch)...")
                val client = OkHttpClient.Builder().build()
                val request = Request.Builder().url(url).build()
                val response = client.newCall(request).execute()
                if (!response.isSuccessful) throw Exception("HTTP ${response.code}")

                val body = response.body ?: throw Exception("Empty body")
                val tarFile = SettingsStore.appDir.resolve("llama.tar.gz").toFile()
                body.byteStream().use { input ->
                    tarFile.outputStream().use { output ->
                        input.copyTo(output)
                    }
                }

                onProgress("Extracting engine...")
                val pb = ProcessBuilder("tar", "-xzf", tarFile.absolutePath)
                pb.directory(SettingsStore.appDir.toFile())
                pb.redirectErrorStream(true)
                val proc = pb.start()
                proc.waitFor()

                onProgress("Setting up binary...")
                val extractedDir = SettingsStore.appDir.toFile().listFiles()?.find { it.isDirectory && it.name.startsWith("llama-") }
                val bin = extractedDir?.listFiles()?.find { it.name == "llama-server" }
                
                if (bin != null && bin.exists()) {
                    val dest = SettingsStore.appDir.resolve("llama-server").toFile()
                    if (dest.exists()) dest.delete()
                    bin.copyTo(dest, overwrite = true)
                    
                    ProcessBuilder("chmod", "+x", dest.absolutePath).start().waitFor()
                    
                    tarFile.delete()
                    extractedDir.deleteRecursively()
                    
                    onDone(true, null)
                } else {
                    throw Exception("llama-server binary not found in archive")
                }
            } catch (e: Exception) {
                onDone(false, e.message ?: "Unknown error")
            }
        }
    }

    fun shutdown() {
        stop()
        scope.cancel()
    }
}
