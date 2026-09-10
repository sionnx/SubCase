package ano.subcase.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import ano.subcase.caseApp
import ano.subcase.service.SubStoreServiceController
import ano.subcase.util.ConfigStore

class MainViewModel : ViewModel() {

    var allowLan by mutableStateOf(false)
    var allowCrashReport by mutableStateOf(true)

    init {
        allowLan = ConfigStore.isAllowLan
        allowCrashReport = ConfigStore.isAllowCrashReport
    }

    fun startService() {
        SubStoreServiceController.start(caseApp)
    }

    fun stopService() {
        SubStoreServiceController.stop(caseApp)
    }
}
