package ano.subcase.ui.components

import android.os.SystemClock
import android.util.Base64
import android.util.Log
import android.view.View
import android.view.ViewGroup
import android.webkit.WebView
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModelProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import ano.subcase.ui.HomeViewModel
import org.json.JSONObject
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlin.math.abs

@RunWith(AndroidJUnit4::class)
class SubStoreWebViewViewportTest {
    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun viewportUnitsFollowBoundsAfterResizeAndReattach() {
        val shown = mutableStateOf(true)
        val height = mutableStateOf(400.dp)
        lateinit var model: HomeViewModel
        compose.runOnUiThread {
            model = ViewModelProvider(compose.activity)[HomeViewModel::class.java]
        }
        val html = """
            <!doctype html><html><head>
            <meta name="viewport" content="width=device-width, initial-scale=1">
            <style>
            body { margin: 0; }
            #vh { height: 100vh; }
            #dvh { height: 100dvh; }
            #preview { position: fixed; inset: 0; height: calc(100vh - 80px); }
            </style></head><body>
            <div id="vh"></div><div id="dvh"></div><div id="preview">Preview</div>
            </body></html>
        """.trimIndent()
        val url = "data:text/html;base64," + Base64.encodeToString(html.toByteArray(), Base64.NO_WRAP)
        compose.setContent {
            Box(Modifier.fillMaxWidth().height(height.value)) {
                if (shown.value) {
                    SubStoreWebView(url, model, Modifier.fillMaxSize())
                }
            }
        }
        compose.waitForIdle()
        lateinit var original: WebView
        compose.runOnUiThread {
            original = requireNotNull(findWebView(compose.activity.window.decorView))
        }
        assertViewport(original, "initial", 400.0)

        compose.runOnIdle { height.value = 300.dp }
        compose.waitForIdle()
        assertViewport(original, "resized", 300.0)

        compose.runOnIdle { shown.value = false }
        compose.waitForIdle()
        compose.runOnIdle { shown.value = true }
        compose.waitForIdle()
        compose.runOnUiThread {
            assertSame(original, findWebView(compose.activity.window.decorView))
        }
        assertViewport(original, "reattached", 300.0)
    }

    private fun assertViewport(webView: WebView, stage: String, expectedHeight: Double) {
        val deadline = SystemClock.uptimeMillis() + 10_000
        var sample = JSONObject()
        do {
            sample = sampleViewport(webView)
            val inner = sample.optDouble("inner")
            if (abs(inner - expectedHeight) <= 2 &&
                abs(sample.optDouble("vh") - inner) <= 2 &&
                abs(sample.optDouble("dvh") - inner) <= 2 &&
                abs(sample.optDouble("preview") - (inner - 80)) <= 2
            ) {
                Log.i("SubCaseViewportTest", "$stage: $sample")
                return
            }
            SystemClock.sleep(100)
        } while (SystemClock.uptimeMillis() < deadline)
        Log.e("SubCaseViewportTest", "$stage: $sample")
        assertTrue("$stage viewport should match ${expectedHeight}px: $sample", false)
    }

    private fun sampleViewport(webView: WebView): JSONObject {
        val result = JSONObject()
        val done = CountDownLatch(1)
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            result.put("layoutHeight", webView.layoutParams.height)
            result.put("nativeHeight", webView.height)
            webView.evaluateJavascript(
                """
                (() => {
                  const height = id => document.getElementById(id)?.getBoundingClientRect().height;
                  return {inner: innerHeight, vh: height('vh'), dvh: height('dvh'), preview: height('preview')};
                })()
                """.trimIndent(),
            ) { raw ->
                try {
                    val dimensions = JSONObject(raw)
                    dimensions.keys().forEach { result.put(it, dimensions.get(it)) }
                } finally {
                    done.countDown()
                }
            }
        }
        check(done.await(5, TimeUnit.SECONDS)) { "WebView viewport probe timed out" }
        return result
    }

    private fun findWebView(view: View): WebView? {
        if (view is WebView) return view
        if (view is ViewGroup) {
            for (index in 0 until view.childCount) {
                findWebView(view.getChildAt(index))?.let { return it }
            }
        }
        return null
    }
}
