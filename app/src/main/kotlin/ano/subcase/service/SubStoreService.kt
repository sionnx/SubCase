package ano.subcase.service

import android.app.Service
import android.content.Intent
import android.os.IBinder
import android.widget.Toast
import ano.subcase.GlobalStatus
import ano.subcase.engine.CaseEngine
import ano.subcase.util.ConfigStore
import ano.subcase.util.NetworkUtil
import ano.subcase.util.NotificationUtil
import com.google.firebase.Firebase
import com.google.firebase.crashlytics.crashlytics

class SubStoreService : Service() {

    companion object {
        var caseEngine: CaseEngine? = null
    }

    override fun onBind(intent: Intent): IBinder? {
        return null
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        try {
            if (caseEngine != null) return START_STICKY
            NotificationUtil.startNotification(this)
            val frontendPort = 8080
            val backendPort = 8081

            val allowLan = ConfigStore.isAllowLan

            if (NetworkUtil.isPortInUse(frontendPort) || NetworkUtil.isPortInUse(backendPort)) {
                // port is in use
                throw Exception("Port $frontendPort or $backendPort is already in use")
            }
            caseEngine = CaseEngine(
                context = this,
                backendPort = backendPort,
                frontendPort = frontendPort,
                allowLan = allowLan
            )
            caseEngine!!.startServer()

            GlobalStatus.isServiceRunning.value = true

            return START_STICKY
        } catch (e: Exception) {
            runCatching { caseEngine?.stopServer() }
            caseEngine = null
            e.printStackTrace()
            Firebase.crashlytics.recordException(e)
            Toast.makeText(this, e.message ?: "Sub-Store 后端启动失败", Toast.LENGTH_LONG).show()
            GlobalStatus.isServiceRunning.value = false
            stopSelf(startId)
            return START_NOT_STICKY
        }
    }

    override fun onDestroy() {
        // Service 主线程按 server → WebView → 通知的顺序释放资源。
        caseEngine?.stopServer()
        caseEngine = null
        GlobalStatus.isServiceRunning.value = false
        NotificationUtil.stopNotification()
        super.onDestroy()
    }
}
