package ano.subcase.engine

import android.net.Uri
import android.webkit.ConsoleMessage
import android.webkit.WebChromeClient
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import ano.subcase.caseApp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import java.io.File
import kotlin.coroutines.resume

/** 使用系统 WebView 验证下载脚本能够被 V8 解析和加载。 */
object BackendScriptValidator {
    suspend fun validate(cacheDirectory: File): Result<Unit> = runCatching {
        for (scriptName in BackendFiles.SCRIPT_NAMES) {
            withTimeout(30_000) { validateOne(cacheDirectory, scriptName) }
                .getOrThrow()
        }
    }

    private suspend fun validateOne(directory: File, scriptName: String): Result<Unit> {
        val page = withContext(Dispatchers.IO) {
            File(directory, "validate-$scriptName.html").also { file ->
                // 验证页和下载文件都只存在 cacheDir，验证完成后统一清理。
                file.writeText(validationPage(scriptName))
            }
        }
        return withContext(Dispatchers.Main.immediate) {
            suspendCancellableCoroutine { continuation ->
                var syntaxError: String? = null
                var resourceError: String? = null
                val webView = WebView(caseApp)

                fun finish(result: Result<Unit>) {
                    if (!continuation.isActive) return
                    webView.stopLoading()
                    webView.loadUrl("about:blank")
                    webView.destroy()
                    page.delete()
                    continuation.resume(result)
                }

                webView.settings.javaScriptEnabled = true
                webView.settings.allowFileAccess = true
                webView.settings.allowContentAccess = false
                webView.settings.domStorageEnabled = false
                webView.settings.cacheMode = WebSettings.LOAD_NO_CACHE
                webView.webChromeClient = object : WebChromeClient() {
                    override fun onConsoleMessage(message: ConsoleMessage): Boolean {
                        if (message.message().contains("SyntaxError", ignoreCase = true)) {
                            syntaxError = message.message()
                        }
                        return true
                    }
                }
                webView.webViewClient = object : WebViewClient() {
                    override fun onReceivedError(
                        view: WebView,
                        request: WebResourceRequest,
                        error: WebResourceError,
                    ) {
                        if (request.url.lastPathSegment == scriptName) {
                            resourceError = error.description.toString()
                        }
                    }

                    override fun onPageFinished(view: WebView, url: String) {
                        view.evaluateJavascript("globalThis.__backendValidationAfterLoad === true") { value ->
                            val failure = syntaxError ?: resourceError
                            when {
                                failure != null -> finish(Result.failure(IllegalStateException(failure)))
                                value != "true" -> finish(Result.failure(IllegalStateException("脚本加载未完成: $scriptName")))
                                else -> finish(Result.success(Unit))
                            }
                        }
                    }
                }
                continuation.invokeOnCancellation {
                    webView.post {
                        webView.stopLoading()
                        webView.loadUrl("about:blank")
                        webView.destroy()
                        page.delete()
                    }
                }
                webView.loadUrl(Uri.fromFile(page).toString())
            }
        }
    }

    private fun validationPage(scriptName: String) = """
        <!doctype html><meta charset="utf-8"><script>
        globalThis.${'$'}request={url:"https://sub.store/",method:"GET",headers:{},body:null};
        globalThis.${'$'}argument="cors=http%3A%2F%2F127.0.0.1%3A8080";
        globalThis.${'$'}loon={deviceName:"Android",systemVersion:"validator",loonVersion:"SubCase WebView",build:"validator"};
        globalThis.${'$'}script={name:"validator",startTime:Date.now()};
        globalThis.${'$'}persistentStore={read(){return null},write(){return true}};
        globalThis.${'$'}notification={post(){}};
        globalThis.${'$'}done=function(){};
        globalThis.${'$'}httpClient=new Proxy({}, {get(){return function(options, callback){callback("validation",null,null)}}});
        </script><script src="$scriptName"></script><script>globalThis.__backendValidationAfterLoad=true;</script>
    """.trimIndent()
}
