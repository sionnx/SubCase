package ano.subcase.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import top.yukonga.miuix.kmp.basic.SwitchDefaults

@Composable
fun switchColors() = SwitchDefaults.switchColors(
    checkedThumbColor = Color.White,
    checkedTrackColor = Color(0xFF478EF2),
    uncheckedThumbColor = Color.White,
    uncheckedTrackColor = if (isSystemInDarkTheme()) {
        Color(0xFF27272A)
    } else {
        Color(0xFFE8E8E8)
    },
)
