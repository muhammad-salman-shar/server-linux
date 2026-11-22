package neurasamu.build.samulab.linux

import fi.iki.elonen.NanoHTTPD
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileInputStream
import java.io.PipedInputStream
import java.io.PipedOutputStream
import java.util.Base64
import java.util.concurrent.TimeUnit

class ApiServer(
    private val settingsProvider: () -> AppSettings,
    private val engine: EngineManager,
    private val mediaEngines: MediaEngines,
    private val modelsProvider: () -> List<File>,
    private val monitor: ServerMonitor? = null
) : NanoHTTPD(
    if (settingsProvider().lanAccess) "0.0.0.0" else "127.0.0.1",
    settingsProvider().port
) {
    private val client = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(0, TimeUnit.MILLISECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .build()

    private val upstreamBase = "http" + "://127.0.0.1:8091"

    override fun useGzipWhenAccepted(r: Response): Boolean {
        val mime = r.mimeType.orEmpty()
        if (mime.contains("text/event-stream", ignoreCase = true) ||
            mime.startsWith("image/", ignoreCase = true) ||
            mime.startsWith("audio/", ignoreCase = true) ||
            mime.startsWith("video/", ignoreCase = true)
        ) return false
        return super.useGzipWhenAccepted(r)
    }

    override fun serve(session: IHTTPSession): Response {
        if (session.method == Method.OPTIONS) {
            return withCors(newFixedLengthResponse(Response.Status.NO_CONTENT, "text/plain", ""))
        }
        val resp = try {
            when {
                session.uri == "/health" && session.method == Method.GET -> handleHealth()
                session.uri == "/v1/models" && session.method == Method.GET -> handleModels(session)
                session.uri == "/v1/chat/completions" && session.method == Method.POST -> handleChat(session)
                session.uri == "/v1/images/generations" && session.method == Method.POST -> handleImageGen(session)
                session.uri == "/v1/audio/speech" && session.method == Method.POST -> handleAudioSpeech(session)
                session.uri == "/v1/videos/generations" && session.method == Method.POST -> handleVideoGen(session)
                session.uri.startsWith("/media/") && session.method == Method.GET -> handleMediaFile(session.uri)
                else -> notFound()
            }
        } catch (e: Exception) {
            monitor?.log("ERR", "${session.uri}: ${e.message ?: "unknown"}")
            errorJson(Response.Status.INTERNAL_ERROR, "Server error: ${e.message ?: "unknown"}")
        }
        return withCors(resp)
    }

    private fun handleHealth(): Response {
        val caps = engine.capabilities
        val hw = HardwareDetector.detect()
        val body = JSONObject().apply {
            put("status", if (engine.isRunning) "ok" else "standby")
            put("engine_running", engine.isRunning)
            put("supports_thinking", caps.supportsThinking)
            put("supports_vision", caps.supportsVision)
            put("native_context", caps.nativeContext)
            put("hardware", hw.tierLabel)
        }.toString()
        return jsonResponse(Response.Status.OK, body)
    }

    private fun handleModels(session: IHTTPSession): Response {
        if (!checkAuth(session)) {
            return errorJson(Response.Status.UNAUTHORIZED, "Invalid or missing API key")
        }
        val current = settingsProvider()
        val arr = JSONArray().put(JSONObject().apply {
            put("id", current.modelApiName)
            put("object", "model")
            put("supports_thinking", engine.capabilities.supportsThinking)
            put("supports_vision", engine.capabilities.supportsVision)
        })
        modelsProvider().forEach { f ->
            val p = current.profileFor(f)
            if (p.kind != ModelKind.TEXT_VISION) {
                arr.put(JSONObject().apply {
                    put("id", f.nameWithoutExtension)
                    put("object", "model")
                    put("kind", p.kind.name.lowercase())
                })
            }
        }
        val body = JSONObject().apply {
            put("object", "list")
            put("data", arr)
        }.toString()
        return jsonResponse(Response.Status.OK, body)
    }

    private fun handleChat(session: IHTTPSession): Response {
        if (!checkAuth(session)) {
            monitor?.log("ERR", "Unauthorized chat request from ${session.remoteIpAddress}")
            return errorJson(Response.Status.UNAUTHORIZED, "Invalid or missing API key")
        }
        if (!engine.isRunning) {
            return errorJson(Response.Status.SERVICE_UNAVAILABLE, "LLM Engine not running. Start a Text/Vision model in Server tab.")
        }

        val bodyMap = mutableMapOf<String, String>()
        session.parseBody(bodyMap)
        val rawBody = bodyMap["postData"] ?: "{}"
        val isStream = runCatching { JSONObject(rawBody).optBoolean("stream", false) }
            .getOrDefault(false)

        val clientIp = session.remoteIpAddress ?: "local"
        val startNanos = monitor?.onRequestStart("/v1/chat/completions", clientIp, isStream)
            ?: System.nanoTime()

        val upstreamReq = Request.Builder()
            .url("$upstreamBase/v1/chat/completions")
            .header("Accept-Encoding", "identity")
            .post(rawBody.toRequestBody("application/json".toMediaType()))
            .build()

        return try {
            val upstreamResp = client.newCall(upstreamReq).execute()
            if (isStream && upstreamResp.isSuccessful) {
                streamResponse(upstreamResp, startNanos)
            } else {
                val status = statusFromCode(upstreamResp.code)
                val text = upstreamResp.body?.string()
                    ?: JSONObject().put("error", "Empty upstream response").toString()
                upstreamResp.close()
                monitor?.onRequestEnd("/v1/chat/completions", startNanos, upstreamResp.isSuccessful, "HTTP ${upstreamResp.code}")
                jsonResponse(status, text)
            }
        } catch (e: Exception) {
            monitor?.onRequestEnd("/v1/chat/completions", startNanos, false, e.message ?: "upstream error")
            errorJson(statusFromCode(502), "Upstream failed: ${e.message ?: "unknown"}")
        }
    }

    private fun handleImageGen(session: IHTTPSession): Response {
        if (!checkAuth(session)) return errorJson(Response.Status.UNAUTHORIZED, "Invalid or missing API key")
        val bodyMap = mutableMapOf<String, String>()
        session.parseBody(bodyMap)
        val j = runCatching { JSONObject(bodyMap["postData"] ?: "{}") }.getOrElse { JSONObject() }
        val prompt = j.optString("prompt", "").trim()
        val sizeStr = j.optString("size", "512x512")
        val dims = sizeStr.lowercase().split("x")
        val w = dims.getOrNull(0)?.toIntOrNull() ?: 512
        val h = dims.getOrNull(1)?.toIntOrNull() ?: 512
        val steps = j.optInt("steps", 20)

        val settings = settingsProvider()
        val imgModel = modelsProvider().firstOrNull { settings.profileFor(it).kind == ModelKind.IMAGE_GEN }
        val profile = imgModel?.let { settings.profileFor(it) }

        val startNanos = monitor?.onRequestStart("/v1/images/generations", session.remoteIpAddress ?: "local", false) ?: System.nanoTime()
        val refFile = decodeRefImage(j.optString("init_image_b64", ""))
        val res = mediaEngines.generateImage(prompt, imgModel, profile, settings, w, h, steps, refFile) { msg ->
            monitor?.log("ACTIVE", msg)
        }
        return res.fold(
            onSuccess = { outFile ->
                val b64 = Base64.getEncoder().encodeToString(outFile.readBytes())
                val host = session.headers["host"] ?: "${getLocalIp()}:${settings.port}"
                val mediaUrl = "http" + "://$host/media/images/${outFile.name}"
                monitor?.onRequestEnd("/v1/images/generations", startNanos, true, outFile.name)
                val respJson = JSONObject().apply {
                    put("created", System.currentTimeMillis() / 1000L)
                    put("data", JSONArray().put(JSONObject().apply {
                        put("url", mediaUrl)
                        put("local_path", outFile.absolutePath)
                        put("b64_json", b64)
                    }))
                }.toString()
                jsonResponse(Response.Status.OK, respJson)
            },
            onFailure = { err ->
                monitor?.onRequestEnd("/v1/images/generations", startNanos, false, err.message ?: "failed")
                errorJson(Response.Status.BAD_REQUEST, err.message ?: "Image generation failed")
            }
        )
    }

    private fun handleAudioSpeech(session: IHTTPSession): Response {
        if (!checkAuth(session)) return errorJson(Response.Status.UNAUTHORIZED, "Invalid or missing API key")
        val bodyMap = mutableMapOf<String, String>()
        session.parseBody(bodyMap)
        val j = runCatching { JSONObject(bodyMap["postData"] ?: "{}") }.getOrElse { JSONObject() }
        val text = j.optString("input", j.optString("prompt", "")).trim()

        val settings = settingsProvider()
        val ttsModel = modelsProvider().firstOrNull { settings.profileFor(it).kind == ModelKind.AUDIO_TTS }
        val profile = ttsModel?.let { settings.profileFor(it) }

        val startNanos = monitor?.onRequestStart("/v1/audio/speech", session.remoteIpAddress ?: "local", false) ?: System.nanoTime()
        val res = mediaEngines.generateAudio(text, ttsModel, profile) { msg ->
            monitor?.log("ACTIVE", msg)
        }
        return res.fold(
            onSuccess = { outFile ->
                monitor?.onRequestEnd("/v1/audio/speech", startNanos, true, outFile.name)
                val host = session.headers["host"] ?: "${getLocalIp()}:${settings.port}"
                val mediaUrl = "http" + "://$host/media/audio/${outFile.name}"
                val wantsJson = j.optString("response_format", "").equals("json", ignoreCase = true)
                if (wantsJson) {
                    val respJson = JSONObject().apply {
                        put("url", mediaUrl)
                        put("local_path", outFile.absolutePath)
                        put("mime", "audio/wav")
                    }.toString()
                    jsonResponse(Response.Status.OK, respJson)
                } else {
                    val resp = newFixedLengthResponse(Response.Status.OK, "audio/wav", FileInputStream(outFile), outFile.length())
                    resp.addHeader("X-SaMu-Media-Url", mediaUrl)
                    resp.addHeader("X-SaMu-Local-Path", outFile.absolutePath)
                    resp
                }
            },
            onFailure = { err ->
                monitor?.onRequestEnd("/v1/audio/speech", startNanos, false, err.message ?: "failed")
                errorJson(Response.Status.BAD_REQUEST, err.message ?: "Audio generation failed")
            }
        )
    }

    private fun handleVideoGen(session: IHTTPSession): Response {
        if (!checkAuth(session)) return errorJson(Response.Status.UNAUTHORIZED, "Invalid or missing API key")
        val bodyMap = mutableMapOf<String, String>()
        session.parseBody(bodyMap)
        val j = runCatching { JSONObject(bodyMap["postData"] ?: "{}") }.getOrElse { JSONObject() }
        val prompt = j.optString("prompt", "").trim()
        val frames = j.optInt("frames", 17)

        val settings = settingsProvider()
        val vidModel = modelsProvider().firstOrNull { settings.profileFor(it).kind == ModelKind.VIDEO_GEN }
        val profile = vidModel?.let { settings.profileFor(it) }

        val startNanos = monitor?.onRequestStart("/v1/videos/generations", session.remoteIpAddress ?: "local", false) ?: System.nanoTime()
        val refFile = decodeRefImage(j.optString("init_image_b64", ""))
        val res = mediaEngines.generateVideo(prompt, vidModel, profile, settings, frames, refFile) { msg ->
            monitor?.log("ACTIVE", msg)
        }
        return res.fold(
            onSuccess = { outFile ->
                val host = session.headers["host"] ?: "${getLocalIp()}:${settings.port}"
                val mediaUrl = "http" + "://$host/media/videos/${outFile.name}"
                monitor?.onRequestEnd("/v1/videos/generations", startNanos, true, outFile.name)
                val respJson = JSONObject().apply {
                    put("created", System.currentTimeMillis() / 1000L)
                    put("data", JSONArray().put(JSONObject().apply {
                        put("url", mediaUrl)
                        put("local_path", outFile.absolutePath)
                        put("mime", if (outFile.extension.equals("mp4", true)) "video/mp4" else "video/x-msvideo")
                    }))
                }.toString()
                jsonResponse(Response.Status.OK, respJson)
            },
            onFailure = { err ->
                monitor?.onRequestEnd("/v1/videos/generations", startNanos, false, err.message ?: "failed")
                errorJson(Response.Status.BAD_REQUEST, err.message ?: "Video generation failed")
            }
        )
    }

    private fun handleMediaFile(uri: String): Response {
        val parts = uri.removePrefix("/media/").split("/")
        if (parts.size != 2) return notFound()
        val folder = parts[0]
        val filename = parts[1]
        if (folder !in setOf("images", "audio", "videos") || filename.contains("..") || filename.contains("/")) {
            return notFound()
        }
        val file = SettingsStore.mediaDir().resolve(folder).resolve(filename).toFile()
        if (!file.isFile) return notFound()
        val mime = when (file.extension.lowercase()) {
            "png" -> "image/png"
            "jpg", "jpeg" -> "image/jpeg"
            "webp" -> "image/webp"
            "wav" -> "audio/wav"
            "mp3" -> "audio/mpeg"
            "mp4" -> "video/mp4"
            "webm" -> "video/webm"
            "avi" -> "video/x-msvideo"
            else -> "application/octet-stream"
        }
        return newFixedLengthResponse(Response.Status.OK, mime, FileInputStream(file), file.length())
    }

    private fun streamResponse(resp: okhttp3.Response, startNanos: Long): Response {
        val body = resp.body
        if (body == null) {
            runCatching { resp.close() }
            monitor?.onRequestEnd("/v1/chat/completions", startNanos, false, "No upstream body")
            return errorJson(Response.Status.INTERNAL_ERROR, "No upstream body")
        }
        val ctype = body.contentType()?.toString() ?: "text/event-stream; charset=utf-8"
        val inputStream = body.byteStream()

        val pipeIn = PipedInputStream(16 * 1024)
        val pipeOut = PipedOutputStream(pipeIn)

        Thread {
            var tokenEstimate = 0
            var lastUiReport = 0L
            var pendingDelta = 0
            var finishedOk = true
            try {
                inputStream.use { input ->
                    val buf = ByteArray(512)
                    while (true) {
                        val n = input.read(buf)
                        if (n < 0) break
                        pipeOut.write(buf, 0, n)
                        pipeOut.flush()

                        val chunkStr = String(buf, 0, n, Charsets.UTF_8)
                        var idx = chunkStr.indexOf("data:")
                        var hits = 0
                        while (idx >= 0) {
                            hits++
                            idx = chunkStr.indexOf("data:", idx + 5)
                        }
                        if (hits > 0) {
                            tokenEstimate += hits
                            pendingDelta += hits
                            val now = System.currentTimeMillis()
                            if (now - lastUiReport >= 60L) {
                                monitor?.onStreamProgress(tokenEstimate, startNanos, pendingDelta)
                                pendingDelta = 0
                                lastUiReport = now
                            }
                        }
                    }
                }
                if (pendingDelta > 0) {
                    monitor?.onStreamProgress(tokenEstimate, startNanos, pendingDelta)
                }
            } catch (_: Exception) {
                finishedOk = false
            } finally {
                runCatching { pipeOut.close() }
                runCatching { resp.close() }
                monitor?.onRequestEnd(
                    "/v1/chat/completions",
                    startNanos,
                    finishedOk,
                    "$tokenEstimate tok"
                )
            }
        }.apply {
            name = "samu-stream"
            isDaemon = true
        }.start()

        val r = newChunkedResponse(Response.Status.OK, ctype, pipeIn)
        r.setGzipEncoding(false)
        r.addHeader("Cache-Control", "no-cache, no-transform")
        r.addHeader("X-Accel-Buffering", "no")
        r.addHeader("Connection", "keep-alive")
        return r
    }

    private fun decodeRefImage(b64: String): File? {
        if (b64.isBlank()) return null
        return runCatching {
            val clean = b64.substringAfter("base64,", b64).trim()
            val bytes = Base64.getDecoder().decode(clean)
            val f = File(SettingsStore.mediaDir().resolve("images").toFile().apply { mkdirs() }, "ref_${System.currentTimeMillis()}.png")
            f.writeBytes(bytes)
            f
        }.getOrNull()
    }

    private fun checkAuth(session: IHTTPSession): Boolean {
        val current = settingsProvider()
        if (!current.requireAuth) return true
        val h = session.headers["authorization"]?.trim() ?: return false
        return h == "Bearer ${current.apiKey}"
    }

    private fun withCors(resp: Response): Response {
        resp.addHeader("Access-Control-Allow-Origin", "*")
        resp.addHeader("Access-Control-Allow-Methods", "GET, POST, OPTIONS")
        resp.addHeader("Access-Control-Allow-Headers", "Authorization, Content-Type")
        return resp
    }

    private fun statusFromCode(code: Int): Response.IStatus = object : Response.IStatus {
        override fun getRequestStatus(): Int = code
        override fun getDescription(): String = "$code"
    }

    private fun errorJson(status: Response.IStatus, message: String): Response {
        val body = JSONObject().put("error", message).toString()
        return jsonResponse(status, body)
    }

    private fun jsonResponse(status: Response.IStatus, body: String): Response =
        newFixedLengthResponse(status, "application/json", body)

    private fun notFound(): Response =
        newFixedLengthResponse(Response.Status.NOT_FOUND, "text/plain", "Not found")

    override fun stop() {
        runCatching { super.stop() }
        runCatching { client.dispatcher.executorService.shutdown() }
        runCatching { client.connectionPool.evictAll() }
    }
}
