package neurasamu.build.samulab.linux

import org.json.JSONObject
import java.nio.file.Files
import java.nio.file.Path
import kotlin.random.Random

data class AppSettings(
    val port: Int = 8080,
    val lanAccess: Boolean = true,
    val threadsOverride: Int = 0,
    val contextSize: Int = 4096,
    val requireAuth: Boolean = false,
    val apiKey: String = generateKey(),
    val modelApiName: String = "samu-local"
) {
    fun toJson(): String = JSONObject().apply {
        put("port", port)
        put("lanAccess", lanAccess)
        put("threadsOverride", threadsOverride)
        put("contextSize", contextSize)
        put("requireAuth", requireAuth)
        put("apiKey", apiKey)
        put("modelApiName", modelApiName)
    }.toString(2)

    companion object {
        fun generateKey(): String {
            val chars = "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789"
            return "sk-samu-" + (1..24).map { chars[Random.nextInt(chars.length)] }.joinToString("")
        }

        fun fromJson(text: String): AppSettings {
            val o = JSONObject(text)
            return AppSettings(
                port = o.optInt("port", 8080),
                lanAccess = o.optBoolean("lanAccess", true),
                threadsOverride = o.optInt("threadsOverride", 0),
                contextSize = o.optInt("contextSize", 4096),
                requireAuth = o.optBoolean("requireAuth", false),
                apiKey = o.optString("apiKey", generateKey()),
                modelApiName = o.optString("modelApiName", "samu-local")
            )
        }
    }
}

object SettingsStore {
    val appDir: Path = Path.of(System.getProperty("user.home"), ".local", "share", "samu-lab")
    private val settingsFile: Path = appDir.resolve("settings.json")
    private val modelsDir: Path = appDir.resolve("models")

    fun ensureDirs() {
        Files.createDirectories(modelsDir)
    }

    fun load(): AppSettings {
        ensureDirs()
        return if (Files.exists(settingsFile)) {
            runCatching { AppSettings.fromJson(Files.readString(settingsFile)) }
                .getOrElse { AppSettings() }
        } else {
            val s = AppSettings()
            save(s)
            s
        }
    }

    fun save(settings: AppSettings) {
        ensureDirs()
        Files.writeString(settingsFile, settings.toJson())
    }

    fun modelsDir(): Path {
        ensureDirs()
        return modelsDir
    }
}
