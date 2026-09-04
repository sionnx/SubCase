package ano.subcase.ui

import android.app.Application
import android.content.Context
import android.content.MutableContextWrapper
import android.view.ViewGroup
import android.webkit.WebView
import androidx.lifecycle.AndroidViewModel

class HomeViewModel(application: Application) : AndroidViewModel(application) {

    private val webViewContext = MutableContextWrapper(application)
    private var retainedWebView: WebView? = null
    private var requestedEntryUrl: String? = null

    fun acquireWebView(context: Context): WebView {
        webViewContext.baseContext = context

        return (retainedWebView ?: WebView(webViewContext).also { retainedWebView = it })
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
