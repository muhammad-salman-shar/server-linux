package neurasamu.build.samulab.linux

import okhttp3.Call
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.io.File
import java.io.InputStreamReader
import java.nio.charset.StandardCharsets
import java.util.Base64
import java.util.concurrent.TimeUnit

class ChatClient {
    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(0, TimeUnit.MILLISECONDS)
        .writeTimeout(120, TimeUnit.SECONDS)
        .build()

    @Volatile private var activeCall: Call? = null
    @Volatile private var cancelledByUser: Boolean = false

    fun abort() {
        cancelledByUser = true
        runCatching { activeCall?.cancel() }
        activeCall = null
    }

    private fun resolveEndpoint(baseUrl: String, suffix: String): String {
        val base = sanitizeApiUrl(baseUrl).trimEnd('/')
        return when {
            base.endsWith(suffix) -> base
            base.endsWith("/v1") -> "$base$suffix"
            else -> "$base/v1$suffix"
        }
    }

    fun streamChat(
        api: ApiConfig,
        history: List<ChatMessage>,
        globalInstructions: String?,
        supportsThinking: Boolean = false,
        thinkingEnabled: Boolean = true,
        thinkingEffort: ThinkingEffort = ThinkingEffort.HIGH,
        onDelta: (String) -> Unit,
        onComplete: (fullText: String, wasStopped: Boolean, error: String?) -> Unit
    ) {
        cancelledByUser = false
        val base = sanitizeApiUrl(api.baseUrl).trimEnd('/')
        if (base.isBlank()) {
            onComplete("", false, "API Base URL is empty.")
            return
        }
        val endpoint = resolveEndpoint(base, "/chat/completions")

        val messagesArray = JSONArray()

        val sysParts = listOfNotNull(
            globalInstructions?.trim()?.takeIf { it.isNotEmpty() },
            api.systemPrompt.trim().takeIf { it.isNotEmpty() }
        )
        if (sysParts.isNotEmpty()) {
            messagesArray.put(JSONObject().apply {
                put("role", "system")
                put("content", sysParts.joinToString("\n\n"))
            })
        }

        for (msg in history) {
            if (msg.isError) continue
            val role = if (msg.role == "assistant") "assistant" else "user"
            if (role == "user" && msg.attachments.isNotEmpty()) {
                val contentParts = JSONArray()
                val textBuilder = StringBuilder(msg.content)
                var hasMultimodalPart = false

                for (att in msg.attachments) {
                    val file = File(att.path)
                    val analyzed = FileAnalyzer.analyze(file)
                    when (analyzed.kind) {
                        AttachmentKind.IMAGE -> {
                            if (!analyzed.base64Data.isNullOrBlank()) {
                                hasMultimodalPart = true
                                contentParts.put(JSONObject().apply {
                                    put("type", "image_url")
                                    put("image_url", JSONObject().put("url", "data:${analyzed.mimeType};base64,${analyzed.base64Data}"))
                                })
                            }
                        }

                        AttachmentKind.AUDIO -> {
                            if (!analyzed.base64Data.isNullOrBlank()) {
                                hasMultimodalPart = true
                                contentParts.put(JSONObject().apply {
                                    put("type", "input_audio")
                                    put("input_audio", JSONObject().apply {
                                        put("data", analyzed.base64Data)
                                        put("format", analyzed.audioFormat ?: "wav")
                                    })
                                })
                            } else if (!analyzed.extractedText.isNullOrBlank()) {
                                textBuilder.append("\n\n").append(analyzed.extractedText)
                            }
                        }

                        else -> {
                            if (!analyzed.extractedText.isNullOrBlank()) {
                                textBuilder.append("\n\n[Attached ${analyzed.kind.name}: ${att.name}]\n")
                                textBuilder.append(analyzed.extractedText)
                            }
                        }
                    }
                }

                val finalPromptText = textBuilder.toString().ifBlank { "Analyze the attached file(s)." }
                if (hasMultimodalPart) {
                    val ordered = JSONArray()
                    ordered.put(JSONObject().apply {
                        put("type", "text")
                        put("text", finalPromptText)
                    })
                    for (i in 0 until contentParts.length()) {
                        ordered.put(contentParts.get(i))
                    }
                    messagesArray.put(JSONObject().apply {
                        put("role", "user")
                        put("content", ordered)
                    })
                } else {
                    messagesArray.put(JSONObject().apply {
                        put("role", "user")
                        put("content", finalPromptText)
                    })
                }
            } else {
                messagesArray.put(JSONObject().apply {
                    put("role", role)
                    put("content", msg.content)
                })
            }
        }

        val payloadObj = JSONObject().apply {
            put("model", api.modelName.ifBlank { "samu-local" })
            put("messages", messagesArray)
            put("stream", true)
            if (supportsThinking) {
                val enableThink = thinkingEnabled && thinkingEffort != ThinkingEffort.OFF
                put("chat_template_kwargs", JSONObject().put("enable_thinking", enableThink))
                put("reasoning_effort", if (enableThink) thinkingEffort.apiValue else "none")
                if (!enableThink) {
                    put("reasoning_budget", 0)
                }
            }
        }

        val reqBuilder = Request.Builder()
            .url(endpoint)
            .header("Content-Type", "application/json")
            .header("Accept", "text/event-stream")
            .header("Accept-Encoding", "identity")
            .header("Cache-Control", "no-cache")
            .post(payloadObj.toString().toRequestBody("application/json".toMediaType()))

        if (api.apiKey.isNotBlank()) {
            reqBuilder.header("Authorization", "Bearer ${api.apiKey.trim()}")
        }

        val call = client.newCall(reqBuilder.build())
        activeCall = call

        val buffer = StringBuilder()
        var inReasoningBlock = false
        var lastEmit = 0L

        try {
            call.execute().use { resp ->
                if (!resp.isSuccessful) {
                    val errBody = runCatching { resp.body?.string() }.getOrNull().orEmpty()
                    val parsedErr = runCatching {
                        val j = JSONObject(errBody)
                        val errObj = j.optJSONObject("error")
                        errObj?.optString("message")?.ifBlank { null }
                            ?: j.optString("error").ifBlank { null }
                    }.getOrNull()
                    val msg = parsedErr ?: "HTTP ${resp.code}: ${errBody.take(200)}"
                    onComplete("", false, msg)
                    return
                }

                val body = resp.body ?: run {
                    onComplete("", false, "Empty response from API")
                    return
                }

                val contentType = body.contentType()?.toString().orEmpty().lowercase()
                if (contentType.contains("application/json")) {
                    val text = body.string()
                    val parsed = runCatching {
                        val j = JSONObject(text)
                        val msgObj = j.getJSONArray("choices").getJSONObject(0).getJSONObject("message")
                        val r = msgObj.optString("reasoning_content", "").trim()
                        val c = msgObj.optString("content", "")
                        if (r.isNotEmpty() && !c.contains("<think>")) "<think>\n$r\n</think>\n\n$c" else c
                    }.getOrDefault("")
                    if (parsed.isNotEmpty()) {
                        onDelta(parsed)
                        onComplete(parsed, false, null)
                    } else {
                        onComplete("", false, "Unexpected JSON response")
                    }
                    return
                }

                BufferedReader(InputStreamReader(body.byteStream(), StandardCharsets.UTF_8), 256).use { reader ->
                    var line: String?
                    while (reader.readLine().also { line = it } != null) {
                        if (cancelledByUser) break
                        val trimmed = line?.trim().orEmpty()
                        if (!trimmed.startsWith("data:")) continue
                        val data = trimmed.removePrefix("data:").trim()
                        if (data == "[DONE]") break
                        if (data.isEmpty()) continue

                        val pair = runCatching {
                            val obj = JSONObject(data)
                            val choices = obj.optJSONArray("choices")
                            if (choices != null && choices.length() > 0) {
                                val delta = choices.getJSONObject(0).optJSONObject("delta")
                                val r = delta?.optString("reasoning_content", "").orEmpty()
                                val c = delta?.optString("content", "").orEmpty()
                                r to c
                            } else "" to ""
                        }.getOrDefault("" to "")

                        val reasoningPart = pair.first
                        val contentPart = pair.second
                        var appended = false

                        if (reasoningPart.isNotEmpty()) {
                            if (!inReasoningBlock) {
                                buffer.append("<think>\n")
                                inReasoningBlock = true
                            }
                            buffer.append(reasoningPart)
                            appended = true
                        }
                        if (contentPart.isNotEmpty()) {
                            if (inReasoningBlock) {
                                buffer.append("\n</think>\n\n")
                                inReasoningBlock = false
                            }
                            buffer.append(contentPart)
                            appended = true
                        }

                        if (appended) {
                            val now = System.currentTimeMillis()
                            if (lastEmit == 0L || now - lastEmit >= 25L) {
                                onDelta(buffer.toString())
                                lastEmit = now
                            }
                        }
                    }
                }

                if (inReasoningBlock) {
                    buffer.append("\n</think>")
                }
                val finalText = buffer.toString()
                if (finalText.isNotEmpty()) onDelta(finalText)

                if (cancelledByUser) {
                    val stoppedText = if (finalText.isBlank()) "[stopped]" else "$finalText\n\n[stopped]"
                    onComplete(stoppedText, true, null)
                } else {
                    onComplete(finalText, false, null)
                }
            }
        } catch (e: Exception) {
            if (inReasoningBlock) buffer.append("\n</think>")
            val partial = buffer.toString()
            if (cancelledByUser) {
                val stoppedText = if (partial.isBlank()) "[stopped]" else "$partial\n\n[stopped]"
                onComplete(stoppedText, true, null)
            } else if (partial.isNotBlank()) {
                onComplete("$partial\n\n[connection lost]", false, e.message ?: "Connection lost")
            } else {
                onComplete("", false, e.message ?: "Request failed")
            }
        } finally {
            if (activeCall === call) activeCall = null
        }
    }

