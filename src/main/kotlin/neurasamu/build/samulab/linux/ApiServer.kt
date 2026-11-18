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
    private val settings: AppSettings,
    private val engine: EngineManager
) : NanoHTTPD(if (settings.lanAccess) "0.0.0.0" else "127.0.0.1", settings.port) {

    private val client = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(0, TimeUnit.MILLISECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .build()

    private val upstreamBase = "http://127.0.0.1:8091"

    override fun serve(session: IHTTPSession): Response {
        return try {
            when (session.uri) {
                "/v1/models" ->
                    if (session.method == Method.GET) handleModels() else notFound()
                "/v1/chat/completions" ->
                    if (session.method == Method.POST) handleChat(session) else notFound()
                else -> notFound()
            }
        } catch (e: Exception) {
            val msg = (e.message ?: "unknown").replace('"', '\'')
            jsonResponse(Response.Status.INTERNAL_ERROR, """{"error":"Server error: $msg"}""")
        }
    }

    private fun handleModels(): Response {
        if (!engine.isRunning) {
            return jsonResponse(Response.Status.SERVICE_UNAVAILABLE, """{"error":"No model loaded"}""")
        }
        val arr = JSONArray().put(JSONObject().apply {
            put("id", settings.modelApiName)
            put("object", "model")
        })
        val body = JSONObject().apply {
            put("object", "list")
            put("data", arr)
        }.toString()
        return jsonResponse(Response.Status.OK, body)
    }

    private fun handleChat(session: IHTTPSession): Response {
        if (!engine.isRunning) {
            return jsonResponse(Response.Status.SERVICE_UNAVAILABLE, """{"error":"Engine not running"}""")
        }
        if (!checkAuth(session)) {
            return jsonResponse(Response.Status.UNAUTHORIZED, """{"error":"Invalid or missing API key"}""")
        }

        val bodyMap = mutableMapOf<String, String>()
        session.parseBody(bodyMap)
        val rawBody = bodyMap["postData"] ?: "{}"
        val isStream = runCatching { JSONObject(rawBody).optBoolean("stream", false) }
            .getOrDefault(false)

        val upstreamReq = Request.Builder()
            .url("$upstreamBase/v1/chat/completions")
            .post(rawBody.toRequestBody("application/json".toMediaType()))
            .build()

        return try {
            val upstreamResp = client.newCall(upstreamReq).execute()
            if (isStream) {
                streamResponse(upstreamResp)
            } else {
                val text = upstreamResp.body?.string()
                    ?: """{"error":"Empty upstream response"}"""
                upstreamResp.close()
                jsonResponse(Response.Status.OK, text)
            }
        } catch (e: Exception) {
            val msg = (e.message ?: "unknown").replace('"', '\'')
            jsonResponse(object : Response.IStatus {
                override fun getRequestStatus(): Int = 502
                override fun getDescription(): String = "502 Bad Gateway"
            }, """{"error":"Upstream failed: $msg"}""")
        }
    }

    private fun streamResponse(resp: okhttp3.Response): Response {
        val body = resp.body
        if (body == null) {
            runCatching { resp.close() }
            return jsonResponse(Response.Status.INTERNAL_ERROR, """{"error":"No upstream body"}""")
        }
        val ctype = body.contentType()?.toString() ?: "text/event-stream"
        val inputStream = body.byteStream()

        val pipeIn = PipedInputStream(64 * 1024)
        val pipeOut = PipedOutputStream(pipeIn)

        Thread {
            try {
                inputStream.use { input ->
                    val buf = ByteArray(8192)
                    while (true) {
                        val n = input.read(buf)
                        if (n < 0) break
                        pipeOut.write(buf, 0, n)
                        pipeOut.flush()
                    }
                }
            } catch (_: Exception) {
                // client ya upstream ne connection band kiya
            } finally {
                runCatching { pipeOut.close() }
                runCatching { resp.close() }
            }
        }.apply {
            name = "samu-stream"
            isDaemon = true
        }.start()

        val r = newChunkedResponse(Response.Status.OK, ctype, pipeIn)
        r.addHeader("Cache-Control", "no-cache")
        r.addHeader("X-Accel-Buffering", "no")
        r.addHeader("Connection", "keep-alive")
        return r
    }

    private fun checkAuth(session: IHTTPSession): Boolean {
        if (!settings.requireAuth) return true
        val h = session.headers["authorization"] ?: return false
        return h == "Bearer ${settings.apiKey}"
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
