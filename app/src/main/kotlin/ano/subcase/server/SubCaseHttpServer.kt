package ano.subcase.server

import ano.subcase.engine.ScriptExecutionTimeoutException
import ano.subcase.model.LoonRequest
import ano.subcase.model.LoonResponse
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.Application
import io.ktor.server.application.ApplicationCall
import io.ktor.server.cio.CIO
import io.ktor.server.engine.EmbeddedServer
import io.ktor.server.engine.embeddedServer
import io.ktor.server.request.receive
import io.ktor.server.request.uri
import io.ktor.server.response.respondBytes
import io.ktor.server.response.respondFile
import io.ktor.server.routing.route
import io.ktor.server.routing.routing
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import timber.log.Timber
import java.io.File
import java.net.BindException
import java.nio.charset.StandardCharsets

/** 分别承载 Sub-Store 前端与脚本 API 的两个 Ktor CIO 服务。 */
class SubCaseHttpServer(
    private val host: String,
    private val frontendPort: Int,
    private val backendPort: Int,
    private val frontendDir: File,
    private val execute: suspend (LoonRequest) -> LoonResponse,
    private val onFailure: (Throwable) -> Unit,
) {
    private var frontendServer: EmbeddedServer<*, *>? = null
    private var backendServer: EmbeddedServer<*, *>? = null
    private val serverJob = SupervisorJob()
    private val serverScope = CoroutineScope(serverJob + Dispatchers.IO)
    private val stateLock = Any()
    private var state = State.NEW
    private var firstFailure: HttpServerFailure? = null

    private enum class State { NEW, STARTING, RUNNING, FAILED, STOPPING, STOPPED }

    // 启停串行执行；异常处理器只持有 stateLock，避免等待引擎退出时相互阻塞。
    @Synchronized
    fun start() {
        synchronized(stateLock) {
            check(state == State.NEW) { "Sub-Store server 实例已使用，请创建新实例" }
            state = State.STARTING
        }
        try {
            startEndpoint("前端", frontendPort) { configureFrontend() }
            synchronized(stateLock) { firstFailure?.let { throw it } }
            startEndpoint("后端", backendPort) { configureBackend() }
            synchronized(stateLock) {
                firstFailure?.let { throw it }
                state = State.RUNNING
            }
        } catch (error: Throwable) {
            val failure = synchronized(stateLock) { firstFailure ?: error }
            try {
                stop()
            } catch (cleanupError: Throwable) {
                if (cleanupError !== failure) failure.addSuppressed(cleanupError)
            }
            throw failure
        }
    }

    private fun startEndpoint(role: String, port: Int, module: suspend Application.() -> Unit) {
        try {
            val server = serverScope.embeddedServer(
                CIO,
                host = host,
                port = port,
                parentCoroutineContext = CoroutineExceptionHandler { _, error ->
                    handleEngineFailure(role, port, error)
                },
                module = module,
            )
            // start 抛错时仍保留实例，以便整组回滚。
            if (role == "前端") frontendServer = server else backendServer = server
            server.start(wait = false)
        } catch (error: Throwable) {
            throw synchronized(stateLock) {
                firstFailure ?: HttpServerFailure(role, host, port, true, error).also {
                    firstFailure = it
                }
            }
        }
    }

    private fun handleEngineFailure(role: String, port: Int, error: Throwable) {
        if (error is CancellationException) return
        val failure = synchronized(stateLock) {
            firstFailure?.let { previous ->
                if (generateSequence(previous as Throwable) { it.cause }.none { it === error }) {
                    previous.addSuppressed(error)
                    Timber.e(error, "%s服务伴随异常：%s:%d", role, host, port)
                }
                return
            }
            if (state == State.STOPPING || state == State.STOPPED) {
                // 正常取消已在上方过滤；额外的退出异常仍需可见。
                Timber.e(error, "%s服务退出异常：%s:%d", role, host, port)
                return
            }
            val failure = HttpServerFailure(role, host, port, state == State.STARTING, error)
            firstFailure = failure
            if (state == State.STARTING) return // 由 start 统一回滚和上报。
            state = State.FAILED
            failure
        }
        // 调用方将清理调度到主线程，避免在引擎的完成回调中等待引擎自身。
        onFailure(failure)
    }

    @Synchronized
    fun stop() {
        synchronized(stateLock) {
            if (state == State.STOPPED) return
            state = State.STOPPING
        }
        var failure: Throwable? = null
        try {
            for (server in listOfNotNull(backendServer, frontendServer)) {
                try {
                    // 立即取消，跳过关闭等待，让主线程继续执行 WebView 清理。
                    server.stop(
                        gracePeriodMillis = 0,
                        timeoutMillis = 0,
                    )
                } catch (error: Throwable) {
                    val previous = failure
                    if (previous == null) failure = error
                }
            }
        } finally {
            serverJob.cancel()
            backendServer = null
            frontendServer = null
            synchronized(stateLock) { state = State.STOPPED }
        }
        failure?.let { throw it }
    }

    private fun Application.configureFrontend() {
        routing {
            route("/{path...}") {
                handle {
                    val relative = call.parameters.getAll("path")?.joinToString("/").orEmpty()
                    val root = frontendDir.canonicalFile
                    val requested = File(root, relative).canonicalFile
                    val file = when {
                        requested.toPath().startsWith(root.toPath()) && requested.isFile -> requested
                        else -> File(root, "index.html")
                    }
                    if (file.isFile) {
                        call.respondFile(file)
                    } else {
                        call.respondBytes(
                            "前端资源尚未安装".toByteArray(StandardCharsets.UTF_8),
                            status = HttpStatusCode.ServiceUnavailable,
                        )
                    }
                }
            }
        }
    }

    private fun Application.configureBackend() {
        routing {
            route("/{path...}") {
                handle {
                    val rawBody = call.receive<ByteArray>()
                    if (rawBody.size > MAX_BODY_BYTES) {
                        call.appendFallbackCorsHeaders()
                        call.respondBytes(ByteArray(0), status = HttpStatusCode.PayloadTooLarge)
                        return@handle
                    }
                    val headers = buildMap {
                        call.request.headers.entries().forEach { (name, values) ->
                            put(name, values.joinToString(", "))
                        }
                    }
                    val request = LoonRequest(
                        url = "https://sub.store${call.request.uri}",
                        method = call.request.local.method.value,
                        headers = headers,
                        body = rawBody.takeIf { it.isNotEmpty() }
                            ?.toString(StandardCharsets.UTF_8),
                    )
                    try {
                        val response = execute(request)
                        response.headers.forEach { (name, value) ->
                            if (!name.equals(HttpHeaders.ContentLength, true) &&
                                !name.equals(HttpHeaders.TransferEncoding, true)
                            ) {
                                call.response.headers.append(name, value, safeOnly = false)
                            }
                        }
                        val status = runCatching { HttpStatusCode.fromValue(response.status) }
                            .getOrDefault(HttpStatusCode.InternalServerError)
                        call.respondBytes(response.body.toByteArray(StandardCharsets.UTF_8), status = status)
                    } catch (error: ScriptExecutionTimeoutException) {
                        Timber.e(error)
                        call.appendFallbackCorsHeaders()
                        call.respondBytes(
                            "Sub-Store script timeout".toByteArray(StandardCharsets.UTF_8),
                            status = HttpStatusCode.GatewayTimeout,
                        )
                    } catch (error: Throwable) {
                        Timber.e(error)
                        call.appendFallbackCorsHeaders()
                        call.respondBytes(
                            (error.message ?: "Sub-Store script failed").toByteArray(StandardCharsets.UTF_8),
                            status = HttpStatusCode.InternalServerError,
                        )
                    }
                }
            }
        }
    }

    /** 让前端在脚本失败时读取真实状态码和错误文本。 */
    private fun ApplicationCall.appendFallbackCorsHeaders() {
        val origin = request.headers[HttpHeaders.Origin] ?: "*"
        response.headers.append(HttpHeaders.AccessControlAllowOrigin, origin, safeOnly = false)
        response.headers.append(HttpHeaders.AccessControlAllowMethods, ALLOWED_METHODS, safeOnly = false)
        response.headers.append(HttpHeaders.AccessControlAllowHeaders, ALLOWED_HEADERS, safeOnly = false)
        if (origin != "*") response.headers.append(HttpHeaders.Vary, HttpHeaders.Origin, safeOnly = false)
    }

    companion object {
        private const val MAX_BODY_BYTES = 64 * 1024 * 1024
        private const val ALLOWED_METHODS = "GET, POST, PUT, PATCH, DELETE, HEAD, OPTIONS"
        private const val ALLOWED_HEADERS = "Origin, X-Requested-With, Content-Type, Accept"
    }
}

/** 同时保留用户可读的地址信息和 Ktor 原始异常链。 */
internal class HttpServerFailure(
    val role: String,
    val host: String,
    val port: Int,
    val duringStartup: Boolean,
    cause: Throwable,
) : Exception(
    buildString {
        append(role).append(if (duringStartup) "服务启动失败：" else "服务运行失败：")
        append(host).append(':').append(port)
        if (generateSequence(cause) { it.cause }.any { it is BindException }) {
            append(" 已被占用")
        } else {
            append("，").append(cause.message ?: cause.javaClass.simpleName)
        }
    },
    cause,
)
