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

data class EngineCapabilities(
    val supportsThinking: Boolean = false,
    val supportsVision: Boolean = false,
    val modelKind: ModelKind = ModelKind.TEXT_VISION,
    val nativeContext: Int = 0
)

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

    @Volatile var capabilities: EngineCapabilities = EngineCapabilities()
        private set

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
        profile: ModelProfile = settings.profileFor(modelPath.toFile()),
        onProgress: (String) -> Unit,
        onReady: (Boolean, String?) -> Unit,
        onUnexpectedExit: (Int, String) -> Unit
    ) {
        scope.launch {
            onProgress("Checking built-in engine...")
            val bundledFresh = runCatching { syncFromBundledResources() }.getOrDefault(false)
            if (!isBinaryInstalled && !bundledFresh) {
                downloadEngine(
                    onProgress = onProgress,
                    onDone = { ok, err ->
                        if (ok) {
                            onProgress("Starting engine...")
                            start(modelPath, settings, profile, onReady, onUnexpectedExit)
                        } else {
                            onReady(false, "Auto-setup failed: ${err ?: "unknown error"}")
                        }
                    }
                )
                return@launch
            }
            onProgress("Starting engine...")
            start(modelPath, settings, profile, onReady, onUnexpectedExit)
        }
    }

    private fun syncFromBundledResources(): Boolean {
        val resDirProp = System.getProperty("compose.application.resources.dir") ?: return false
        val resRoot = File(resDirProp)
        if (!resRoot.exists()) return false

        val bundledTar = resRoot.walkTopDown().find { it.isFile && it.name == "llama-engine.tar.gz" }
        if (bundledTar != null) {
            SettingsStore.ensureDirs()
            val stampFile = SettingsStore.appDir.resolve(".llama-engine.stamp").toFile()
            val currentStamp = "${bundledTar.length()}_${bundledTar.lastModified()}"
            val destBin = SettingsStore.appDir.resolve("llama-server").toFile()
            if (destBin.exists() && destBin.canExecute() && stampFile.exists() &&
                runCatching { stampFile.readText().trim() == currentStamp }.getOrDefault(false)
            ) {
                return true
            }
            val proc = ProcessBuilder(
                "tar", "-xzf", bundledTar.absolutePath, "-C", SettingsStore.appDir.toAbsolutePath().toString()
            ).redirectErrorStream(true).start()
            if (proc.waitFor() == 0) {
                destBin.setExecutable(true, false)
                runCatching { ProcessBuilder("chmod", "+x", destBin.absolutePath).start().waitFor() }
                if (destBin.exists() && destBin.canExecute()) {
                    runCatching { stampFile.writeText(currentStamp) }
                    return true
                }
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

    private fun killStrayEngineOnPort8091() {
        runCatching {
            ProcessBuilder("pkill", "-f", "llama-server.*--port 8091").start().waitFor(2, TimeUnit.SECONDS)
        }
    }

    private fun readRecentEngineError(logFile: File, startOffset: Long): String {
        return runCatching {
            if (!logFile.exists()) return ""
            val bytes = logFile.readBytes()
            val tail = if (bytes.size > startOffset && startOffset >= 0) {
                String(bytes, startOffset.toInt(), (bytes.size - startOffset).toInt(), Charsets.UTF_8)
            } else {
                String(bytes, (bytes.size - 4096).coerceAtLeast(0), bytes.size.coerceAtMost(4096), Charsets.UTF_8)
            }
            val lines = tail.lines().map { it.trim() }.filter { it.isNotEmpty() }
            val errLine = lines.lastOrNull {
                val l = it.lowercase()
                l.contains("error") || l.contains("failed") || l.contains("cannot") ||
                    l.contains("out of memory") || l.contains("invalid") || l.contains("unknown")
            } ?: lines.lastOrNull().orEmpty()
            errLine.take(180)
        }.getOrDefault("")
    }

    fun start(
        modelPath: Path,
        settings: AppSettings,
        profile: ModelProfile = settings.profileFor(modelPath.toFile()),
        onReady: (Boolean, String?) -> Unit = { _, _ -> },
        onUnexpectedExit: (Int, String) -> Unit = { _, _ -> }
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

        // Clean any orphaned llama-server process still holding port 8091
        killStrayEngineOnPort8091()

        val safeSettings = settings.validated()
        val hw = HardwareDetector.detect()
        val threads = if (safeSettings.threadsOverride > 0) safeSettings.threadsOverride else hw.recommendedThreads

        val cmd = mutableListOf(
            binary.absolutePath,
            "-m", modelPath.toAbsolutePath().toString(),
            "--host", "127.0.0.1",
            "--port", "8091",
            "-c", safeSettings.contextSize.toString(),
            "-t", threads.toString(),
            "-tb", threads.toString(),
            "--jinja"
        )

        // Vision / multimodal projector support (--mmproj)
        if (profile.hasVision()) {
            cmd.add("--mmproj")
            cmd.add(File(profile.mmprojPath).absolutePath)
        }

        // Extra LoRA files if attached
        profile.extraFiles
            .filter { it.role.equals("lora", ignoreCase = true) && File(it.path).isFile }
            .forEach {
                cmd.add("--lora")
                cmd.add(File(it.path).absolutePath)
            }

        // GPU offload only when explicitly set or GPU detected
        val ngl = if (profile.gpuLayers >= 0) profile.gpuLayers else hw.recommendedGpuLayers
        if (ngl > 0) {
            cmd.add("-ngl")
            cmd.add(ngl.toString())
        }

        // Optional user CLI flags
        if (profile.customArgs.isNotBlank()) {
            profile.customArgs.trim().split(Regex("\\s+")).filter { it.isNotBlank() }.forEach { cmd.add(it) }
        }

        try {
            SettingsStore.ensureDirs()
            val logFile = SettingsStore.appDir.resolve("engine.log").toFile()
            if (logFile.exists() && logFile.length() > 5L * 1024L * 1024L) {
                logFile.writeText("")
            }
            val logOffset = if (logFile.exists()) logFile.length() else 0L

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
                    val detail = readRecentEngineError(logFile, logOffset)
                    val reason = if (detail.isNotBlank()) "Engine exited (code=$code): $detail"
                    else "Engine exited unexpectedly (code=$code). Check engine.log"
                    if (!readySignaled.get()) {
                        signalReady(false, reason)
                    } else {
                        onUnexpectedExit(code, detail)
                    }
                }
            }

            scope.launch {
                val ok = waitForEngineReady(proc, 180_000)
                if (ok && running.get() && proc.isAlive) {
                    capabilities = probeCapabilities(modelPath.toFile(), profile)
                    signalReady(true, null)
                } else {
                    val detail = readRecentEngineError(logFile, logOffset)
                    if (running.compareAndSet(true, false)) {
                        runCatching { proc.destroy() }
                        if (process === proc) process = null
                    }
                    val reason = if (detail.isNotBlank()) "Engine failed: $detail"
                    else "Engine did not respond in time. Check engine.log"
                    signalReady(false, reason)
                }
            }
        } catch (e: Exception) {
            running.set(false)
            starting.set(false)
            process = null
            onReady(false, "Failed to start engine: ${e.message}")
        }
    }

    private fun probeCapabilities(modelFile: File, profile: ModelProfile): EngineCapabilities {
        val nameLower = modelFile.name.lowercase()
        val nameHintsThinking = listOf(
            "qwen3", "deepseek-r1", "deepseek_r1", "qwq", "thinking", "reason", "r1-", "o1", "o3", "cogito", "marco-o1", "glm-4.5", "gpt-oss"
        ).any { nameLower.contains(it) }

        var templateHintsThinking = false
        var nativeCtx = 0
        var propsVision = profile.hasVision()

        runCatching {
            val propsUrl = "http" + "://127.0.0.1:8091/props"
            val req = Request.Builder().url(propsUrl).get().build()
            healthClient.newCall(req).execute().use { resp ->
                if (resp.isSuccessful) {
                    val body = resp.body?.string().orEmpty()
                    val j = JSONObject(body)
                    val tmpl = j.optString("chat_template", "").lowercase()
                    if (tmpl.contains("<think>") || tmpl.contains("enable_thinking") || tmpl.contains("reasoning")) {
                        templateHintsThinking = true
                    }
                    val genSettings = j.optJSONObject("default_generation_settings")
                    nativeCtx = genSettings?.optInt("n_ctx", 0) ?: 0
                    val modalities = j.optJSONObject("modalities")
                    if (modalities?.optBoolean("vision", false) == true) {
                        propsVision = true
                    }
                }
            }
        }

        val supportsThinking = when (profile.thinkingMode) {
            ThinkingSupportMode.ENABLED -> true
            ThinkingSupportMode.DISABLED -> false
            ThinkingSupportMode.AUTO -> nameHintsThinking || templateHintsThinking
        }

        return EngineCapabilities(
            supportsThinking = supportsThinking,
            supportsVision = propsVision,
            modelKind = profile.kind,
            nativeContext = nativeCtx
        )
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
        val healthUrl = "http" + "://127.0.0.1:8091/health"
        val req = Request.Builder().url(healthUrl).get().build()
        while (System.currentTimeMillis() < deadline) {
            if (!running.get() || !proc.isAlive) return false
            try {
                healthClient.newCall(req).execute().use { resp ->
                    if (resp.code == 200 || resp.code == 404) {
                        return true
                    }
                }
            } catch (_: Exception) {
                // Model still loading
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
                val apiUrl = "https" + "://api.github.com/repos/ggml-org/llama.cpp/releases?per_page=10"
                val apiReq = Request.Builder()
                    .url(apiUrl)
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