    fun requestImageGeneration(api: ApiConfig, prompt: String): Result<GeneratedMedia> {
        return runCatching {
            val endpoint = resolveEndpoint(api.baseUrl, "/images/generations")
            val bodyJson = JSONObject().apply {
                put("model", api.modelName.ifBlank { "samu-image" })
                put("prompt", prompt)
                put("n", 1)
                put("size", "512x512")
            }.toString()

            val rb = Request.Builder()
                .url(endpoint)
                .header("Content-Type", "application/json")
                .post(bodyJson.toRequestBody("application/json".toMediaType()))
            if (api.apiKey.isNotBlank()) rb.header("Authorization", "Bearer ${api.apiKey.trim()}")

            client.newCall(rb.build()).execute().use { resp ->
                val text = resp.body?.string().orEmpty()
                if (!resp.isSuccessful) {
                    val msg = runCatching { JSONObject(text).optString("error") }.getOrNull().orEmpty().ifBlank { "HTTP ${resp.code}: ${text.take(200)}" }
                    throw Exception(msg)
                }
                val j = JSONObject(text)
                val item = j.getJSONArray("data").getJSONObject(0)
                val localPath = item.optString("local_path", "")
                val remoteUrl = item.optString("url", "")
                val b64 = item.optString("b64_json", "")
                val outFile = if (localPath.isNotBlank() && File(localPath).isFile) {
                    File(localPath)
                } else {
                    val f = SettingsStore.mediaDir().resolve("images/gen_${System.currentTimeMillis()}.png").toFile()
                    f.parentFile?.mkdirs()
                    if (b64.isNotBlank()) {
                        f.writeBytes(Base64.getDecoder().decode(b64))
                    } else if (remoteUrl.isNotBlank()) {
                        downloadBytes(remoteUrl, f)
                    } else throw Exception("API returned neither image bytes nor URL")
                    f
                }
                GeneratedMedia(kind = "image", localPath = outFile.absolutePath, remoteUrl = remoteUrl, prompt = prompt, mimeType = "image/png")
            }
        }
    }

