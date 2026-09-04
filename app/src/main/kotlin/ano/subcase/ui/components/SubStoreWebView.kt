package ano.subcase.ui.components

import android.annotation.SuppressLint
import android.view.View
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.compose.BackHandler
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import ano.subcase.ui.HomeViewModel

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

    BackHandler(enabled = canGoBack) {
        homeViewModel.goBack()
    }

    AndroidView(
        factory = { context ->
            homeViewModel.acquireWebView(context).apply {
                setLayerType(View.LAYER_TYPE_HARDWARE, null)
                settings.javaScriptEnabled = true
                settings.domStorageEnabled = true
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
            view.webViewClient = WebViewClient()
            homeViewModel.releaseWebView(view)
        },
    )
}
