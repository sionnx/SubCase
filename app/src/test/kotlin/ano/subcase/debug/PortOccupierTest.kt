package ano.subcase.debug

import ano.subcase.util.ServerEndpoint
import org.junit.Assert.*
import org.junit.Test
import java.net.BindException
import java.net.InetAddress
import java.net.ServerSocket
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

class PortOccupierTest {
    private val host = InetAddress.getByName("127.0.0.1")
    private fun socket(port: Int = 0) = ServerSocket(port, 50, host)
    private fun freePort() = socket().use { it.localPort }

    @Test
    fun `single and dual occupancy release independently and can bind again`() {
        val first: Int
        val second: Int
        socket().use { a -> socket().use { b -> first = a.localPort; second = b.localPort } }
        PortOccupier().use { owner ->
            owner.occupy(DebugPort.FRONTEND, ServerEndpoint(host.hostAddress!!, first))
            assertThrows(BindException::class.java) { socket(first).close() }
            owner.occupy(DebugPort.BACKEND, ServerEndpoint(host.hostAddress!!, second))
            assertThrows(BindException::class.java) { socket(second).close() }
            owner.release(DebugPort.FRONTEND)
            socket(first).close()
            assertThrows(BindException::class.java) { socket(second).close() }
            owner.occupy(DebugPort.FRONTEND, ServerEndpoint(host.hostAddress!!, first))
        }
        socket(first).close()
        socket(second).close()
    }

    @Test
    fun `bind conflict closes failed socket and leaves other occupancy intact`() {
        val created = mutableListOf<ServerSocket>()
        PortOccupier { ServerSocket().also(created::add) }.use { owner ->
            val port = freePort()
            owner.occupy(DebugPort.FRONTEND, ServerEndpoint("127.0.0.1", port))
            socket().use { occupied ->
                assertThrows(BindException::class.java) {
                    owner.occupy(DebugPort.BACKEND, ServerEndpoint("127.0.0.1", occupied.localPort))
                }
                assertTrue(created.last().isClosed)
                assertThrows(BindException::class.java) { socket(port).close() }
            }
        }
        assertTrue(created.all { it.isClosed })
    }

    @Test
    fun `close racing an in flight bind releases its eventual socket`() {
        val binding = CountDownLatch(1)
        val proceed = CountDownLatch(1)
        val port = freePort()
        val owner = PortOccupier {
            binding.countDown()
            check(proceed.await(5, TimeUnit.SECONDS))
            ServerSocket()
        }
        val executor = Executors.newFixedThreadPool(2)
        try {
            val bind = executor.submit { owner.occupy(DebugPort.FRONTEND, ServerEndpoint("127.0.0.1", port)) }
            assertTrue(binding.await(5, TimeUnit.SECONDS))
            val close = executor.submit { owner.close() }
            proceed.countDown()
            bind.get(5, TimeUnit.SECONDS)
            close.get(5, TimeUnit.SECONDS)
            socket(port).close()
            assertThrows(IllegalStateException::class.java) {
                owner.occupy(DebugPort.BACKEND, ServerEndpoint("127.0.0.1", port))
            }
            owner.close()
        } finally {
            proceed.countDown()
            executor.shutdownNow()
            owner.close()
        }
    }
}
