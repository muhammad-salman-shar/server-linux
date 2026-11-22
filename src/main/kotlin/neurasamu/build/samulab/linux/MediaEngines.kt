package neurasamu.build.samulab.linux

import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import java.io.File
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.util.concurrent.TimeUnit
import java.util.zip.ZipInputStream

class MediaEngines {
    private val dlClient = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(180, TimeUnit.SECONDS)
        .followRedirects(true)
        .build()

    private val sdDir: File get() = SettingsStore.enginesDir().resolve("sd").toFile()
    private val piperDir: File get() = SettingsStore.enginesDir().resolve("piper").toFile()

    fun generateImage(
        prompt: String,
        modelFile: File?,
        profile: ModelProfile?,
        settings: AppSettings,
        width: Int = 512,
        height: Int = 512,
        steps: Int = 20,
        onProgress: (String) -> Unit = {}
    ): Result<File> {
        if (prompt.isBlank()) return Result.failure(Exception("Image prompt is empty."))
        val targetModel = modelFile?.takeIf { it.isFile }
            ?: return Result.failure(Exception("No Image Generation model configured. Add an image model (.gguf / .safetensors / .ckpt) in Server tab and set its type to Image Generation."))

        val sdBin = ensureSdBinary(onProgress).getOrElse { return Result.failure(it) }
        val hw = HardwareDetector.detect()
        val threads = if (settings.threadsOverride > 0) settings.threadsOverride else hw.recommendedThreads

        val outDir = SettingsStore.mediaDir().resolve("images").toFile().apply { mkdirs() }
        val outFile = File(outDir, "img_${System.currentTimeMillis()}.png")

        val cmd = mutableListOf(sdBin.absolutePath)
        val n = targetModel.name.lowercase()
        val isDiffusionOnly = n.contains("flux") || n.contains("sd3") || n.contains("qwen-image") || n.contains("chroma")
        if (isDiffusionOnly && (profile?.extraByRole("vae") != null || profile?.extraByRole("clip") != null || profile?.extraByRole("t5") != null)) {
            cmd.add("--diffusion-model")
            cmd.add(targetModel.absolutePath)
        } else {
            cmd.add("-m")
            cmd.add(targetModel.absolutePath)
        }

        profile?.extraByRole("vae")?.let { cmd.add("--vae"); cmd.add(it) }
        profile?.extraByRole("clip")?.let { cmd.add("--clip_l"); cmd.add(it) }
        profile?.extraByRole("t5")?.let { cmd.add("--t5xxl"); cmd.add(it) }
        profile?.extraByRole("lora")?.let {
            val loraParent = File(it).parentFile?.absolutePath
            if (loraParent != null) {
                cmd.add("--lora-model-dir")
                cmd.add(loraParent)
            }
        }

        cmd.addAll(
            listOf(
                "-p", prompt,
                "-W", width.coerceIn(256, 2048).toString(),
                "-H", height.coerceIn(256, 2048).toString(),
                "--steps", steps.coerceIn(1, 100).toString(),
                "-t", threads.toString(),
                "-o", outFile.absolutePath
            )
        )

        if (!profile?.customArgs.isNullOrBlank()) {
            profile!!.customArgs.trim().split(Regex("\\s+")).filter { it.isNotBlank() }.forEach { cmd.add(it) }
        }

        onProgress("Generating image (${width}x${height})...")
        return runMediaProcess(cmd, sdBin.parentFile, outFile, "Image generation")
    }

