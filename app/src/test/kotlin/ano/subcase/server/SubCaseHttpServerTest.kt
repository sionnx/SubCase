package ano.subcase.server

import ano.subcase.model.LoonResponse
import io.ktor.server.engine.EmbeddedServer
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.IOException
import java.net.BindException
import java.net.InetAddress
import java.net.ServerSocket
import java.net.Socket
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

class SubCaseHttpServerTest {
    @get:Rule val temporaryFolder = TemporaryFolder()
    private val servers = mutableListOf<SubCaseHttpServer>()
    private val uncaught = ConcurrentLinkedQueue<Throwable>()
    private var previousHandler: Thread.UncaughtExceptionHandler? = null
    private val host = "127.0.0.1"

    @Before
    fun captureUncaughtExceptions() {
        previousHandler = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { _, error -> uncaught.add(error) }
    }

    @After
    fun closeServersAndCheckUncaughtExceptions() {
        try {
            servers.forEach {
                it.stop()
                awaitScopeClosed(it)
            }
            assertTrue("Unexpected uncaught exceptions: $uncaught", uncaught.isEmpty())
        } finally {
            Thread.setDefaultUncaughtExceptionHandler(previousHandler)
        }
    }

    @Test
    fun `frontend bind failure releases group and is only returned to caller`() {
        val callbacks = ConcurrentLinkedQueue<Throwable>()
        reservePort().use { occupied ->
            val backendPort = freePort()
            val server = server(occupied.localPort, backendPort, callbacks::add)
            val error = assertThrows(HttpServerFailure::class.java) { server.start() }
            assertEquals("前端", error.role)
            assertEquals(occupied.localPort, error.port)
            assertEquals(host, error.host)
            assertTrue(error.duringStartup)
            assertTrue(error.message!!.contains("$host:${occupied.localPort} 已被占用"))
            assertTrue(generateSequence(error as Throwable) { it.cause }.any { it is BindException })
            server.stop()
            server.stop()
            awaitScopeClosed(server)
            assertTrue(callbacks.isEmpty())
            assertPortFree(backendPort)
        }
    }

    @Test
    fun `backend bind failure stops frontend and permits a new group after conflict clears`() {
        val callbacks = ConcurrentLinkedQueue<Throwable>()
        val frontendPort: Int
        val backendPort: Int
        reservePort().use { occupied ->
            backendPort = occupied.localPort
            frontendPort = freePort()
            val server = server(frontendPort, backendPort, callbacks::add)
            val error = assertThrows(HttpServerFailure::class.java) { server.start() }
            assertEquals("后端", error.role)
            assertEquals(backendPort, error.port)
            assertTrue(error.message!!.contains("$host:$backendPort 已被占用"))
            awaitScopeClosed(server)
            assertTrue(callbacks.isEmpty())
            assertPortFree(frontendPort)
        }
        val replacement = server(frontendPort, backendPort, callbacks::add)
        replacement.start()
        assertListening(frontendPort)
        assertListening(backendPort)
        replacement.stop()
        awaitScopeClosed(replacement)
        assertPortFree(frontendPort)
        assertPortFree(backendPort)
        assertTrue(callbacks.isEmpty())
    }

    @Test
    fun `both servers support repeated group start and stop`() {
        val (frontendPort, backendPort) = freePorts()
        val callbacks = ConcurrentLinkedQueue<Throwable>()
        repeat(3) {
            val server = server(frontendPort, backendPort, callbacks::add)
            server.start()
            assertListening(frontendPort)
            assertListening(backendPort)
            assertThrows(IllegalStateException::class.java) { server.start() }
            // 重复启动被拒绝后，原有两个监听仍有效。
            assertListening(frontendPort)
            assertListening(backendPort)
            server.stop()
            server.stop()
            awaitScopeClosed(server)
            assertPortFree(frontendPort)
            assertPortFree(backendPort)
        }
        assertTrue(callbacks.isEmpty())
    }

