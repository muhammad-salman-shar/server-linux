package neurasamu.build.samulab.linux

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

class EngineManager {
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    private val healthClient = OkHttpClient.Builder()
        .connectTimeout(2, TimeUnit.SECONDS)
        .readTimeout(2, TimeUnit.SECONDS)
        .build()

    @Volatile private var process: Process? = null
    private val running = AtomicBoolean(false)
    private val starting = AtomicBoolean(false)
    private val downloading = AtomicBoolean(false)

    val isRunning: Boolean
        get() = running.get() && process?.isAlive == true

    val isBinaryInstalled: Boolean
        get() {
            val binary = SettingsStore.appDir.resolve("llama-server").toFile()
            return binary.exists() && binary.canExecute()
        }

    fun ensureEngineAndStart(
        modelPath: Path,
        settings: AppSettings,
        onProgress: (String) -> Unit,
        onReady: (Boolean, String?) -> Unit,
        onUnexpectedExit: (Int) -> Unit
    ) {
        scope.launch {
            if (!isBinaryInstalled) {
                onProgress("Preparing built-in engine...")
                val bundledOk = runCatching { installFromBundledResources() }.getOrDefault(false)
                if (!bundledOk) {
                    downloadEngine(
                        onProgress = onProgress,
                        onDone = { ok, err ->
                            if (ok) {
                                onProgress("Starting engine...")
                                start(modelPath, settings, onReady, onUnexpectedExit)
                            } else {
                                onReady(false, "Auto-setup failed: ${err ?: "unknown error"}")
                            }
                        }
                    )
                    return@launch
                }
            }
            onProgress("Starting engine...")
            start(modelPath, settings, onReady, onUnexpectedExit)
        }
    }

    private fun installFromBundledResources(): Boolean {
        val resDirProp = System.getProperty("compose.application.resources.dir") ?: return false
        val resRoot = File(resDirProp)
        if (!resRoot.exists()) return false

        val bundledTar = resRoot.walkTopDown().find { it.isFile && it.name == "llama-engine.tar.gz" }
        if (bundledTar != null) {
            SettingsStore.ensureDirs()
            val proc = ProcessBuilder(
                "tar", "-xzf", bundledTar.absolutePath, "-C", SettingsStore.appDir.toAbsolutePath().toString()
            ).redirectErrorStream(true).start()
            if (proc.waitFor() == 0) {
                val destBin = SettingsStore.appDir.resolve("llama-server").toFile()
                destBin.setExecutable(true, false)
                runCatching { ProcessBuilder("chmod", "+x", destBin.absolutePath).start().waitFor() }
                if (destBin.exists() && destBin.canExecute()) return true
            }
        }

        val bundledBin = resRoot.walkTopDown().find { it.isFile && it.name == "llama-server" }
            ?: return false

        SettingsStore.ensureDirs()
        val srcDir = bundledBin.parentFile
        srcDir.listFiles()?.forEach { file ->
            val shouldCopy = file.name == "llama-server" || file.name.contains(".so")
            if (shouldCopy && (file.isFile || Files.isSymbolicLink(file.toPath()))) {
                val dest = SettingsStore.appDir.resolve(file.name)
                Files.deleteIfExists(dest)
                if (Files.isSymbolicLink(file.toPath())) {
                    val target = Files.readSymbolicLink(file.toPath())
                    runCatching { Files.createSymbolicLink(dest, target) }
                        .onFailure { Files.copy(file.toPath(), dest, StandardCopyOption.REPLACE_EXISTING) }
                } else {
                    Files.copy(file.toPath(), dest, StandardCopyOption.REPLACE_EXISTING)
                }
            }
        }
        val destBin = SettingsStore.appDir.resolve("llama-server").toFile()
        destBin.setExecutable(true, false)
        runCatching { ProcessBuilder("chmod", "+x", destBin.absolutePath).start().waitFor() }
        return destBin.exists() && destBin.canExecute()
    }

