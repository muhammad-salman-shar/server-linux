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
        .writeTimeout(60, TimeUnit.SECONDS)
        .build()

    @Volatile private var activeCall: Call? = null
    @Volatile private var cancelledByUser: Boolean = false

    fun abort() {
        cancelledByUser = true
        runCatching { activeCall?.cancel() }
        activeCall = null
    }

    fun streamChat(
        api: ApiConfig,
        history: List<ChatMessage>,
        globalInstructions: String?,
        onDelta: (String) -> Unit,
        onComplete: (fullText: String, wasStopped: Boolean, error: String?) -> Unit
    ) {
        cancelledByUser = false
        val base = api.baseUrl.trim().trimEnd('/')
        if (base.isBlank()) {
            onComplete("", false, "API Base URL is empty.")
            return
        }
        val endpoint = if (base.endsWith("/chat/completions")) {
            base
        } else if (base.endsWith("/v1")) {
            "$base/chat/completions"
        } else {
            "$base/v1/chat/completions"
        }

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
                val imageAtts = msg.attachments.filter { it.isImage && File(it.path).exists() }
                val fileAtts = msg.attachments.filter { !it.isImage && File(it.path).exists() }

                val textBuilder = StringBuilder(msg.content)
                for (att in fileAtts) {
                    val f = File(att.path)
                    val snippet = runCatching {
                        val bytes = f.readBytes()
                        val limit = bytes.size.coerceAtMost(24 * 1024)
                        String(bytes, 0, limit, StandardCharsets.UTF_8)
                    }.getOrNull()
                    if (!snippet.isNullOrBlank()) {
                        textBuilder.append("\n\n[Attached file: ${att.name}]\n```\n$snippet\n```")
                    }
                }

                if (imageAtts.isNotEmpty()) {
                    val contentParts = JSONArray()
                    contentParts.put(JSONObject().apply {
                        put("type", "text")
                        put("text", textBuilder.toString().ifBlank { "Describe this image." })
                    })
                    for (img in imageAtts) {
                        val file = File(img.path)
                        val b64 = runCatching {
                            Base64.getEncoder().encodeToString(file.readBytes())
                        }.getOrNull() ?: continue
                        val mime = when (file.extension.lowercase()) {
                            "png" -> "image/png"
                            "webp" -> "image/webp"
                            "gif" -> "image/gif"
                            else -> "image/jpeg"
                        }
                        contentParts.put(JSONObject().apply {
                            put("type", "image_url")
                            put("image_url", JSONObject().put("url", "data:$mime;base64,$b64"))
                        })
                    }
                    messagesArray.put(JSONObject().apply {
                        put("role", "user")
                        put("content", contentParts)
                    })
                } else {
                    messagesArray.put(JSONObject().apply {
                        put("role", "user")
                        put("content", textBuilder.toString())
                    })
                }
            } else {
                messagesArray.put(JSONObject().apply {
                    put("role", role)
                    put("content", msg.content)
                })
            }
        }

        val payload = JSONObject().apply {
            put("model", api.modelName.ifBlank { "samu-local" })
            put("messages", messagesArray)
            put("stream", true)
        }.toString()

        val reqBuilder = Request.Builder()
            .url(endpoint)
            .header("Content-Type", "application/json")
            .header("Accept", "text/event-stream")
            .post(payload.toRequestBody("application/json".toMediaType()))

        if (api.apiKey.isNotBlank()) {
            reqBuilder.header("Authorization", "Bearer ${api.apiKey.trim()}")
        }

        val call = client.newCall(reqBuilder.build())
        activeCall = call

        val buffer = StringBuilder()
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
                    // Fallback for non-streaming server response
                    val text = body.string()
                    val parsed = runCatching {
                        val j = JSONObject(text)
                        j.getJSONArray("choices")
                            .getJSONObject(0)
                            .getJSONObject("message")
                            .optString("content", "")
                    }.getOrDefault("")
                    if (parsed.isNotEmpty()) {
                        onDelta(parsed)
                        onComplete(parsed, false, null)
                    } else {
                        onComplete("", false, "Unexpected JSON response")
                    }
                    return
                }

                BufferedReader(InputStreamReader(body.byteStream(), StandardCharsets.UTF_8)).use { reader ->
                    var line: String?
                    while (reader.readLine().also { line = it } != null) {
                        if (cancelledByUser) break
                        val trimmed = line?.trim().orEmpty()
                        if (!trimmed.startsWith("data:")) continue
                        val data = trimmed.removePrefix("data:").trim()
                        if (data == "[DONE]") break
                        if (data.isEmpty()) continue

                        val chunk = runCatching {
                            val obj = JSONObject(data)
                            val choices = obj.optJSONArray("choices")
                            if (choices != null && choices.length() > 0) {
                                val delta = choices.getJSONObject(0).optJSONObject("delta")
                                val reasoning = delta?.optString("reasoning_content", "").orEmpty()
                                val content = delta?.optString("content", "").orEmpty()
                                if (reasoning.isNotEmpty()) reasoning else content
                            } else ""
                        }.getOrDefault("")

                        if (chunk.isNotEmpty()) {
                            buffer.append(chunk)
                            val now = System.currentTimeMillis()
                            if (now - lastEmit >= 80L) {
                                onDelta(buffer.toString())
                                lastEmit = now
                            }
                        }
                    }
                }

                val finalText = buffer.toString()
                if (finalText.isNotEmpty()) {
                    onDelta(finalText)
                }
                if (cancelledByUser) {
                    val stoppedText = if (finalText.isBlank()) "[stopped]" else "$finalText\n\n[stopped]"
                    onComplete(stoppedText, true, null)
                } else {
                    onComplete(finalText, false, null)
                }
            }
        } catch (e: Exception) {
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

    fun shutdown() {
        abort()
        runCatching { client.dispatcher.executorService.shutdown() }
        runCatching { client.connectionPool.evictAll() }
    }
}
