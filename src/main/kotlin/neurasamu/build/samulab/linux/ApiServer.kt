package neurasamu.build.samulab.linux

import fi.iki.elonen.NanoHTTPD
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.PipedInputStream
import java.io.PipedOutputStream
import java.util.concurrent.TimeUnit

class ApiServer(
    private val settingsProvider: () -> AppSettings,
    private val engine: EngineManager,
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

    private val upstreamBase = "http://127.0.0.1:8091"

    // CRITICAL: Never GZIP-buffer SSE streams so tokens flush live immediately!
    override fun useGzipWhenAccepted(r: Response): Boolean {
        val mime = r.mimeType.orEmpty()
        if (mime.contains("text/event-stream", ignoreCase = true)) return false
        return super.useGzipWhenAccepted(r)
    }

    override fun serve(session: IHTTPSession): Response {
        if (session.method == Method.OPTIONS) {
            return withCors(newFixedLengthResponse(Response.Status.NO_CONTENT, "text/plain", ""))
        }
        val resp = try {
            when (session.uri) {
                "/health" ->
                    if (session.method == Method.GET) handleHealth() else notFound()
                "/v1/models" ->
                    if (session.method == Method.GET) handleModels(session) else notFound()
                "/v1/chat/completions" ->
                    if (session.method == Method.POST) handleChat(session) else notFound()
                else -> notFound()
            }
        } catch (e: Exception) {
            monitor?.log("ERR", "${session.uri}: ${e.message ?: "unknown"}")
            errorJson(Response.Status.INTERNAL_ERROR, "Server error: ${e.message ?: "unknown"}")
        }
        return withCors(resp)
    }

    private fun handleHealth(): Response {
        val body = JSONObject().apply {
            put("status", if (engine.isRunning) "ok" else "stopped")
            put("engine_running", engine.isRunning)
        }.toString()
        return jsonResponse(Response.Status.OK, body)
    }

    private fun handleModels(session: IHTTPSession): Response {
        if (!checkAuth(session)) {
            return errorJson(Response.Status.UNAUTHORIZED, "Invalid or missing API key")
        }
        if (!engine.isRunning) {
            return errorJson(Response.Status.SERVICE_UNAVAILABLE, "No model loaded")
        }
        val current = settingsProvider()
        val arr = JSONArray().put(JSONObject().apply {
            put("id", current.modelApiName)
            put("object", "model")
        })
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
            return errorJson(Response.Status.SERVICE_UNAVAILABLE, "Engine not running")
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
