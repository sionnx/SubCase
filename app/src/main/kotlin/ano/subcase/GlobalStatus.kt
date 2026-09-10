package ano.subcase

import androidx.compose.runtime.mutableStateOf

object GlobalStatus {
    var isWifi = mutableStateOf(false)
    var lanIP = mutableStateOf("")

}