    fun generateAudio(
        text: String,
        modelFile: File?,
        profile: ModelProfile?,
        onProgress: (String) -> Unit = {}
    ): Result<File> {
        if (text.isBlank()) return Result.failure(Exception("Audio text is empty."))
        val outDir = SettingsStore.mediaDir().resolve("audio").toFile().apply { mkdirs() }
        val outFile = File(outDir, "aud_${System.currentTimeMillis()}.wav")

        // 1. Neural ONNX Piper voice model
        if (modelFile != null && modelFile.isFile && modelFile.extension.equals("onnx", ignoreCase = true)) {
            val piperBin = ensurePiperBinary(onProgress).getOrElse { return Result.failure(it) }
            val cmd = mutableListOf(piperBin.absolutePath, "--model", modelFile.absolutePath, "--output_file", outFile.absolutePath)
            val cfgPath = profile?.extraByRole("voice_config")
                ?: File(modelFile.absolutePath + ".json").takeIf { it.isFile }?.absolutePath
            if (!cfgPath.isNullOrBlank()) {
                cmd.add("--config")
                cmd.add(cfgPath)
            }
            onProgress("Synthesizing neural voice (Piper)...")
            return runMediaProcess(cmd, piperBin.parentFile, outFile, "Piper TTS", stdinText = text)
        }

        // 2. GGUF TTS model via llama-tts if available
        if (modelFile != null && modelFile.isFile && modelFile.extension.equals("gguf", ignoreCase = true)) {
            val llamaTts = findBinary("llama-tts", SettingsStore.appDir.toFile())
            if (llamaTts != null) {
                val cmd = mutableListOf(llamaTts.absolutePath, "-m", modelFile.absolutePath, "-p", text, "-o", outFile.absolutePath)
                profile?.extraByRole("vocoder")?.let { cmd.add("-mv"); cmd.add(it) }
                onProgress("Synthesizing voice (llama-tts)...")
                return runMediaProcess(cmd, llamaTts.parentFile, outFile, "GGUF TTS")
            }
        }

        // 3. Built-in instant fallback via system espeak-ng / espeak so audio generation works out of the box
        val espeak = findBinary("espeak-ng") ?: findBinary("espeak")
        if (espeak != null) {
            onProgress("Synthesizing audio (system TTS)...")
            val cmd = listOf(espeak.absolutePath, "-w", outFile.absolutePath, text)
            return runMediaProcess(cmd, null, outFile, "Audio synthesis")
        }

        return Result.failure(
            Exception("No TTS model or system voice engine found. Add a Piper .onnx voice model in Server tab (or install espeak-ng).")
        )
    }

    fun generateVideo(
        prompt: String,
        modelFile: File?,
        profile: ModelProfile?,
        settings: AppSettings,
        frames: Int = 17,
        onProgress: (String) -> Unit = {}
    ): Result<File> {
        if (prompt.isBlank()) return Result.failure(Exception("Video prompt is empty."))
        val targetModel = modelFile?.takeIf { it.isFile }
            ?: return Result.failure(Exception("No Video Generation model configured. Add a video model (e.g. Wan2.1/2.2 GGUF) in Server tab and set its type to Video Generation."))

        val sdBin = ensureSdBinary(onProgress).getOrElse { return Result.failure(it) }
        val hw = HardwareDetector.detect()
        val threads = if (settings.threadsOverride > 0) settings.threadsOverride else hw.recommendedThreads

        val outDir = SettingsStore.mediaDir().resolve("videos").toFile().apply { mkdirs() }
        val stamp = System.currentTimeMillis()
        val rawOut = File(outDir, "vid_${stamp}.avi")
        val mp4Out = File(outDir, "vid_${stamp}.mp4")

        val cmd = mutableListOf(
            sdBin.absolutePath,
            "-M", "vid_gen",
            "--diffusion-model", targetModel.absolutePath,
            "-p", prompt,
            "--video-frames", frames.coerceIn(5, 121).toString(),
            "-W", "480",
            "-H", "320",
            "-t", threads.toString(),
            "-o", rawOut.absolutePath
        )

        profile?.extraByRole("vae")?.let { cmd.add("--vae"); cmd.add(it) }
        profile?.extraByRole("t5")?.let { cmd.add("--t5xxl"); cmd.add(it) }
        profile?.extraByRole("clip")?.let { cmd.add("--clip_l"); cmd.add(it) }
        if (!profile?.customArgs.isNullOrBlank()) {
            profile!!.customArgs.trim().split(Regex("\\s+")).filter { it.isNotBlank() }.forEach { cmd.add(it) }
        }

        onProgress("Generating video ($frames frames)...")
        val genRes = runMediaProcess(cmd, sdBin.parentFile, rawOut, "Video generation")
        if (genRes.isFailure) return genRes

        val produced = genRes.getOrThrow()
        // Convert to web-friendly MP4 if ffmpeg is available
        val ffmpeg = findBinary("ffmpeg")
        if (ffmpeg != null && !produced.extension.equals("mp4", ignoreCase = true)) {
            onProgress("Encoding MP4...")
            val conv = runCatching {
                ProcessBuilder(
                    ffmpeg.absolutePath, "-y", "-i", produced.absolutePath,
                    "-c:v", "libx264", "-pix_fmt", "yuv420p", mp4Out.absolutePath
                ).redirectErrorStream(true).start().waitFor()
            }.getOrDefault(-1)
            if (conv == 0 && mp4Out.exists() && mp4Out.length() > 0) {
                runCatching { produced.delete() }
                return Result.success(mp4Out)
            }
        }
        return Result.success(produced)
    }

