package ano.subcase.engine.bridge

import android.os.Handler
import android.os.Looper
import android.util.Base64
import android.webkit.JavascriptInterface
import android.webkit.WebView
import androidx.annotation.Keep
import ano.subcase.model.LoonResponse
import ano.subcase.util.NotificationUtil
import kotlinx.coroutines.CompletableDeferred
import okhttp3.Call
import okhttp3.Callback
import okhttp3.Headers
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import org.json.JSONObject
import timber.log.Timber
import java.io.IOException
import java.nio.charset.StandardCharsets
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit

/** WebView 内 Loon 脚本访问 Android 能力的异步宿主 Bridge。 */
@Keep
class LoonHostBridge(
    private val webViewProvider: () -> WebView?,
    private val scriptTag: String,
) {
    private val client = OkHttpClient()
    private val calls = ConcurrentHashMap<String, Call>()
    private val doneChunks = ConcurrentHashMap<String, ChunkState>()
    @Volatile private var activeExecutionId: String? = null
    @Volatile private var activeResult: CompletableDeferred<LoonResponse>? = null
    @Volatile private var ready = CompletableDeferred<Unit>()

    /** runner 脚本加载完毕后解除执行器等待。 */
    @JavascriptInterface
    fun runnerReady() {
        ready.complete(Unit)
    }

    suspend fun awaitReady() = ready.await()

    fun resetReady() {
        ready = CompletableDeferred()
    }

    fun begin(executionId: String, result: CompletableDeferred<LoonResponse>) {
        activeExecutionId = executionId
        activeResult = result
        doneChunks.remove(executionId)
    }

    /** 接收 `$done` 的分块数量。大响应分块可避开 evaluateJavascript 单次参数限制。 */
    @JavascriptInterface
    fun doneStart(executionId: String, total: Int) {
        if (!isCurrent(executionId) || total !in 1..MAX_CHUNKS) return
        doneChunks[executionId] = ChunkState(total)
    }

    @JavascriptInterface
    fun doneChunk(executionId: String, index: Int, chunk: String) {
        if (!isCurrent(executionId)) return
        doneChunks[executionId]?.put(index, chunk)
    }

    @JavascriptInterface
    fun doneEnd(executionId: String) {
        if (!isCurrent(executionId)) return
        runCatching {
            val encoded = doneChunks.remove(executionId)?.join()
                ?: error("\$done 响应分块不完整")
            val json = String(Base64.decode(encoded, Base64.DEFAULT), StandardCharsets.UTF_8)
            parseLoonResponse(JSONObject(json))
        }.onSuccess { activeResult?.complete(it) }
            .onFailure { activeResult?.completeExceptionally(it) }
    }

    /** execution ID 会过滤已经超时或属于上一请求的回调。 */
    @JavascriptInterface
    fun scriptError(executionId: String, message: String) {
        if (isCurrent(executionId)) {
            activeResult?.completeExceptionally(IllegalStateException(message))
        }
    }

    @JavascriptInterface
    fun notify(
        executionId: String,
        title: String,
        subtitle: String,
        content: String,
        attachmentJson: String,
    ) {
        if (!isCurrent(executionId)) return
        NotificationUtil.postScriptNotification(title, subtitle, content)
        Timber.tag(scriptTag).d("notification attachment=%s", attachmentJson)
    }

    /**
     * 启动 OkHttp 异步请求。该方法运行在 WebView Bridge 后台线程，完成结果会切回主线程投递给 JS。
     */
    @JavascriptInterface
    fun startHttpRequest(
        executionId: String,
        requestId: String,
        method: String,
        optionsJson: String,
    ) {
        if (!isCurrent(executionId) || !requestId.startsWith("$executionId-")) return
        runCatching {
            val options = JSONObject(optionsJson)
            val request = buildRequest(method, options)
            val timeout = options.optLong("timeout", DEFAULT_HTTP_TIMEOUT_MS)
                .coerceIn(1L, MAX_HTTP_TIMEOUT_MS)
            val followRedirects = options.optBoolean(
                "auto-redirect",
                options.optBoolean("redirection", true),
            )
            val call = client.newBuilder()
                .followRedirects(followRedirects)
                .followSslRedirects(followRedirects)
                .callTimeout(timeout, TimeUnit.MILLISECONDS)
                .build().newCall(request)
            calls[requestId] = call
            Timber.tag(scriptTag).d("HTTP %s %s node=%s", method, request.url, options.opt("node"))
            call.enqueue(object : Callback {
                override fun onFailure(call: Call, e: IOException) {
                    calls.remove(requestId)
                    deliverHttp(executionId, requestId, JSONObject().put("error", e.message ?: "网络请求失败"))
                }

                override fun onResponse(call: Call, response: Response) {
                    calls.remove(requestId)
                    response.use {
                        runCatching { buildHttpPayload(it, options.optBoolean("binary-mode", false)) }
                            .onSuccess { payload -> deliverHttp(executionId, requestId, payload) }
                            .onFailure { error ->
                                deliverHttp(
                                    executionId,
                                    requestId,
                                    JSONObject().put("error", error.message ?: "读取响应失败"),
                                )
                            }
                    }
                }
            })
        }.onFailure {
            deliverHttp(executionId, requestId, JSONObject().put("error", it.message ?: "请求参数错误"))
        }
    }

    fun cancel(executionId: String, cause: Throwable? = null) {
        if (!isCurrent(executionId)) return
        calls.entries.removeIf { (requestId, call) ->
            if (requestId.startsWith("$executionId-")) call.cancel()
            requestId.startsWith("$executionId-")
        }
        doneChunks.remove(executionId)
        cause?.let { activeResult?.completeExceptionally(it) }
        activeResult = null
        activeExecutionId = null
    }

    fun close(cause: Throwable) {
        activeExecutionId?.let { cancel(it, cause) }
        calls.values.forEach(Call::cancel)
        calls.clear()
        ready.completeExceptionally(cause)
    }

    private fun buildRequest(methodValue: String, options: JSONObject): Request {
        val method = methodValue.uppercase()
        require(method in SUPPORTED_METHODS) { "不支持的 HTTP method: $method" }
        val builder = Request.Builder().url(options.getString("url"))
        val headers = options.optJSONObject("headers")
        headers?.keys()?.forEach { name -> builder.header(name, headers.optString(name)) }

        val contentType = headers?.keys()?.asSequence()
            ?.firstOrNull { it.equals("Content-Type", ignoreCase = true) }
            ?.let(headers::optString)
            ?.takeIf(String::isNotBlank)
            ?.toMediaTypeOrNull()
        val body = when {
            method == "GET" || method == "HEAD" -> null
            options.optBoolean("body-base64", options.optBoolean("bodyBase64", false)) -> {
                val bytes = Base64.decode(options.optString("body"), Base64.DEFAULT)
                require(bytes.size <= MAX_BODY_BYTES) { "HTTP 请求体超过 64 MiB" }
                bytes.toRequestBody(contentType)
            }
            options.has("body") && !options.isNull("body") -> {
                val bytes = options.optString("body").toByteArray(StandardCharsets.UTF_8)
                require(bytes.size <= MAX_BODY_BYTES) { "HTTP 请求体超过 64 MiB" }
                bytes.toRequestBody(contentType)
            }
            method in METHODS_REQUIRING_BODY -> ByteArray(0).toRequestBody(contentType)
            else -> null
        }
        builder.method(method, body)
        return builder.build()
    }

    private fun buildHttpPayload(response: Response, binary: Boolean): JSONObject {
        val responseBody = response.body
        val charset = responseBody.contentType()?.charset(StandardCharsets.UTF_8)
            ?: StandardCharsets.UTF_8
        val bytes = responseBody.bytes()
        require(bytes.size <= MAX_BODY_BYTES) { "HTTP 响应超过 64 MiB" }
        val body = if (binary) {
            Base64.encodeToString(bytes, Base64.NO_WRAP)
        } else {
            String(bytes, charset)
        }
        return JSONObject()
            .put("status", response.code)
            .put("headers", response.headers.toJson())
            .put("body", body)
            .put("binary", binary)
    }

    private fun deliverHttp(executionId: String, requestId: String, payload: JSONObject) {
        if (!isCurrent(executionId)) return
        val encoded = Base64.encodeToString(
            payload.toString().toByteArray(StandardCharsets.UTF_8),
            Base64.NO_WRAP,
        )
        val chunks = encoded.chunked(JS_CHUNK_SIZE).ifEmpty { listOf("") }
        // Headless WebView 未挂载到 View 树，必须通过主线程 Handler 投递。
        mainHandler.post {
            if (!isCurrent(executionId)) return@post
            val webView = webViewProvider() ?: return@post
            chunks.forEachIndexed { index, chunk ->
                webView.evaluateJavascript(
                    "__loonBridge.receiveHttpChunk(${JSONObject.quote(requestId)},$index,${chunks.size},${JSONObject.quote(chunk)});",
                    null,
                )
            }
        }
    }

    private fun parseLoonResponse(root: JSONObject): LoonResponse {
        val response = root.optJSONObject("response") ?: root
        val status = response.optInt("status", response.optInt("statusCode", 200))
        val headers = buildMap {
            response.optJSONObject("headers")?.let { json ->
                json.keys().forEach { key -> put(key, json.optString(key)) }
            }
        }
        return LoonResponse(status, headers, response.optString("body", ""))
    }

    private fun Headers.toJson() = JSONObject().also { json ->
        names().forEach { name -> json.put(name, values(name).joinToString(", ")) }
    }

    private fun isCurrent(executionId: String) = activeExecutionId == executionId

    private class ChunkState(private val total: Int) {
        private val chunks = arrayOfNulls<String>(total)
        @Synchronized fun put(index: Int, value: String) {
            if (index in chunks.indices) chunks[index] = value
        }
        @Synchronized fun join(): String? =
            if (chunks.all { it != null }) chunks.joinToString("") else null
    }

    companion object {
        /** 投递给 JS 的 HTTP 响应单块大小，避开 evaluateJavascript 参数上限。 */
        private const val JS_CHUNK_SIZE = 256 * 1024
        /** HTTP 请求默认超时。 */
        private const val DEFAULT_HTTP_TIMEOUT_MS = 5_000L
        /** HTTP 请求允许的最大超时。 */
        private const val MAX_HTTP_TIMEOUT_MS = 900_000L
        /** `$done` 响应允许的最大分块数。 */
        private const val MAX_CHUNKS = 512
        /** HTTP 请求体与响应体上限。 */
        private const val MAX_BODY_BYTES = 64 * 1024 * 1024
        /** 向未挂载 View 树的 Headless WebView 投递 JS 必须走主线程。 */
        private val mainHandler = Handler(Looper.getMainLooper())
        /** Bridge 支持的 HTTP 方法。 */
        private val SUPPORTED_METHODS = setOf("GET", "POST", "PUT", "PATCH", "DELETE", "HEAD", "OPTIONS")
        /** 即使脚本未提供 body，OkHttp 也要求带空请求体的方法。 */
        private val METHODS_REQUIRING_BODY = setOf("POST", "PUT", "PATCH")
    }
}
