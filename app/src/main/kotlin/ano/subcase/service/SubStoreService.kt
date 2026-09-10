package ano.subcase.service

import android.app.Service
import android.content.Intent
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import ano.subcase.engine.CaseEngine
import ano.subcase.util.currentServerConfig
import ano.subcase.util.NotificationUtil
import ano.subcase.util.runSubStoreUpdateLoop
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

class SubStoreService : Service() {

    companion object {
        var caseEngine: CaseEngine? = null
            private set
    }

    private val mainHandler = Handler(Looper.getMainLooper())
    private val updateLoopScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var ownedEngine: CaseEngine? = null
    private var notificationStarted = false
    private var updateLoopJob: Job? = null

    override fun onBind(intent: Intent): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        try {
            if (caseEngine != null) return START_STICKY
            SubStoreServiceController.store.starting()
            notificationStarted = true
            NotificationUtil.startNotification(this)
            val config = currentServerConfig()
            val engine = CaseEngine(
                context = this,
                backendPort = config.backend.port,
                frontendPort = config.frontend.port,
                host = config.frontend.host,
                onFailure = { source, error ->
                    mainHandler.post {
                        // 仅当前 Service 所持有的实例可以触发整组停止。
                        if (ownedEngine === source && caseEngine === source) {
                            handleFailure(error)
                            stopSelf()
                        }
                    }
                },
            )
            ownedEngine = engine
            caseEngine = engine
            engine.startServer()
            startUpdateLoop()
            SubStoreServiceController.store.running()
            return START_STICKY
        } catch (error: Exception) {
            handleFailure(error)
            stopSelf(startId)
            return START_NOT_STICKY
        }
    }

    private fun handleFailure(error: Throwable) {
        val failure = if (SubStoreServiceController.state.value.phase == ServicePhase.Starting)
            ServiceFailure.Startup else ServiceFailure.Runtime
        val cleanupError = releaseResources()
        SubStoreServiceController.store.stopped(failure)
        if (cleanupError != null && cleanupError !== error) error.addSuppressed(cleanupError)
        SubStoreServiceController.reportFailure(this, error)
    }

    private fun releaseResources(): Throwable? {
        val engine = ownedEngine
        ownedEngine = null // 先失效回调，再执行可能耗时的清理。
        var failure: Throwable? = null
        fun cleanup(action: () -> Unit) {
            try {
                action()
            } catch (error: Throwable) {
                val previous = failure
                if (previous == null) failure = error
                else if (previous !== error) previous.addSuppressed(error)
            }
        }
        try {
            cleanup { engine?.stopServer() }
        } finally {
            stopUpdateLoop()
            if (caseEngine === engine) {
                caseEngine = null
                SubStoreServiceController.store.stopped()
            }
            if (notificationStarted) {
                notificationStarted = false
                cleanup { stopForeground(STOP_FOREGROUND_REMOVE) }
                cleanup { NotificationUtil.stopNotification() }
            }
        }
        return failure
    }

    override fun onDestroy() {
        try {
            releaseResources()?.let { error ->
                SubStoreServiceController.store.stopped(ServiceFailure.Shutdown)
                val message = "停止 Sub-Store 服务失败：${error.message ?: error.javaClass.simpleName}"
                SubStoreServiceController.reportFailure(this, IllegalStateException(message, error))
            }
        } finally {
            stopUpdateLoop()
            updateLoopScope.cancel()
            mainHandler.removeCallbacksAndMessages(null)
            super.onDestroy()
        }
    }

    /** 服务运行后启动 SubStore 周期检查；已有活跃任务则不重复启动。 */
    private fun startUpdateLoop() {
        if (updateLoopJob?.isActive == true) return
        updateLoopJob = updateLoopScope.launch {
            runSubStoreUpdateLoop()
        }
    }

    /** 取消周期检查并清空 Job，避免服务停止后仍后台拉版本。 */
    private fun stopUpdateLoop() {
        updateLoopJob?.cancel()
        updateLoopJob = null
    }
}
