package ano.subcase.service

import android.app.Service
import android.content.Intent
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.widget.Toast
import ano.subcase.GlobalStatus
import ano.subcase.engine.CaseEngine
import ano.subcase.util.currentServerConfig
import ano.subcase.util.CrashReporter
import ano.subcase.util.NotificationUtil

class SubStoreService : Service() {

    companion object {
        var caseEngine: CaseEngine? = null
            private set
    }

    private val mainHandler = Handler(Looper.getMainLooper())
    private var ownedEngine: CaseEngine? = null
    private var notificationStarted = false

    override fun onBind(intent: Intent): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        try {
            if (caseEngine != null) return START_STICKY
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
            GlobalStatus.isServiceRunning.value = true
            return START_STICKY
        } catch (error: Exception) {
            handleFailure(error)
            stopSelf(startId)
            return START_NOT_STICKY
        }
    }

    private fun handleFailure(error: Throwable) {
        val cleanupError = releaseResources()
        if (cleanupError != null && cleanupError !== error) error.addSuppressed(cleanupError)
        CrashReporter.recordException(error)
        Toast.makeText(this, error.message ?: "Sub-Store 服务失败", Toast.LENGTH_LONG).show()
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
            if (caseEngine === engine) {
                caseEngine = null
                GlobalStatus.isServiceRunning.value = false
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
            releaseResources()?.let(CrashReporter::recordException)
        } finally {
            mainHandler.removeCallbacksAndMessages(null)
            super.onDestroy()
        }
    }
}