    private fun runMediaProcess(
        cmd: List<String>,
        workDir: File?,
        expectedOut: File,
        label: String,
        stdinText: String? = null
    ): Result<File> {
        return runCatching {
            val pb = ProcessBuilder(cmd)
            if (workDir != null && workDir.isDirectory) {
                pb.directory(workDir)
                val env = pb.environment()
                val existing = env["LD_LIBRARY_PATH"].orEmpty()
                env["LD_LIBRARY_PATH"] = if (existing.isBlank()) workDir.absolutePath else "${workDir.absolutePath}:$existing"
            }
            pb.redirectErrorStream(true)
            val proc = pb.start()
            if (stdinText != null) {
                proc.outputStream.bufferedWriter().use { w ->
                    w.write(stdinText)
                    w.newLine()
                }
            }
            val output = proc.inputStream.bufferedReader().readText()
            val code = proc.waitFor()
            if (code == 0 && expectedOut.exists() && expectedOut.length() > 0L) {
                expectedOut
            } else {
                val tail = output.lines().map { it.trim() }.filter { it.isNotEmpty() }.takeLast(4).joinToString(" | ")
                throw Exception("$label failed (exit $code): ${tail.take(260).ifBlank { "check model & extra files" }}")
            }
        }
    }

    private fun findBinary(name: String, extraDir: File? = null): File? {
        if (extraDir != null) {
            val local = File(extraDir, name)
            if (local.isFile && local.canExecute()) return local
        }
        val pathDirs = System.getenv("PATH").orEmpty().split(File.pathSeparator)
        for (d in pathDirs) {
            val f = File(d, name)
            if (f.isFile && f.canExecute()) return f
        }
        return null
    }

    private fun ensureSdBinary(onProgress: (String) -> Unit): Result<File> {
        findBinary("sd-cli", sdDir)?.let { return Result.success(it) }
        findBinary("sd", sdDir)?.let { return Result.success(it) }
        findBinary("sd-cli")?.let { return Result.success(it) }
        findBinary("sd")?.let { return Result.success(it) }

        return runCatching {
            sdDir.mkdirs()
            onProgress("Downloading Image/Video engine (stable-diffusion.cpp)...")
            val apiUrl = "https" + "://api.github.com/repos/leejet/stable-diffusion.cpp/releases?per_page=8"
            val req = Request.Builder()
                .url(apiUrl)
                .header("User-Agent", "SaMu-Lab-Linux")
                .header("Accept", "application/vnd.github+json")
                .build()

            val hw = HardwareDetector.detect()
            val assetUrl = dlClient.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) throw Exception("GitHub API error: HTTP ${resp.code}")
                val rels = JSONArray(resp.body?.string() ?: throw Exception("Empty releases"))
                var bestUrl: String? = null
                var fallbackUrl: String? = null
                for (r in 0 until rels.length()) {
                    val rel = rels.getJSONObject(r)
                    if (rel.optBoolean("draft", false)) continue
                    val assets = rel.optJSONArray("assets") ?: continue
                    for (i in 0 until assets.length()) {
                        val a = assets.getJSONObject(i)
                        val name = a.optString("name", "").lowercase()
                        val url = a.optString("browser_download_url", "")
                        if (!name.contains("linux") && !name.contains("ubuntu")) continue
                        if (!name.endsWith(".zip") && !name.endsWith(".tar.gz")) continue
                        if (name.contains("arm64") || name.contains("aarch64")) continue
                        if (hw.hasVulkan && name.contains("vulkan")) {
                            bestUrl = url
                            break
                        }
                        if (fallbackUrl == null && !name.contains("cuda") && !name.contains("rocm") && !name.contains("sycl")) {
                            fallbackUrl = url
                        }
                    }
                    if (bestUrl != null) break
                }
                bestUrl ?: fallbackUrl ?: throw Exception("No compatible Linux stable-diffusion.cpp binary found")
            }

