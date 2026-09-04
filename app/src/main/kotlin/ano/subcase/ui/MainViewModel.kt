package ano.subcase.ui

import android.content.Intent
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import ano.subcase.caseApp
import ano.subcase.service.SubStoreService
import ano.subcase.util.ConfigStore

class MainViewModel : ViewModel() {

    var allowLan by mutableStateOf(false)
    var allowCrashReport by mutableStateOf(true)

    init {
        allowLan = ConfigStore.isAllowLan
        allowCrashReport = ConfigStore.isAllowCrashReport
    }

    fun startService() {
        val intent = Intent(caseApp, SubStoreService::class.java)
        intent.putExtra("backendPort", 8081)
        intent.putExtra("frontendPort", 8080)
        intent.putExtra("allowLan", allowLan)
        caseApp.startService(intent)
    }

    fun stopService() {
        caseApp.stopService(Intent(caseApp, SubStoreService::class.java))
    }
}
