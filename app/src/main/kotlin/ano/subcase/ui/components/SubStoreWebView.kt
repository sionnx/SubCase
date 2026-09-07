package ano.subcase.ui.components

import android.annotation.SuppressLint
import android.net.Uri
import android.view.View
import android.webkit.ValueCallback
import android.webkit.WebChromeClient
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import ano.subcase.ui.HomeViewModel
import timber.log.Timber

internal fun buildSubStoreUrl(
    allowLan: Boolean,
    lanIp: String,
): String {
    val host = lanIp.takeIf { allowLan && it.isNotBlank() } ?: "127.0.0.1"
    return "http://$host:8080/subs?api=http://$host:8081"
}

@SuppressLint("SetJavaScriptEnabled")
@Composable
internal fun SubStoreWebView(
    url: String,
    homeViewModel: HomeViewModel,
    modifier: Modifier = Modifier,
) {
    var canGoBack by remember { mutableStateOf(false) }
    var fileCallback by remember { mutableStateOf<ValueCallback<Array<Uri>>?>(null) }
    // Keep an old picker result from completing a new request after recreation.
    var fileChooserInFlight by rememberSaveable { mutableStateOf(false) }
    val fileChooserLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult(),
    ) { result ->
        val callback = fileCallback
        fileCallback = null
        fileChooserInFlight = false
        callback?.onReceiveValue(
            WebChromeClient.FileChooserParams.parseResult(result.resultCode, result.data),
        )
    }

    BackHandler(enabled = canGoBack) {
        homeViewModel.goBack()
    }

    AndroidView(
        factory = { context ->
            homeViewModel.acquireWebView(context).apply {
                setLayerType(View.LAYER_TYPE_HARDWARE, null)
                settings.javaScriptEnabled = true
                settings.domStorageEnabled = true
                webChromeClient = object : WebChromeClient() {
                    // 允许用户通过导入文件的形式添加订阅
                    override fun onShowFileChooser(
                        webView: WebView,
                        filePathCallback: ValueCallback<Array<Uri>>,
                        fileChooserParams: FileChooserParams,
                    ): Boolean {
                        if (fileChooserInFlight) {
                            filePathCallback.onReceiveValue(null)
                            return true
                        }
                        fileCallback = filePathCallback
                        fileChooserInFlight = true
                        try {
                            fileChooserLauncher.launch(fileChooserParams.createIntent())
                        } catch (error: RuntimeException) {
                            fileCallback = null
                            fileChooserInFlight = false
                            filePathCallback.onReceiveValue(null)
                            Timber.e(error, "Unable to open file chooser")
                            Toast.makeText(context, "无法打开文件选择器，请检查系统文件应用", Toast.LENGTH_LONG).show()
                        }
                        return true
                    }
                }
                webViewClient = object : WebViewClient() {
                    override fun doUpdateVisitedHistory(
                        view: WebView?,
                        url: String?,
                        isReload: Boolean,
                    ) {
                        super.doUpdateVisitedHistory(view, url, isReload)
                        canGoBack = view?.canGoBack() == true
                    }

                    override fun onPageFinished(view: WebView?, url: String?) {
                        super.onPageFinished(view, url)
                        canGoBack = view?.canGoBack() == true
                    }
                }
                canGoBack = canGoBack()
            }
        },
        modifier = modifier,
        update = {
            homeViewModel.loadEntryUrl(url)
        },
        onRelease = { view ->
            canGoBack = false
            view.webChromeClient = null
            val callback = fileCallback
            fileCallback = null
            callback?.onReceiveValue(null)
            view.webViewClient = WebViewClient()
            homeViewModel.releaseWebView(view)
        },
    )
}
