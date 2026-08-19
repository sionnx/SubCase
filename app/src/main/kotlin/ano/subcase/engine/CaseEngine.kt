package ano.subcase.engine

import ano.subcase.caseApp
import com.caoccao.javet.enums.V8AwaitMode
import com.caoccao.javet.interception.logging.JavetStandardConsoleInterceptor
import com.caoccao.javet.interop.NodeRuntime
import com.caoccao.javet.interop.V8Host
import com.caoccao.javet.interop.options.NodeRuntimeOptions
import timber.log.Timber
import java.io.File
import kotlin.io.path.Path

class CaseEngine(backendPort: Int, frontendPort: Int, allowLan: Boolean) {
    val host = if (allowLan) "0.0.0.0" else "127.0.0.1"

    private var nodeRuntime: NodeRuntime? = null
    private lateinit var thread: Thread

    private var shouldAwait = true

    private val argv2EnvScript = """
        process.argv.slice(2).forEach(arg => {
          if (arg.startsWith('--SUB_STORE')) {
            const [key, value] = arg.slice(2).split('=');
            if (key && value !== undefined) {
              process.env[key] = value;
            }
          }
        });
    """.trimIndent()

    init {
        try {
            val nodeRuntimeOptions = NodeRuntimeOptions()
            nodeRuntimeOptions.setConsoleArguments(
                arrayOf(
                    "--allow-fs-read",
                    "--allow-fs-write",

                    // Front end
                    "--SUB_STORE_FRONTEND_HOST=$host",
                    "--SUB_STORE_FRONTEND_PORT=$frontendPort",
                    "--SUB_STORE_FRONTEND_PATH=${Path(caseApp.filesDir.path).resolve("frontend")}",

                    // Back end
                    "--SUB_STORE_BACKEND_API_HOST=$host",
                    "--SUB_STORE_BACKEND_API_PORT=$backendPort",

                    // Database
                    "--SUB_STORE_DATA_BASE_PATH=${Path(caseApp.filesDir.path).resolve("data")}",
                )
            )

            nodeRuntime = V8Host.getNodeInstance().createV8Runtime(nodeRuntimeOptions)

            // register console interceptor
            val javetStandardConsoleInterceptor = JavetStandardConsoleInterceptor(nodeRuntime)
            javetStandardConsoleInterceptor.register(nodeRuntime!!.globalObject)

            // allow eval
            nodeRuntime!!.allowEval(true)

            // set env
            nodeRuntime!!.getExecutor(argv2EnvScript).executeVoid()

        } catch (e: Exception) {
            Timber.w("Create V8Runtime error: %s", e.message)
        }
    }

    fun startServer() {
        val codeFile: File =
            Path(caseApp.filesDir.path).resolve("backend/sub-store.bundle.js").toFile()

        Thread {
            try {
                nodeRuntime!!.getExecutor(codeFile).executeVoid()
                while (shouldAwait) {
                    nodeRuntime!!.await(V8AwaitMode.RunNoWait)
                }
            } catch (e: Exception) {
                Timber.e(e)
            }

            nodeRuntime!!.isStopping = true
            nodeRuntime!!.close()
        }.start()
    }

    fun stopServer() {
        try {
            shouldAwait = false
            nodeRuntime!!.terminateExecution()
            Timber.d("Server stopped")
        } catch (e: Exception) {
            Timber.e(e)
        }
    }
}