            val archive = File(sdDir, if (assetUrl.endsWith(".zip")) "sd.zip" else "sd.tar.gz")
            downloadToFile(assetUrl, archive)
            onProgress("Extracting Image/Video engine...")
            extractArchive(archive, sdDir)
            runCatching { archive.delete() }

            val bin = sdDir.walkTopDown().firstOrNull {
                it.isFile && (it.name == "sd-cli" || it.name == "sd")
            } ?: throw Exception("sd binary not found inside downloaded archive")

            bin.setExecutable(true, false)
            runCatching { ProcessBuilder("chmod", "+x", bin.absolutePath).start().waitFor() }
            bin
        }
    }

    private fun ensurePiperBinary(onProgress: (String) -> Unit): Result<File> {
        findBinary("piper", piperDir)?.let { return Result.success(it) }
        piperDir.walkTopDown().firstOrNull { it.isFile && it.name == "piper" && it.canExecute() }
            ?.let { return Result.success(it) }
        findBinary("piper")?.let { return Result.success(it) }

        return runCatching {
            piperDir.mkdirs()
            onProgress("Downloading Neural Audio TTS engine (Piper)...")
            val arch = System.getProperty("os.arch").orEmpty().lowercase()
            val target = if (arch.contains("aarch64") || arch.contains("arm64")) "piper_linux_aarch64.tar.gz" else "piper_linux_x86_64.tar.gz"
            val apiUrl = "https" + "://api.github.com/repos/rhasspy/piper/releases?per_page=8"
            val req = Request.Builder()
                .url(apiUrl)
                .header("User-Agent", "SaMu-Lab-Linux")
                .header("Accept", "application/vnd.github+json")
                .build()

            val assetUrl = dlClient.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) throw Exception("GitHub API error: HTTP ${resp.code}")
                val rels = JSONArray(resp.body?.string() ?: throw Exception("Empty releases"))
                var found: String? = null
                for (r in 0 until rels.length()) {
                    val rel = rels.getJSONObject(r)
                    val assets = rel.optJSONArray("assets") ?: continue
                    for (i in 0 until assets.length()) {
                        val a = assets.getJSONObject(i)
                        if (a.optString("name", "") == target) {
                            found = a.optString("browser_download_url", "")
                            break
                        }
                    }
                    if (found != null) break
                }
                found ?: throw Exception("No Piper binary matching $target found")
            }

            val archive = File(piperDir, target)
            downloadToFile(assetUrl, archive)
            onProgress("Extracting Piper TTS engine...")
            extractArchive(archive, piperDir)
            runCatching { archive.delete() }

            val bin = piperDir.walkTopDown().firstOrNull { it.isFile && it.name == "piper" }
                ?: throw Exception("piper executable not found in archive")
            bin.setExecutable(true, false)
            runCatching { ProcessBuilder("chmod", "+x", bin.absolutePath).start().waitFor() }
            bin
        }
    }

    private fun downloadToFile(url: String, dest: File) {
        val req = Request.Builder().url(url).header("User-Agent", "SaMu-Lab-Linux").build()
        dlClient.newCall(req).execute().use { resp ->
            if (!resp.isSuccessful) throw Exception("Download failed: HTTP ${resp.code}")
            val body = resp.body ?: throw Exception("Empty download body")
            body.byteStream().use { input ->
                dest.outputStream().use { output -> input.copyTo(output) }
            }
        }
    }

    private fun extractArchive(archive: File, destDir: File) {
        if (archive.name.endsWith(".zip", ignoreCase = true)) {
            ZipInputStream(archive.inputStream().buffered()).use { zis ->
                var entry = zis.nextEntry
                while (entry != null) {
                    val out = File(destDir, entry.name)
                    if (entry.isDirectory) {
                        out.mkdirs()
                    } else {
                        out.parentFile?.mkdirs()
                        Files.copy(zis, out.toPath(), StandardCopyOption.REPLACE_EXISTING)
                    }
                    entry = zis.nextEntry
                }
            }
        } else {
            val proc = ProcessBuilder("tar", "-xzf", archive.absolutePath, "-C", destDir.absolutePath)
                .redirectErrorStream(true).start()
            val code = proc.waitFor()
            if (code != 0) throw Exception("Archive extraction failed (exit $code)")
        }
    }
}