    @Test
    fun `failure in either running CIO engine triggers one callback and group cleanup`() {
        for (field in listOf("frontendServer", "backendServer")) {
            val (frontendPort, backendPort) = freePorts()
            val callbackCount = AtomicInteger()
            val cleaned = CompletableFuture<Throwable>()
            val cleanupExecutor = Executors.newSingleThreadExecutor()
            lateinit var server: SubCaseHttpServer
            server = server(frontendPort, backendPort) { error ->
                callbackCount.incrementAndGet()
                // 对应 Android 主线程调度，清理在引擎完成回调之外执行。
                cleanupExecutor.execute {
                    try {
                        server.stop()
                        cleaned.complete(error)
                    } catch (cleanupError: Throwable) {
                        cleaned.completeExceptionally(cleanupError)
                    }
                }
            }
            try {
                server.start()
                val embedded = endpoint(server, field)
                val context = embedded.application.parentCoroutineContext
                val engineJob = embedded.engine.javaClass.getDeclaredField("serverJob").apply {
                    isAccessible = true
                }.get(embedded.engine) as Job
                val injected = IllegalStateException("Injected CIO engine failure", IOException("Socket failed"))
                // 直接使真实 CIO 引擎的子协程失败，覆盖引擎取消和异常传播链。
                CoroutineScope(context + engineJob).launch { throw injected }
                val error = cleaned.get(10, TimeUnit.SECONDS) as HttpServerFailure
                assertSame(injected, error.cause)
                assertEquals(if (field == "frontendServer") "前端" else "后端", error.role)
                assertFalse(error.duringStartup)
                awaitScopeClosed(server)
                assertPortFree(frontendPort)
                assertPortFree(backendPort)
                context[CoroutineExceptionHandler]!!.handleException(context, injected)
                context[CoroutineExceptionHandler]!!.handleException(context, injected.cause!!)
                assertTrue("Wrapped and original causes represent one failure", error.suppressed.isEmpty())
                assertEquals(1, callbackCount.get())
            } finally {
                server.stop()
                cleanupExecutor.shutdown()
                assertTrue(cleanupExecutor.awaitTermination(10, TimeUnit.SECONDS))
            }
        }
    }

    @Test
    fun `normal cancellation and delayed failure from stopped group skip failure callback`() {
        val (frontendPort, backendPort) = freePorts()
        val callbacks = ConcurrentLinkedQueue<Throwable>()
        val old = server(frontendPort, backendPort, callbacks::add)
        old.start()
        val context = endpoint(old, "frontendServer").application.parentCoroutineContext
        val handler = context[CoroutineExceptionHandler]!!
        handler.handleException(context, CancellationException("Normal cancellation"))
        assertTrue(callbacks.isEmpty())
        old.stop()
        awaitScopeClosed(old)
        val replacement = server(frontendPort, backendPort, callbacks::add)
        replacement.start()
        handler.handleException(context, IllegalStateException("Delayed old engine failure"))
        assertTrue(callbacks.isEmpty())
        assertListening(frontendPort)
        assertListening(backendPort)
    }

    private fun server(frontendPort: Int, backendPort: Int, onFailure: (Throwable) -> Unit) =
        SubCaseHttpServer(
            host = host,
            frontendPort = frontendPort,
            backendPort = backendPort,
            frontendDir = temporaryFolder.newFolder(),
            execute = { LoonResponse(body = "ok") },
            onFailure = onFailure,
        ).also(servers::add)

    private fun endpoint(server: SubCaseHttpServer, field: String): EmbeddedServer<*, *> =
        SubCaseHttpServer::class.java.getDeclaredField(field).apply { isAccessible = true }
            .get(server) as EmbeddedServer<*, *>

    private fun awaitScopeClosed(server: SubCaseHttpServer) = runBlocking {
        val job = SubCaseHttpServer::class.java.getDeclaredField("serverJob").apply {
            isAccessible = true
        }.get(server) as Job
        withTimeout(10_000) { job.join() }
    }

    private fun reservePort() = ServerSocket(0, 50, InetAddress.getByName(host))
    private fun freePort() = reservePort().use { it.localPort }
    private fun freePorts() = reservePort().use { first ->
        reservePort().use { second -> first.localPort to second.localPort }
    }
    private fun assertPortFree(port: Int) {
        ServerSocket(port, 50, InetAddress.getByName(host)).use { assertEquals(port, it.localPort) }
    }
    private fun assertListening(port: Int) {
        Socket(host, port).use { assertTrue(it.isConnected) }
    }
}
