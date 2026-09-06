package ano.subcase.ui

import androidx.lifecycle.ViewModel
import ano.subcase.debug.DebugPort
import ano.subcase.debug.PortOccupier
import ano.subcase.util.ServerEndpoint
import ano.subcase.util.currentServerConfig
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import timber.log.Timber
import java.net.BindException
import java.util.concurrent.Executors

internal data class DebugPortState(val occupied: ServerEndpoint? = null, val busy: Boolean = false)

class DebugViewModel : ViewModel() {
    private val occupier = PortOccupier()
    private val worker = Executors.newSingleThreadExecutor()
    private val states = MutableStateFlow(DebugPort.entries.associateWith { DebugPortState() })
    internal val ports = states.asStateFlow()
    private val errors = Channel<String>(Channel.UNLIMITED)
    val messages = errors.receiveAsFlow()
    @Volatile private var cleared = false

    internal fun setOccupied(port: DebugPort, enabled: Boolean) {
        if (cleared || states.value.getValue(port).busy) return
        val config = currentServerConfig()
        val endpoint = if (port == DebugPort.FRONTEND) config.frontend else config.backend
        states.update { it + (port to it.getValue(port).copy(busy = true)) }
        worker.execute {
            try {
                if (enabled) occupier.occupy(port, endpoint) else occupier.release(port)
                states.update { it + (port to DebugPortState(if (enabled) endpoint else null)) }
            } catch (error: Exception) {
                Timber.e(error)
                states.update { it + (port to it.getValue(port).copy(busy = false)) }
                val target = states.value.getValue(port).occupied ?: endpoint
                val reason = if (error is BindException) "地址已被占用，请先停止使用该端口的服务" else error.message
                if (!cleared) check(errors.trySend("${if (enabled) "占用" else "释放"} $target 失败：$reason").isSuccess)
            }
        }
    }

    override fun onCleared() {
        cleared = true
        // 清理排在所有已提交的绑定之后，覆盖 Activity 结束时仍在执行的绑定。
        worker.execute {
            try {
                occupier.close()
            } catch (error: Exception) {
                Timber.e(error, "释放调试端口失败")
            } finally {
                errors.close()
            }
        }
        worker.shutdown()
        super.onCleared()
    }
}
