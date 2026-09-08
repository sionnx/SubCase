package ano.subcase.ui

import android.app.Application
import android.content.Context
import android.content.MutableContextWrapper
import android.view.ViewGroup
import android.webkit.WebView
import android.widget.Toast
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import ano.subcase.R
import ano.subcase.util.ConfigStore
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import timber.log.Timber

class HomeViewModel(application: Application) : AndroidViewModel(application) {
    var panelVerticalFraction by mutableStateOf(ConfigStore.homePanelVerticalFraction)
        private set

    private val panelSaveMutex = Mutex()

    // 保存面板位置
    fun savePanelPosition(fraction: Float) {
        panelVerticalFraction = fraction
        viewModelScope.launch {
            try {
                // Preserve release order when several drags finish in quick succession.
                panelSaveMutex.withLock {
                    withContext(Dispatchers.IO) {
                        ConfigStore.saveHomePanelVerticalFraction(fraction)
                    }
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                Timber.e(error, "Failed to persist home panel position")
                Toast.makeText(getApplication(), R.string.panel_position_save_failed, Toast.LENGTH_LONG).show()
            }
        }
    }

    private val webViewContext = MutableContextWrapper(application)
    private var retainedWebView: WebView? = null
    private var requestedEntryUrl: String? = null

    fun acquireWebView(context: Context): WebView {
        webViewContext.baseContext = context

        return (retainedWebView ?: WebView(webViewContext).also { webView ->
            // WRAP_CONTENT makes WebView use a zero-height CSS viewport, even when
            // Compose measures the AndroidView with an exact, nonzero height.
            webView.layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT,
            )
            retainedWebView = webView
        })
            .also { webView ->
                (webView.parent as? ViewGroup)?.removeView(webView)
                webView.onResume()
            }
    }

    fun releaseWebView(webView: WebView) {
        if (retainedWebView !== webView) return

        (webView.parent as? ViewGroup)?.removeView(webView)
        webView.onPause()
        webViewContext.baseContext = getApplication()
    }

    fun goBack() {
        retainedWebView?.takeIf(WebView::canGoBack)?.goBack()
    }

    fun loadEntryUrl(url: String) {
        if (requestedEntryUrl == url) return

        requestedEntryUrl = url
        retainedWebView?.loadUrl(url)
    }

    override fun onCleared() {
        retainedWebView?.let { webView ->
            (webView.parent as? ViewGroup)?.removeView(webView)
            webView.stopLoading()
            webView.removeAllViews()
            webView.destroy()
        }
        retainedWebView = null
        requestedEntryUrl = null
        super.onCleared()
    }
}
