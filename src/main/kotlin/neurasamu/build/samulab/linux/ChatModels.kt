package neurasamu.build.samulab.linux

import org.json.JSONArray
import org.json.JSONObject
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.util.UUID

const val LOCAL_SAMU_API_ID = "__samu_local_builtin__"

enum class ThinkingEffort(val label: String, val apiValue: String) {
    OFF("Off", "none"),
    LOW("Low", "low"),
    HIGH("High", "medium"),
    MAX("Max", "high");

    companion object {
        fun fromName(name: String?): ThinkingEffort =
            entries.firstOrNull { it.name.equals(name, ignoreCase = true) } ?: HIGH
    }
}

enum class ChatMode(val label: String) {
    CHAT("Chat"),
    IMAGE("Image"),
    AUDIO("Audio"),
    VIDEO("Video")
}

data class ApiConfig(
    val id: String = UUID.randomUUID().toString(),
    val label: String,
    val baseUrl: String,
    val modelName: String,
    val apiKey: String = "",
    val systemPrompt: String = "",
    val isBuiltInLocal: Boolean = false
) {
    fun toJson(): JSONObject = JSONObject().apply {
        put("id", id)
        put("label", label)
        put("baseUrl", baseUrl)
        put("modelName", modelName)
        put("apiKey", apiKey)
        put("systemPrompt", systemPrompt)
    }

    companion object {
        fun fromJson(o: JSONObject): ApiConfig = ApiConfig(
            id = o.optString("id", UUID.randomUUID().toString()),
            label = o.optString("label", "Custom API"),
            baseUrl = sanitizeApiUrl(o.optString("baseUrl", "")),
            modelName = o.optString("modelName", ""),
            apiKey = o.optString("apiKey", ""),
            systemPrompt = o.optString("systemPrompt", ""),
            isBuiltInLocal = false
        )
    }
}

data class ChatAttachment(
    val path: String,
    val name: String,
    val isImage: Boolean,
    val sizeBytes: Long = 0L,
    val kind: String = if (isImage) "image" else "file"
) {
    fun toJson(): JSONObject = JSONObject().apply {
        put("path", path)
        put("name", name)
        put("isImage", isImage)
        put("sizeBytes", sizeBytes)
        put("kind", kind)
    }

    companion object {
        fun fromJson(o: JSONObject): ChatAttachment {
            val isImg = o.optBoolean("isImage", false)
            return ChatAttachment(
                path = o.optString("path", ""),
                name = o.optString("name", "file"),
                isImage = isImg,
                sizeBytes = o.optLong("sizeBytes", 0L),
                kind = o.optString("kind", if (isImg) "image" else "file")
            )
        }
    }
}

data class GeneratedMedia(
    val id: String = UUID.randomUUID().toString(),
    val kind: String, // "image", "audio", "video"
    val localPath: String,
    val remoteUrl: String = "",
    val prompt: String = "",
    val mimeType: String = ""
) {
    fun toJson(): JSONObject = JSONObject().apply {
        put("id", id)
        put("kind", kind)
        put("localPath", localPath)
        put("remoteUrl", remoteUrl)
        put("prompt", prompt)
        put("mimeType", mimeType)
    }

    companion object {
        fun fromJson(o: JSONObject): GeneratedMedia = GeneratedMedia(
            id = o.optString("id", UUID.randomUUID().toString()),
            kind = o.optString("kind", "image"),
            localPath = o.optString("localPath", ""),
            remoteUrl = o.optString("remoteUrl", ""),
            prompt = o.optString("prompt", ""),
            mimeType = o.optString("mimeType", "")
        )
    }
}

data class ChatMessage(
    val id: String = UUID.randomUUID().toString(),
    val role: String, // "user" or "assistant"
    val content: String,
    val attachments: List<ChatAttachment> = emptyList(),
    val media: List<GeneratedMedia> = emptyList(),
    val isError: Boolean = false,
    val timestamp: Long = System.currentTimeMillis()
) {
    fun toJson(): JSONObject = JSONObject().apply {
        put("id", id)
        put("role", role)
        put("content", content)
        put("isError", isError)
        put("timestamp", timestamp)
        put("attachments", JSONArray().apply { attachments.forEach { put(it.toJson()) } })
        put("media", JSONArray().apply { media.forEach { put(it.toJson()) } })
    }

    companion object {
        fun fromJson(o: JSONObject): ChatMessage {
            val attArr = o.optJSONArray("attachments")
            val atts = buildList {
                if (attArr != null) {
                    for (i in 0 until attArr.length()) {
                        attArr.optJSONObject(i)?.let { add(ChatAttachment.fromJson(it)) }
                    }
                }
            }
            val medArr = o.optJSONArray("media")
            val meds = buildList {
                if (medArr != null) {
                    for (i in 0 until medArr.length()) {
                        medArr.optJSONObject(i)?.let { add(GeneratedMedia.fromJson(it)) }
                    }
                }
            }
            return ChatMessage(
                id = o.optString("id", UUID.randomUUID().toString()),
                role = o.optString("role", "user"),
                content = o.optString("content", ""),
                attachments = atts,
                media = meds,
                isError = o.optBoolean("isError", false),
                timestamp = o.optLong("timestamp", System.currentTimeMillis())
            )
        }
    }
}