    fun start(
        modelPath: Path,
        settings: AppSettings,
        onReady: (Boolean, String?) -> Unit = { _, _ -> },
        onUnexpectedExit: (Int) -> Unit = {}
    ) {
        if (isRunning) {
            onReady(true, null)
            return
        }
        if (!starting.compareAndSet(false, true)) {
            onReady(false, "Engine is already starting...")
            return
        }

        val binary = SettingsStore.appDir.resolve("llama-server").toFile()
        if (!binary.exists() || !binary.canExecute()) {
            starting.set(false)
            onReady(false, "Engine binary not ready at ${binary.absolutePath}")
            return
        }
        if (!modelPath.toFile().exists()) {
            starting.set(false)
            onReady(false, "Model file not found: $modelPath")
            return
        }

        val safeSettings = settings.validated()
        val cores = Runtime.getRuntime().availableProcessors()
        val threads = if (safeSettings.threadsOverride > 0) safeSettings.threadsOverride
        else (cores / 2).coerceAtLeast(1)

        val cmd = listOf(
            binary.absolutePath,
            "-m", modelPath.toAbsolutePath().toString(),
            "--host", "127.0.0.1",
            "--port", "8091",
            "-c", safeSettings.contextSize.toString(),
            "-t", threads.toString(),
            "-tb", threads.toString()
        )

        try {
            SettingsStore.ensureDirs()
            val logFile = SettingsStore.appDir.resolve("engine.log").toFile()
            if (logFile.exists() && logFile.length() > 5L * 1024L * 1024L) {
                logFile.writeText("")
            }

            val pb = ProcessBuilder(cmd)
            pb.directory(SettingsStore.appDir.toFile())
            val env = pb.environment()
            val appDirPath = SettingsStore.appDir.toAbsolutePath().toString()
            val existingLd = env["LD_LIBRARY_PATH"].orEmpty()
            env["LD_LIBRARY_PATH"] = if (existingLd.isBlank()) appDirPath else "$appDirPath:$existingLd"
            pb.redirectErrorStream(true)
            pb.redirectOutput(ProcessBuilder.Redirect.appendTo(logFile))

            val proc = pb.start()
            process = proc
            running.set(true)

            val readySignaled = AtomicBoolean(false)
            fun signalReady(ok: Boolean, err: String?) {
                if (readySignaled.compareAndSet(false, true)) {
                    starting.set(false)
                    onReady(ok, err)
                }
            }

            scope.launch {
                val code = runCatching { proc.waitFor() }.getOrDefault(-1)
                if (process === proc) {
                    process = null
                }
                if (running.compareAndSet(true, false)) {
                    if (!readySignaled.get()) {
                        signalReady(false, "Engine exited unexpectedly (code=$code). Check engine.log")
                    } else {
                        onUnexpectedExit(code)
                    }
                }
            }

            scope.launch {
                val ok = waitForEngineReady(proc, 120_000)
                if (ok && running.get() && proc.isAlive) {
                    signalReady(true, null)
                } else {
                    if (running.compareAndSet(true, false)) {
                        runCatching { proc.destroy() }
                        if (process === proc) process = null
                    }
                    signalReady(false, "Engine did not respond within 120s. Check engine.log")
                }
            }
        } catch (e: Exception) {
            running.set(false)
            starting.set(false)
            process = null
            onReady(false, "Failed to start engine: ${e.message}")
        }
    }

