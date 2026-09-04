package ano.subcase.engine

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import android.os.Build
import android.os.Looper
import android.util.Base64
import android.webkit.ConsoleMessage
import android.webkit.RenderProcessGoneDetail
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import ano.subcase.BuildConfig
import ano.subcase.engine.bridge.LoonHostBridge
import ano.subcase.engine.bridge.PersistentStoreBridge
import ano.subcase.model.LoonRequest
import ano.subcase.model.LoonResponse
import ano.subcase.model.SubStoreScript
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.json.JSONObject
import timber.log.Timber
import java.io.ByteArrayInputStream
import java.io.File
import java.nio.charset.StandardCharsets
import java.util.UUID

/** 单次请求使用的 Headless WebView 执行器。 */
open class ScriptWebView internal constructor(
    private val context: Context,
    private val backendDir: File,
    private val script: SubStoreScript,
) {
    private val persistentStore = PersistentStoreBridge(context)
    private var webView: WebView? = null
    private var hostBridge: LoonHostBridge? = null
    private var closed = false

    init {
        check(Looper.myLooper() == Looper.getMainLooper()) { "ScriptWebView 必须在主线程创建" }
        createWebView()
    }

    suspend fun execute(request: LoonRequest, argument: String): LoonResponse {
        val executionId = UUID.randomUUID().toString()
        val result = CompletableDeferred<LoonResponse>()
        val bridge = withContext(Dispatchers.Main.immediate) {
            check(!closed) { "WebView 执行器已关闭" }
            hostBridge?.also { it.begin(executionId, result) }
                ?: error("WebView Bridge 尚未创建")
        }

        return try {
            withTimeout(EXECUTION_TIMEOUT_MS) {
                bridge.awaitReady()
                withContext(Dispatchers.Main.immediate) {
                    injectAndStart(executionId, request, argument)
                }
                result.await()
            }
        } catch (error: TimeoutCancellationException) {
            throw ScriptExecutionTimeoutException(script.tag, error)
        } finally {
            bridge.cancel(executionId)
        }
    }

    fun close() {
        check(Looper.myLooper() == Looper.getMainLooper()) { "WebView 必须在主线程销毁" }
        closed = true
        destroyWebView(IllegalStateException("Sub-Store 服务已停止"))
    }

    private fun createWebView() {
        // Android WebView 的创建、配置、导航和销毁都要求在主线程完成。
        lateinit var created: WebView
        val bridge = LoonHostBridge({ webView }, script.tag)
        created = WebView(context).apply {
            settings.javaScriptEnabled = true
            settings.domStorageEnabled = false
            settings.allowContentAccess = false
            settings.allowFileAccess = true
            settings.setSupportMultipleWindows(false)
            settings.javaScriptCanOpenWindowsAutomatically = false
            settings.cacheMode = WebSettings.LOAD_NO_CACHE
            addJavascriptInterface(persistentStore, PERSISTENT_INTERFACE)
            addJavascriptInterface(bridge, HOST_INTERFACE)
            webChromeClient = object : WebChromeClient() {
                override fun onConsoleMessage(consoleMessage: ConsoleMessage): Boolean {
                    val message = "${consoleMessage.sourceId()}:${consoleMessage.lineNumber()} ${consoleMessage.message()}"
                    when (consoleMessage.messageLevel()) {
                        ConsoleMessage.MessageLevel.ERROR -> Timber.tag(script.tag).e(message)
                        ConsoleMessage.MessageLevel.WARNING -> Timber.tag(script.tag).w(message)
                        ConsoleMessage.MessageLevel.TIP -> Timber.tag(script.tag).i(message)
                        ConsoleMessage.MessageLevel.LOG,
                        ConsoleMessage.MessageLevel.DEBUG -> Timber.tag(script.tag).d(message)
                    }
                    return true
                }
            }
            webViewClient = LocalOnlyClient()
        }
        hostBridge = bridge
        webView = created
        created.loadUrl(Uri.fromFile(File(backendDir, "runner.html")).toString())
    }

    private fun injectAndStart(
        executionId: String,
        request: LoonRequest,
        argument: String,
    ) {
        val currentWebView = webView ?: error("WebView 已销毁")
        val contextJson = JSONObject()
            .put("executionId", executionId)
            .put("systemVersion", Build.VERSION.RELEASE)
            .put("appVersion", BuildConfig.VERSION_NAME)
            .put("scriptTag", script.tag)
            .put("argument", argument)
            .put(
                "request",
                JSONObject()
                    .put("url", request.url)
                    .put("method", request.method)
                    .put("headers", JSONObject(request.headers))
                    .put("body", request.body ?: JSONObject.NULL),
            )
        val encoded = Base64.encodeToString(
            contextJson.toString().toByteArray(StandardCharsets.UTF_8),
            Base64.NO_WRAP,
        )
        val chunks = encoded.chunked(JS_CHUNK_SIZE).ifEmpty { listOf("") }
        chunks.forEachIndexed { index, chunk ->
            currentWebView.evaluateJavascript(
                "__loonBridge.receiveExecutionChunk(${JSONObject.quote(executionId)},$index,${chunks.size},${JSONObject.quote(chunk)});",
                null,
            )
        }
        val scriptFile = File(BackendFiles.versionDir(context), script.fileName)
        check(scriptFile.isFile) { "后端脚本不存在: ${scriptFile.path}" }
        currentWebView.evaluateJavascript(
            "__loonBridge.startExecution(${JSONObject.quote(executionId)},${JSONObject.quote(Uri.fromFile(scriptFile).toString())});",
            null,
        )
    }

    private fun destroyWebView(cause: Throwable) {
        val current = webView ?: return
        hostBridge?.close(cause)
        // 先停止加载并切到空页，再移除 Bridge，最后销毁 renderer 资源。
        current.stopLoading()
        current.loadUrl("about:blank")
        current.removeJavascriptInterface(PERSISTENT_INTERFACE)
        current.removeJavascriptInterface(HOST_INTERFACE)
        current.destroy()
        webView = null
        hostBridge = null
    }

    private inner class LocalOnlyClient : WebViewClient() {
        override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean =
            !isAllowed(request.url)

        override fun shouldInterceptRequest(
            view: WebView,
            request: WebResourceRequest,
        ): WebResourceResponse? = if (isAllowed(request.url)) {
            null
        } else {
            WebResourceResponse(
                "text/plain",
                "UTF-8",
                403,
                "Forbidden",
                emptyMap(),
                ByteArrayInputStream(ByteArray(0)),
            )
        }

        override fun onPageStarted(view: WebView, url: String, favicon: Bitmap?) {
            Timber.tag(script.tag).d("runner loading: %s", url)
        }

        override fun onRenderProcessGone(view: WebView, detail: RenderProcessGoneDetail): Boolean {
            Timber.tag(script.tag).e("WebView renderer exited, crashed=%s", detail.didCrash())
            destroyCrashedWebView(view, IllegalStateException("WebView renderer 已退出"))
            if (!closed) createWebView()
            return true
        }
    }

    private fun isAllowed(uri: Uri): Boolean {
        if (uri.scheme != "file") return false
        val requested = runCatching { File(requireNotNull(uri.path)).canonicalFile }.getOrNull()
            ?: return false
        val root = backendDir.canonicalFile
        return requested == File(root, "runner.html").canonicalFile || requested.toPath().startsWith(root.toPath())
    }

    private fun destroyCrashedWebView(crashed: WebView, cause: Throwable) {
        hostBridge?.close(cause)
        // renderer 已退出时实例不能继续导航，直接移除 Bridge 并销毁。
        crashed.removeJavascriptInterface(PERSISTENT_INTERFACE)
        crashed.removeJavascriptInterface(HOST_INTERFACE)
        crashed.destroy()
        if (webView === crashed) webView = null
        hostBridge = null
    }

    companion object {
        private const val HOST_INTERFACE = "__loonHost"
        private const val PERSISTENT_INTERFACE = "__persistentStore"
        private const val JS_CHUNK_SIZE = 256 * 1024
        private const val EXECUTION_TIMEOUT_MS = 900_000L
    }
}

/** 固定执行 `sub-store-0.min.js` 的 Simple 后端 WebView。 */
class SimpleScriptWebView(context: Context, backendDir: File) :
    ScriptWebView(context, backendDir, SubStoreScript.SIMPLE)

/** 固定执行 `sub-store-1.min.js` 的 Core 后端 WebView。 */
class CoreScriptWebView(context: Context, backendDir: File) :
    ScriptWebView(context, backendDir, SubStoreScript.CORE)

/** 单次 Sub-Store 脚本执行超过 900 秒。 */
class ScriptExecutionTimeoutException(scriptTag: String, cause: Throwable) :
    RuntimeException("$scriptTag 执行超时", cause)