data class ChatSession(
    val id: String = UUID.randomUUID().toString(),
    val title: String = "New Chat",
    val messages: List<ChatMessage> = emptyList(),
    val updatedAt: Long = System.currentTimeMillis()
) {
    val estimatedTokens: Int
        get() = messages.sumOf { (it.content.length / 4).coerceAtLeast(1) }

    fun toJson(): JSONObject = JSONObject().apply {
        put("id", id)
        put("title", title)
        put("updatedAt", updatedAt)
        put("messages", JSONArray().apply { messages.forEach { put(it.toJson()) } })
    }

    companion object {
        fun fromJson(o: JSONObject): ChatSession {
            val msgArr = o.optJSONArray("messages")
            val msgs = buildList {
                if (msgArr != null) {
                    for (i in 0 until msgArr.length()) {
                        msgArr.optJSONObject(i)?.let { add(ChatMessage.fromJson(it)) }
                    }
                }
            }
            return ChatSession(
                id = o.optString("id", UUID.randomUUID().toString()),
                title = o.optString("title", "New Chat"),
                messages = msgs,
                updatedAt = o.optLong("updatedAt", System.currentTimeMillis())
            )
        }
    }
}

data class ChatPrefs(
    val themeMode: ThemeMode = ThemeMode.DARK,
    val fontScale: Float = 1.0f,
    val customInstructionsEnabled: Boolean = false,
    val customInstructions: String = "",
    val activeApiId: String = LOCAL_SAMU_API_ID,
    val activeChatId: String = "",
    val thinkingEnabled: Boolean = true,
    val thinkingEffort: ThinkingEffort = ThinkingEffort.HIGH
) {
    fun toJson(): String = JSONObject().apply {
        put("themeMode", themeMode.name)
        put("fontScale", fontScale.toDouble())
        put("customInstructionsEnabled", customInstructionsEnabled)
        put("customInstructions", customInstructions)
        put("activeApiId", activeApiId)
        put("activeChatId", activeChatId)
        put("thinkingEnabled", thinkingEnabled)
        put("thinkingEffort", thinkingEffort.name)
    }.toString(2)

    companion object {
        fun fromJson(text: String): ChatPrefs {
            val o = JSONObject(text)
            return ChatPrefs(
                themeMode = ThemeMode.fromName(o.optString("themeMode", "DARK")),
                fontScale = o.optDouble("fontScale", 1.0).toFloat().coerceIn(0.8f, 1.4f),
                customInstructionsEnabled = o.optBoolean("customInstructionsEnabled", false),
                customInstructions = o.optString("customInstructions", ""),
                activeApiId = o.optString("activeApiId", LOCAL_SAMU_API_ID),
                activeChatId = o.optString("activeChatId", ""),
                thinkingEnabled = o.optBoolean("thinkingEnabled", true),
                thinkingEffort = ThinkingEffort.fromName(o.optString("thinkingEffort", "HIGH"))
            )
        }
    }
}

object ChatStore {
    private val apisFile: Path = SettingsStore.appDir.resolve("apis.json")
    private val chatsFile: Path = SettingsStore.appDir.resolve("chats.json")
    private val prefsFile: Path = SettingsStore.appDir.resolve("chat_prefs.json")

    private fun writeAtomic(target: Path, content: String) {
        SettingsStore.ensureDirs()
        val tmp = target.resolveSibling(target.fileName.toString() + ".tmp")
        try {
            Files.writeString(tmp, content)
            Files.move(tmp, target, StandardCopyOption.REPLACE_EXISTING)
        } catch (_: Exception) {
            Files.writeString(target, content)
        }
    }

    fun loadApis(): List<ApiConfig> {
        SettingsStore.ensureDirs()
        if (!Files.exists(apisFile)) return emptyList()
        return runCatching {
            val arr = JSONArray(Files.readString(apisFile))
            buildList {
                for (i in 0 until arr.length()) {
                    arr.optJSONObject(i)?.let { add(ApiConfig.fromJson(it)) }
                }
            }
        }.getOrElse { emptyList() }
    }

    fun saveApis(apis: List<ApiConfig>) {
        val arr = JSONArray().apply {
            apis.filterNot { it.isBuiltInLocal }.forEach { put(it.toJson()) }
        }
        writeAtomic(apisFile, arr.toString(2))
    }

    fun loadChats(): List<ChatSession> {
        SettingsStore.ensureDirs()
        if (!Files.exists(chatsFile)) return emptyList()
        return runCatching {
            val arr = JSONArray(Files.readString(chatsFile))
            buildList {
                for (i in 0 until arr.length()) {
                    arr.optJSONObject(i)?.let { add(ChatSession.fromJson(it)) }
                }
            }
        }.getOrElse { emptyList() }
    }

    fun saveChats(chats: List<ChatSession>) {
        val arr = JSONArray().apply { chats.forEach { put(it.toJson()) } }
        writeAtomic(chatsFile, arr.toString(2))
    }

    fun loadPrefs(): ChatPrefs {
        SettingsStore.ensureDirs()
        if (!Files.exists(prefsFile)) return ChatPrefs()
        return runCatching { ChatPrefs.fromJson(Files.readString(prefsFile)) }
            .getOrElse { ChatPrefs() }
    }

    fun savePrefs(prefs: ChatPrefs) {
        writeAtomic(prefsFile, prefs.toJson())
    }
}