    fun stop() {
        running.set(false)
        starting.set(false)
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

    private suspend fun waitForEngineReady(proc: Process, timeoutMs: Long): Boolean {
        val deadline = System.currentTimeMillis() + timeoutMs
        val req = Request.Builder().url("http://127.0.0.1:8091/health").get().build()
        while (System.currentTimeMillis() < deadline) {
            if (!running.get() || !proc.isAlive) return false
            try {
                healthClient.newCall(req).execute().use { resp ->
                    if (resp.code == 200 || resp.code == 404) {
                        return true
                    }
                }
            } catch (_: Exception) {
                // Engine still loading model
            }
            delay(500)
        }
        return false
    }

    fun downloadEngine(onProgress: (String) -> Unit, onDone: (Boolean, String?) -> Unit) {
        if (!downloading.compareAndSet(false, true)) {
            onDone(false, "Engine setup already in progress")
            return
        }
        scope.launch {
            val tarFile = SettingsStore.appDir.resolve("llama.tar.gz").toFile()
            val extractRoot = SettingsStore.appDir.resolve("extract_tmp").toFile()
            try {
                SettingsStore.ensureDirs()
                val arch = System.getProperty("os.arch").orEmpty().lowercase()
                val isArm = arch.contains("aarch64") || arch.contains("arm64")
                val targetSuffix = if (isArm) "-bin-ubuntu-arm64.tar.gz" else "-bin-ubuntu-x64.tar.gz"

                val client = OkHttpClient.Builder()
                    .connectTimeout(30, TimeUnit.SECONDS)
                    .readTimeout(120, TimeUnit.SECONDS)
                    .followRedirects(true)
                    .build()

                onProgress("Finding latest engine...")
                val apiReq = Request.Builder()
                    .url("https://api.github.com/repos/ggml-org/llama.cpp/releases?per_page=10")
                    .header("User-Agent", "SaMu-Lab-Linux")
                    .header("Accept", "application/vnd.github+json")
                    .build()

                val downloadUrl = client.newCall(apiReq).execute().use { resp ->
                    if (!resp.isSuccessful) throw Exception("GitHub API error: HTTP ${resp.code}")
                    val releases = JSONArray(resp.body?.string() ?: throw Exception("Empty GitHub releases response"))
                    var found: String? = null
                    for (r in 0 until releases.length()) {
                        val rel = releases.getJSONObject(r)
                        if (rel.optBoolean("draft", false)) continue
                        val assets = rel.optJSONArray("assets") ?: continue
                        for (i in 0 until assets.length()) {
                            val asset = assets.getJSONObject(i)
                            val name = asset.optString("name", "")
                            if (name.startsWith("llama-") && name.endsWith(targetSuffix)) {
                                found = asset.optString("browser_download_url", "")
                                break
                            }
                        }
                        if (!found.isNullOrBlank()) break
                    }
                    found?.takeIf { it.isNotBlank() }
                        ?: throw Exception("No Linux binary matching llama-*$targetSuffix found")
                }

                onProgress("Downloading engine ($arch)...")
                val dlReq = Request.Builder()
                    .url(downloadUrl)
                    .header("User-Agent", "SaMu-Lab-Linux")
                    .build()

                client.newCall(dlReq).execute().use { resp ->
                    if (!resp.isSuccessful) throw Exception("Download failed: HTTP ${resp.code}")
                    val body = resp.body ?: throw Exception("Empty download body")
                    body.byteStream().use { input ->
                        tarFile.outputStream().use { output ->
                            input.copyTo(output)
                        }
                    }
                }

                onProgress("Extracting engine...")
                if (extractRoot.exists()) extractRoot.deleteRecursively()
                extractRoot.mkdirs()

                val tarProc = ProcessBuilder("tar", "-xzf", tarFile.absolutePath, "-C", extractRoot.absolutePath)
                    .redirectErrorStream(true)
                    .start()
                val tarExit = tarProc.waitFor()
                if (tarExit != 0) {
                    val out = runCatching { tarProc.inputStream.bufferedReader().readText() }.getOrDefault("")
                    throw Exception("Extraction failed (exit $tarExit): ${out.take(200)}")
                }

                onProgress("Setting up engine...")
                val bin = extractRoot.walkTopDown().find { it.isFile && it.name == "llama-server" }
                    ?: throw Exception("llama-server binary not found in archive")

                val binDir = bin.parentFile
                binDir.listFiles()?.forEach { file ->
                    val shouldCopy = file.name == "llama-server" || file.name.contains(".so")
                    if (shouldCopy && (file.isFile || Files.isSymbolicLink(file.toPath()))) {
                        val dest = SettingsStore.appDir.resolve(file.name)
                        Files.deleteIfExists(dest)
                        if (Files.isSymbolicLink(file.toPath())) {
                            val linkTarget = Files.readSymbolicLink(file.toPath())
                            runCatching { Files.createSymbolicLink(dest, linkTarget) }
                                .onFailure { Files.copy(file.toPath(), dest, StandardCopyOption.REPLACE_EXISTING) }
                        } else {
                            Files.copy(file.toPath(), dest, StandardCopyOption.REPLACE_EXISTING)
                        }
                    }
                }

                val destBin = SettingsStore.appDir.resolve("llama-server").toFile()
                destBin.setExecutable(true, false)
                runCatching { ProcessBuilder("chmod", "+x", destBin.absolutePath).start().waitFor() }

                if (!destBin.exists() || !destBin.canExecute()) {
                    throw Exception("Failed to mark engine executable")
                }

                onDone(true, null)
            } catch (e: Exception) {
                onDone(false, e.message ?: "Unknown error")
            } finally {
                runCatching { tarFile.delete() }
                runCatching { extractRoot.deleteRecursively() }
                downloading.set(false)
            }
        }
    }

    fun shutdown() {
        stop()
        scope.cancel()
        runCatching { healthClient.dispatcher.executorService.shutdown() }
        runCatching { healthClient.connectionPool.evictAll() }
    }
}
