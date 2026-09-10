package ano.subcase.service

import android.content.Context
import android.content.Intent
import android.os.Handler
import android.os.Looper
import android.widget.Toast
import androidx.core.content.ContextCompat
import ano.subcase.util.CrashReporter

internal object SubStoreServiceController {
    val store = ServiceStateStore()
    val state = store.state
    private val handler = Handler(Looper.getMainLooper())

    fun start(context: Context) = onMain {
        val app = context.applicationContext
        store.start(
            action = { ContextCompat.startForegroundService(app, Intent(app, SubStoreService::class.java)) },
            onError = { reportFailure(app, it) },
        )
    }

    fun stop(context: Context) = onMain {
        val app = context.applicationContext
        store.stop(
            action = { app.stopService(Intent(app, SubStoreService::class.java)) },
            onError = { reportFailure(app, it) },
        )
    }

    fun reportFailure(context: Context, error: Throwable) {
        CrashReporter.recordException(error)
        Toast.makeText(context, error.message ?: "Sub-Store 服务失败", Toast.LENGTH_LONG).show()
    }

    private fun onMain(action: () -> Unit) {
        if (Looper.myLooper() == Looper.getMainLooper()) action() else handler.post { action() }
    }
}
