package neurasamu.build.samulab.linux

import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.security.SecureRandom

enum class ModelKind(val label: String, val badge: String) {
    TEXT_VISION("Text / Vision (LLM)", "LLM"),
    IMAGE_GEN("Image Generation", "IMAGE"),
    AUDIO_TTS("Audio / TTS", "AUDIO"),
    VIDEO_GEN("Video Generation", "VIDEO");

    companion object {
        fun fromName(name: String?): ModelKind =
            entries.firstOrNull { it.name.equals(name, ignoreCase = true) } ?: TEXT_VISION

        fun guessFromFile(file: File): ModelKind {
            val n = file.name.lowercase()
            return when {
                n.endsWith(".onnx") || n.contains("piper") || n.contains("kokoro") || n.contains("tts") || n.contains("oute") -> AUDIO_TTS
                n.contains("wan2") || n.contains("mochi") || n.contains("cogvideo") || n.contains("ltx-video") || n.contains("hunyuan-video") -> VIDEO_GEN
                n.endsWith(".safetensors") || n.endsWith(".ckpt") || n.contains("flux") || n.contains("sdxl") || n.contains("sd3") || n.contains("stable-diffusion") -> IMAGE_GEN
                else -> TEXT_VISION
            }
        }
    }
}

enum class ThinkingSupportMode(val label: String) {
    AUTO("Auto Detect"),
    ENABLED("Force Supported"),
    DISABLED("No Thinking");

    companion object {
        fun fromName(name: String?): ThinkingSupportMode =
            entries.firstOrNull { it.name.equals(name, ignoreCase = true) } ?: AUTO
    }
}

data class ExtraModelFile(
    val role: String, // mmproj, vae, clip, t5, lora, voice_config, other
    val path: String
) {
    fun toJson(): JSONObject = JSONObject().apply {
        put("role", role)
        put("path", path)
    }

    companion object {
        fun fromJson(o: JSONObject): ExtraModelFile = ExtraModelFile(
            role = o.optString("role", "other"),
            path = o.optString("path", "").trim()
        )
    }
}

data class ModelProfile(
    val modelPath: String,
    val kind: ModelKind = ModelKind.TEXT_VISION,
    val mmprojPath: String = "",
    val extraFiles: List<ExtraModelFile> = emptyList(),
    val gpuLayers: Int = -1, // -1 = Auto hardware, 0 = CPU only, >0 = custom
    val thinkingMode: ThinkingSupportMode = ThinkingSupportMode.AUTO,
    val customArgs: String = ""
) {
    fun hasVision(): Boolean = mmprojPath.isNotBlank() && File(mmprojPath).isFile

    fun extraByRole(role: String): String? =
        extraFiles.firstOrNull { it.role.equals(role, ignoreCase = true) && it.path.isNotBlank() && File(it.path).isFile }?.path

    fun toJson(): JSONObject = JSONObject().apply {
        put("modelPath", modelPath)
        put("kind", kind.name)
        put("mmprojPath", mmprojPath)
        put("extraFiles", JSONArray().apply { extraFiles.forEach { put(it.toJson()) } })
        put("gpuLayers", gpuLayers)
        put("thinkingMode", thinkingMode.name)
        put("customArgs", customArgs)
    }

    companion object {
        fun defaultFor(file: File): ModelProfile {
            val kind = ModelKind.guessFromFile(file)
            val extras = mutableListOf<ExtraModelFile>()
            if (kind == ModelKind.AUDIO_TTS) {
                val cfg = File(file.absolutePath + ".json")
                if (cfg.isFile) extras.add(ExtraModelFile("voice_config", cfg.absolutePath))
            }
            return ModelProfile(
                modelPath = file.absoluteFile.normalize().path,
                kind = kind,
                extraFiles = extras
            )
        }

        fun fromJson(o: JSONObject): ModelProfile {
            val arr = o.optJSONArray("extraFiles")
            val extras = buildList {
                if (arr != null) {
                    for (i in 0 until arr.length()) {
                        arr.optJSONObject(i)?.let {
                            val ef = ExtraModelFile.fromJson(it)
                            if (ef.path.isNotBlank()) add(ef)
                        }
                    }
                }
            }
            return ModelProfile(
                modelPath = o.optString("modelPath", "").trim(),
                kind = ModelKind.fromName(o.optString("kind", "TEXT_VISION")),
                mmprojPath = o.optString("mmprojPath", "").trim(),
                extraFiles = extras,
                gpuLayers = o.optInt("gpuLayers", -1),
                thinkingMode = ThinkingSupportMode.fromName(o.optString("thinkingMode", "AUTO")),
                customArgs = o.optString("customArgs", "").trim()
            )
        }
    }
}

