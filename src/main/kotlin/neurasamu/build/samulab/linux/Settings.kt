package neurasamu.build.samulab.linux

import org.json.JSONArray
import org.json.JSONObject
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.security.SecureRandom

data class AppSettings(
    val port: Int = 8080,
    val lanAccess: Boolean = true,
    val threadsOverride: Int = 0,
    val contextSize: Int = 4096,
    val requireAuth: Boolean = false,
    val apiKey: String = generateKey(),
    val modelApiName: String = "samu-local",
    val modelPaths: List<String> = emptyList()
) {
    fun validated(): AppSettings = copy(
        port = port.coerceIn(1024, 65535),
        threadsOverride = threadsOverride.coerceIn(0, 256),
        contextSize = contextSize.coerceIn(512, 131072),
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
            return AppSettings(
                port = o.optInt("port", 8080),
                lanAccess = o.optBoolean("lanAccess", true),
                threadsOverride = o.optInt("threadsOverride", 0),
                contextSize = o.optInt("contextSize", 4096),
                requireAuth = o.optBoolean("requireAuth", false),
                apiKey = o.optString("apiKey", "").trim().let { if (it.length >= 8) it else generateKey() },
                modelApiName = o.optString("modelApiName", "samu-local"),
                modelPaths = paths
            ).validated()
        }
    }
}

object SettingsStore {
    val appDir: Path = Path.of(System.getProperty("user.home"), ".local", "share", "samu-lab")
    private val settingsFile: Path = appDir.resolve("settings.json")
    private val modelsDir: Path = appDir.resolve("models")

    fun ensureDirs() {
        Files.createDirectories(appDir)
        Files.createDirectories(modelsDir)
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
}