    fun requestAudioGeneration(api: ApiConfig, textInput: String): Result<GeneratedMedia> {
        return runCatching {
            val endpoint = resolveEndpoint(api.baseUrl, "/audio/speech")
            val bodyJson = JSONObject().apply {
                put("model", api.modelName.ifBlank { "samu-tts" })
                put("input", textInput)
                put("voice", "alloy")
            }.toString()

            val rb = Request.Builder()
                .url(endpoint)
                .header("Content-Type", "application/json")
                .post(bodyJson.toRequestBody("application/json".toMediaType()))
            if (api.apiKey.isNotBlank()) rb.header("Authorization", "Bearer ${api.apiKey.trim()}")

            client.newCall(rb.build()).execute().use { resp ->
                if (!resp.isSuccessful) {
                    val err = runCatching { resp.body?.string() }.getOrNull().orEmpty()
                    val msg = runCatching { JSONObject(err).optString("error") }.getOrNull().orEmpty().ifBlank { "HTTP ${resp.code}: ${err.take(200)}" }
                    throw Exception(msg)
                }
                val localHeader = resp.header("X-SaMu-Local-Path").orEmpty()
                val remoteHeader = resp.header("X-SaMu-Media-Url").orEmpty()
                val bytes = resp.body?.bytes() ?: throw Exception("Empty audio response")
                val outFile = if (localHeader.isNotBlank() && File(localHeader).isFile) {
                    File(localHeader)
                } else {
                    val ext = if (resp.header("Content-Type").orEmpty().contains("mpeg")) "mp3" else "wav"
                    val f = SettingsStore.mediaDir().resolve("audio/gen_${System.currentTimeMillis()}.$ext").toFile()
                    f.parentFile?.mkdirs()
                    f.writeBytes(bytes)
                    f
                }
                GeneratedMedia(kind = "audio", localPath = outFile.absolutePath, remoteUrl = remoteHeader, prompt = textInput, mimeType = "audio/wav")
            }
        }
    }

