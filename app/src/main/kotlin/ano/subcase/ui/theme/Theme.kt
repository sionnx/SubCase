package ano.subcase.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalView
import com.google.accompanist.systemuicontroller.rememberSystemUiController
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.theme.darkColorScheme
import top.yukonga.miuix.kmp.theme.lightColorScheme

private val LightColorScheme = lightColorScheme(
    primary = Blue,
    onPrimary = Color.White,
    background = Color(0xFFF4F4F4),
    onBackground = Color(0xFF121212),
    surface = Color.White,
    onSurface = Color.Black,
    error = Color(0xFFD32F2F),
    onError = Color.White,
)

private val DarkColorScheme = darkColorScheme(
    primary = Blue,
    onPrimary = Color.White,
    background = Color(0xFF121212),
    onBackground = Color(0xFFF4F4F4),
    surface = Color(0xFF202020),
    onSurface = Color.White,
    error = Color(0xFFD32F2F),
    onError = Color.White,
)


@Composable
fun SubCaseTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    // 直接提供固定明暗配色，ThemeController 的 Monet 动态取色模式保持关闭。
    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme
    val view = LocalView.current
    if (!view.isInEditMode) {
        val systemUiController = rememberSystemUiController()
        val useDarkIcons = !darkTheme

        // 内容延伸到系统栏区域，图标明暗跟随当前主题以保证可读性。
        DisposableEffect(systemUiController, useDarkIcons) {
            systemUiController.setSystemBarsColor(
                color = Color.Transparent,
                darkIcons = useDarkIcons
            )
            onDispose {}
        }
    }

    MiuixTheme(
        colors = colorScheme,
        textStyles = SubCaseTextStyles,
        content = content,
    )
}
