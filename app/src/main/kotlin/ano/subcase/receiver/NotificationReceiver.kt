package ano.subcase.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import ano.subcase.caseApp
import ano.subcase.service.SubStoreServiceController
import timber.log.Timber

class NotificationReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        Timber.d("NotificationReceiver onReceive")

        SubStoreServiceController.stop(caseApp)
    }
}