    fun requestVideoGeneration(api: ApiConfig, prompt: String): Result<GeneratedMedia> {
        return runCatching {
            val endpoint = resolveEndpoint(api.baseUrl, "/videos/generations")
            val bodyJson = JSONObject().apply {
                put("model", api.modelName.ifBlank { "samu-video" })
                put("prompt", prompt)
            }.toString()

            val rb = Request.Builder()
                .url(endpoint)
                .header("Content-Type", "application/json")
                .post(bodyJson.toRequestBody("application/json".toMediaType()))
            if (api.apiKey.isNotBlank()) rb.header("Authorization", "Bearer ${api.apiKey.trim()}")

            client.newCall(rb.build()).execute().use { resp ->
                val text = resp.body?.string().orEmpty()
                if (!resp.isSuccessful) {
                    val msg = runCatching { JSONObject(text).optString("error") }.getOrNull().orEmpty().ifBlank { "HTTP ${resp.code}: ${text.take(200)}" }
                    throw Exception(msg)
                }
                val j = JSONObject(text)
                val item = j.getJSONArray("data").getJSONObject(0)
                val localPath = item.optString("local_path", "")
                val remoteUrl = item.optString("url", "")
                val mime = item.optString("mime", "video/mp4")
                val outFile = if (localPath.isNotBlank() && File(localPath).isFile) {
                    File(localPath)
                } else if (remoteUrl.isNotBlank()) {
                    val f = SettingsStore.mediaDir().resolve("videos/gen_${System.currentTimeMillis()}.mp4").toFile()
                    f.parentFile?.mkdirs()
                    downloadBytes(remoteUrl, f)
                    f
                } else throw Exception("No video returned by API")
                GeneratedMedia(kind = "video", localPath = outFile.absolutePath, remoteUrl = remoteUrl, prompt = prompt, mimeType = mime)
            }
        }
    }

    private fun downloadBytes(url: String, dest: File) {
        val req = Request.Builder().url(url).build()
        client.newCall(req).execute().use { r ->
            if (!r.isSuccessful) throw Exception("Media download failed: HTTP ${r.code}")
            val b = r.body ?: throw Exception("Empty media body")
            b.byteStream().use { inp -> dest.outputStream().use { out -> inp.copyTo(out) } }
        }
    }

    fun shutdown() {
        abort()
        runCatching { client.dispatcher.executorService.shutdown() }
        runCatching { client.connectionPool.evictAll() }
    }
}
