package ano.subcase.engine

import android.content.Context
import android.os.Looper
import ano.subcase.model.LoonRequest
import ano.subcase.model.LoonResponse
import ano.subcase.model.SubStoreScript
import ano.subcase.server.SubCaseHttpServer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext
import java.io.File
import java.net.URLEncoder
import java.nio.charset.StandardCharsets

/** 协调两个 Ktor 服务，并为每个后端请求创建独立 WebView。 */
class CaseEngine(
    private val context: Context,
    private val backendPort: Int,
    private val frontendPort: Int,
    val host: String,
    private val onFailure: (CaseEngine, Throwable) -> Unit,
) {
    private val backendDir: File
    private var httpServer: SubCaseHttpServer? = null

    init {
        check(Looper.myLooper() == Looper.getMainLooper()) { "CaseEngine 必须在主线程创建" }
        backendDir = BackendFiles.directory(context)
    }

    fun startServer() {
        check(httpServer == null) { "Sub-Store server 已启动" }
        httpServer = SubCaseHttpServer(
            host = host,
            frontendPort = frontendPort,
            backendPort = backendPort,
            frontendDir = File(context.filesDir, "frontend"),
            execute = ::execute,
            onFailure = { error -> onFailure(this, error) },
        )
        httpServer!!.start()
    }

    fun stopServer() {
        check(Looper.myLooper() == Looper.getMainLooper()) { "CaseEngine 必须在主线程停止" }
        try {
            httpServer?.stop()
        } finally {
            httpServer = null
        }
    }

    private suspend fun execute(request: LoonRequest): LoonResponse {
        val origin = request.headers.entries
            .firstOrNull { it.key.equals("Origin", ignoreCase = true) }
            ?.value
            ?: "http://127.0.0.1:$frontendPort"
        val argument = "cors=" + URLEncoder.encode(origin, StandardCharsets.UTF_8.name())
        var executor: ScriptWebView? = null
        return try {
            withContext(Dispatchers.Main.immediate) {
                executor = if (SubStoreScriptRouter.select(request.url) == SubStoreScript.CORE) {
                    CoreScriptWebView(context, backendDir)
                } else {
                    SimpleScriptWebView(context, backendDir)
                }
            }
            checkNotNull(executor).execute(request, argument)
        } finally {
            executor?.let {
                withContext(NonCancellable + Dispatchers.Main.immediate) {
                    it.close()
                }
            }
        }
    }
}

/** 保持与 Loon http-request 规则一致的脚本选择器。 */
internal object SubStoreScriptRouter {
    private val coreRequestPattern =
        Regex("""^https?://sub\.store/((download)|api/(preview|sync|(utils/node-info)))""")

    fun select(url: String): SubStoreScript =
        if (coreRequestPattern.containsMatchIn(url)) SubStoreScript.CORE else SubStoreScript.SIMPLE
}
