package ano.subcase.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import ano.subcase.caseApp
import ano.subcase.service.SubStoreService
import timber.log.Timber

class NotificationReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        Timber.d("NotificationReceiver onReceive")

        val serviceIntent = Intent(caseApp, SubStoreService::class.java)
        caseApp.stopService(serviceIntent)
    }
}
