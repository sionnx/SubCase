package ano.subcase.server

import ano.subcase.engine.ScriptExecutionTimeoutException
import ano.subcase.model.LoonRequest
import ano.subcase.model.LoonResponse
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.Application
import io.ktor.server.application.ApplicationCall
import io.ktor.server.application.call
import io.ktor.server.cio.CIO
import io.ktor.server.engine.EmbeddedServer
import io.ktor.server.engine.embeddedServer
import io.ktor.server.request.receive
import io.ktor.server.request.uri
import io.ktor.server.response.respondBytes
import io.ktor.server.response.respondFile
import io.ktor.server.routing.route
import io.ktor.server.routing.routing
import timber.log.Timber
import java.io.File
import java.nio.charset.StandardCharsets

/** 分别承载 Sub-Store 前端与脚本 API 的两个 Ktor CIO 服务。 */
class SubCaseHttpServer(
    private val host: String,
    private val frontendPort: Int,
    private val backendPort: Int,
    private val frontendDir: File,
    private val execute: suspend (LoonRequest) -> LoonResponse,
) {
    private var frontendServer: EmbeddedServer<*, *>? = null
    private var backendServer: EmbeddedServer<*, *>? = null

    fun start() {
        try {
            frontendServer = embeddedServer(CIO, host = host, port = frontendPort) {
                configureFrontend()
            }.start(wait = false)
            backendServer = embeddedServer(CIO, host = host, port = backendPort) {
                configureBackend()
            }.start(wait = false)
        } catch (error: Throwable) {
            stop()
            throw error
        }
    }

    fun stop() {
        backendServer?.stop(1_000, 5_000)
        frontendServer?.stop(1_000, 5_000)
        backendServer = null
        frontendServer = null
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
