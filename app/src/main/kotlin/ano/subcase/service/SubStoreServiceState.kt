package ano.subcase.service

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

internal enum class ServicePhase { Stopped, Starting, Running, Stopping }
internal enum class ServiceFailure { Startup, Runtime, Shutdown }
internal data class SubStoreServiceState(
    val phase: ServicePhase = ServicePhase.Stopped,
    val failure: ServiceFailure? = null,
) {
    val isRunning get() = phase == ServicePhase.Running
    val isBusy get() = phase == ServicePhase.Starting || phase == ServicePhase.Stopping
}

/** 所有调用由控制器及 Android Service 在主线程串行执行。 */
internal class ServiceStateStore {
    private val mutableState = MutableStateFlow(SubStoreServiceState())
    val state = mutableState.asStateFlow()

    fun start(action: () -> Unit, onError: (Exception) -> Unit) {
        if (state.value.phase != ServicePhase.Stopped) return
        starting()
        try {
            action()
        } catch (error: Exception) {
            stopped(ServiceFailure.Startup)
            onError(error)
        }
    }

    fun stop(action: () -> Boolean, onError: (Exception) -> Unit) {
        if (state.value.phase != ServicePhase.Running) return
        mutableState.value = SubStoreServiceState(ServicePhase.Stopping)
        try {
            if (!action()) stopped()
        } catch (error: Exception) {
            // 请求被拒绝时服务仍然运行，允许用户重新停止。
            mutableState.value = SubStoreServiceState(ServicePhase.Running, ServiceFailure.Shutdown)
            onError(error)
        }
    }

    fun starting() { mutableState.value = SubStoreServiceState(ServicePhase.Starting) }
    fun running() { mutableState.value = SubStoreServiceState(ServicePhase.Running) }
    fun stopped(failure: ServiceFailure? = state.value.failure) {
        mutableState.value = SubStoreServiceState(failure = failure)
    }
}
