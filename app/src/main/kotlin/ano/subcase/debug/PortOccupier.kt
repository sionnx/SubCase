/**
 * 调试用端口占用器：绑定监听 socket，主动占住前端/后端地址。
 *
 * 用于在真机上验证服务启动时的端口冲突路径。本文件只负责 socket 生命周期，
 * 调用方（[ano.subcase.ui.DebugViewModel]）在单线程后台串行调度 occupy / release / close。
 */
package ano.subcase.debug

import ano.subcase.util.ServerEndpoint
import java.net.InetSocketAddress
import java.net.ServerSocket

/** 可被调试占用的服务端口角色。 */
internal enum class DebugPort { FRONTEND, BACKEND }

/** 仅持有监听 socket；调用方在后台串行调度操作。 */
internal class PortOccupier(
    private val createSocket: () -> ServerSocket = { ServerSocket() },
) : AutoCloseable {
    private val sockets = mutableMapOf<DebugPort, ServerSocket>()
    private var closed = false

    /**
     * 在 [endpoint] 上绑定监听 socket，使该端口无法被服务再次占用。
     *
     * @throws IllegalStateException 管理器已关闭，或该端口已被本实例占用
     * @throws java.net.BindException 目标地址已被其他进程占用
     */
    @Synchronized
    fun occupy(port: DebugPort, endpoint: ServerEndpoint) {
        check(!closed) { "调试占用管理器已关闭" }
        check(port !in sockets) { "该端口已被调试占用" }
        val socket = createSocket()
        try {
            // 允许服务停止后的旧连接继续收尾；活动监听仍会使 bind 失败。
            socket.reuseAddress = true
            socket.bind(InetSocketAddress(endpoint.host, endpoint.port))
            sockets[port] = socket
        } catch (error: Throwable) {
            try {
                socket.close()
            } catch (cleanup: Throwable) {
                error.addSuppressed(cleanup)
            }
            throw error
        }
    }

    /** 关闭并移除指定端口的监听 socket；未占用时为无操作。 */
    @Synchronized
    fun release(port: DebugPort) {
        sockets[port]?.close()
        sockets.remove(port)
    }

    /**
     * 标记已关闭，并释放全部已占用端口。
     * 任一释放失败时抛出该异常，其余失败挂到 suppressed。
     */
    @Synchronized
    override fun close() {
        closed = true
        var failure: Throwable? = null
        DebugPort.entries.forEach { port ->
            try {
                release(port)
            } catch (error: Throwable) {
                val previous = failure
                if (previous == null) failure = error else previous.addSuppressed(error)
            }
        }
        failure?.let { throw it }
    }
}
