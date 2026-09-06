package ano.subcase.ui

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.lifecycle.viewmodel.compose.viewModel
import ano.subcase.BuildConfig
import ano.subcase.GlobalStatus
import ano.subcase.ui.screens.DebugScreen
import ano.subcase.ui.components.StartupUpdateCoordinator
import ano.subcase.ui.screens.HomeScreen
import ano.subcase.ui.screens.SettingsScreen
import ano.subcase.ui.theme.SubCaseTheme
import ano.subcase.util.NetworkUtil
import ano.subcase.ui.components.NotificationPermissionGate

lateinit var caseActivity: MainActivity

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        caseActivity = this

        super.onCreate(savedInstanceState)

        enableEdgeToEdge()

        setContent {
            SubCaseTheme {
                NotificationPermissionGate(activity = this@MainActivity) {
                    val navController = rememberNavController()
                    SetupNavGraph(navController = navController)
                    val startupUpdateViewModel = viewModel<StartupUpdateViewModel>()
                    StartupUpdateCoordinator(startupUpdateViewModel)
                }
            }
        }

        GlobalStatus.lanIP.value = NetworkUtil.getLanIp() ?: ""

        // prepare network
        NetworkUtil.startObserve()
    }

    override fun onDestroy() {
        super.onDestroy()
        NetworkUtil.stopObserve()
    }

}

@Composable
fun SetupNavGraph(navController: NavHostController) {
    // 在 NavHost 外获取 Activity 的 ViewModelStore，返回首页和旋转时保留占用。
    val debugViewModel = if (BuildConfig.DEBUG) viewModel<DebugViewModel>() else null
    NavHost(navController = navController, startDestination = "home_screen") {
        if (BuildConfig.DEBUG) {
            composable(
                route = "debug_screen",
                enterTransition = {
                    slideIntoContainer(AnimatedContentTransitionScope.SlideDirection.Left, tween(300))
                },
                popExitTransition = {
                    slideOutOfContainer(AnimatedContentTransitionScope.SlideDirection.Right, tween(300))
                },
            ) {
                DebugScreen(navController, checkNotNull(debugViewModel))
            }
        }
        composable("home_screen") { HomeScreen(navController = navController) }
        composable(
            route = "settings_screen",
            enterTransition = {
                slideIntoContainer(
                    AnimatedContentTransitionScope.SlideDirection.Left,
                    animationSpec = tween(300)
                )
            },
            popExitTransition = {
                slideOutOfContainer(
                    AnimatedContentTransitionScope.SlideDirection.Right,
                    animationSpec = tween(300)
                )
            }
        ) {
            SettingsScreen(navController = navController)
        }
    }
}