data class AppSettings(
    val port: Int = 8080,
    val lanAccess: Boolean = true,
    val threadsOverride: Int = 0,
    val contextSize: Int = 8000, // 0 = model native max, any positive value allowed (unlimited!)
    val requireAuth: Boolean = false,
    val apiKey: String = generateKey(),
    val modelApiName: String = "samu-local",
    val modelPaths: List<String> = emptyList(),
    val modelProfiles: Map<String, ModelProfile> = emptyMap()
) {
    fun profileFor(file: File): ModelProfile {
        val norm = file.absoluteFile.normalize().path
        return modelProfiles[norm] ?: ModelProfile.defaultFor(file)
    }

    fun withProfile(profile: ModelProfile): AppSettings {
        val norm = File(profile.modelPath).absoluteFile.normalize().path
        val clean = profile.copy(modelPath = norm)
        return copy(modelProfiles = modelProfiles + (norm to clean))
    }

    fun validated(): AppSettings = copy(
        port = port.coerceIn(1024, 65535),
        threadsOverride = threadsOverride.coerceIn(0, 512),
        contextSize = contextSize.coerceAtLeast(0),
        apiKey = apiKey.trim().let { if (it.length >= 8) it else generateKey() },
        modelApiName = modelApiName.trim().ifEmpty { "samu-local" },
        modelPaths = modelPaths.map { it.trim() }.filter { it.isNotEmpty() }.distinct()
    )

    fun toJson(): String = JSONObject().apply {
        put("port", port)
        put("lanAccess", lanAccess)
        put("threadsOverride", threadsOverride)
        put("contextSize", contextSize)
        put("requireAuth", requireAuth)
        put("apiKey", apiKey)
        put("modelApiName", modelApiName)
        put("modelPaths", JSONArray(modelPaths))
        val profObj = JSONObject()
        modelProfiles.forEach { (k, v) -> profObj.put(k, v.toJson()) }
        put("modelProfiles", profObj)
    }.toString(2)

    companion object {
        private val secureRandom = SecureRandom()

        fun generateKey(): String {
            val chars = "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789"
            return "sk-samu-" + (1..24).map { chars[secureRandom.nextInt(chars.length)] }.joinToString("")
        }

        fun fromJson(text: String): AppSettings {
            val o = JSONObject(text)
            val arr = o.optJSONArray("modelPaths")
            val paths = buildList {
                if (arr != null) {
                    for (i in 0 until arr.length()) {
                        val p = arr.optString(i, "").trim()
                        if (p.isNotEmpty()) add(p)
                    }
                }
            }
            val profMap = mutableMapOf<String, ModelProfile>()
            val profObj = o.optJSONObject("modelProfiles")
            if (profObj != null) {
                for (key in profObj.keys()) {
                    profObj.optJSONObject(key)?.let { pJson ->
                        val p = ModelProfile.fromJson(pJson)
                        val normKey = if (p.modelPath.isNotBlank()) p.modelPath else key
                        profMap[normKey] = p.copy(modelPath = normKey)
                    }
                }
            }
            return AppSettings(
                port = o.optInt("port", 8080),
                lanAccess = o.optBoolean("lanAccess", true),
                threadsOverride = o.optInt("threadsOverride", 0),
                contextSize = o.optInt("contextSize", 8000),
                requireAuth = o.optBoolean("requireAuth", false),
                apiKey = o.optString("apiKey", "").trim().let { if (it.length >= 8) it else generateKey() },
                modelApiName = o.optString("modelApiName", "samu-local"),
                modelPaths = paths,
                modelProfiles = profMap
            ).validated()
        }
    }
}

object SettingsStore {
    val appDir: Path = Platform.appDataDir()
    private val settingsFile: Path = appDir.resolve("settings.json")
    private val modelsDir: Path = appDir.resolve("models")
    private val mediaDir: Path = appDir.resolve("media")
    private val enginesDir: Path = appDir.resolve("engines")

    fun ensureDirs() {
        Files.createDirectories(appDir)
        Files.createDirectories(modelsDir)
        Files.createDirectories(mediaDir.resolve("images"))
        Files.createDirectories(mediaDir.resolve("audio"))
        Files.createDirectories(mediaDir.resolve("videos"))
        Files.createDirectories(enginesDir)
    }

    fun load(): AppSettings {
        ensureDirs()
        return if (Files.exists(settingsFile)) {
            runCatching { AppSettings.fromJson(Files.readString(settingsFile)) }
                .getOrElse { AppSettings().validated() }
        } else {
            val s = AppSettings().validated()
            save(s)
            s
        }
    }

    fun save(settings: AppSettings) {
        ensureDirs()
        val safe = settings.validated()
        val tmpFile = appDir.resolve("settings.json.tmp")
        try {
            Files.writeString(tmpFile, safe.toJson())
            Files.move(tmpFile, settingsFile, StandardCopyOption.REPLACE_EXISTING)
        } catch (_: Exception) {
            Files.writeString(settingsFile, safe.toJson())
        }
    }

    fun modelsDir(): Path {
        ensureDirs()
        return modelsDir
    }

    fun mediaDir(): Path {
        ensureDirs()
        return mediaDir
    }

    fun enginesDir(): Path {
        ensureDirs()
        return enginesDir
    }
}
