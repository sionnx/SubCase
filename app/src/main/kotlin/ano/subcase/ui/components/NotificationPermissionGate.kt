package ano.subcase.ui.components

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.core.net.toUri
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import ano.subcase.R
import ano.subcase.ui.NotificationPermissionAction
import ano.subcase.ui.resolveNotificationPermissionAction
import ano.subcase.util.ConfigStore
import timber.log.Timber
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.window.WindowDialog

/**
 * 通知权限闸门：未授权时挡住主界面，授权后才渲染 [content]。
 *
 * Android 13 及以上先走系统运行时权限；用户拒绝或渠道被关闭后，改为引导到系统设置。
 * 返回前台时会重新读取实际权限，避免设置页改完权限后界面不刷新。
 *
 * @param activity 用于检查权限、拉起系统权限框或通知设置页
 * @param content 权限已授予时展示的正式界面
 */
@Composable
fun NotificationPermissionGate(activity: ComponentActivity, content: @Composable () -> Unit) {
    /** 读取当前应采取的动作：已授权、请求运行时权限，或跳转系统设置。 */
    fun readAction(): NotificationPermissionAction = resolveNotificationPermissionAction(
        sdkInt = Build.VERSION.SDK_INT,
        runtimePermissionGranted = Build.VERSION.SDK_INT < 33 ||
            ContextCompat.checkSelfPermission(activity, Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED,
        notificationsEnabled = NotificationManagerCompat.from(activity).areNotificationsEnabled(),
        previouslyRequested = ConfigStore.notificationPermissionRequested,
    )

    // Recheck actual permission on recreation; retain the request flow across rotation.
    var action by remember(activity) { mutableStateOf(readAction()) }
    var requestInFlight by rememberSaveable { mutableStateOf(false) }
    var launchFailed by rememberSaveable { mutableStateOf(false) }
    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        action = readAction()
        requestInFlight = false
    }
    val settingsLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        action = readAction()
        requestInFlight = false
    }

    /** 打开应用通知设置；系统不支持该页时回退到应用详情页。 */
    fun openSettings() {
        if (requestInFlight) return
        requestInFlight = true
        launchFailed = false
        try {
            settingsLauncher.launch(
                Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                    .putExtra(Settings.EXTRA_APP_PACKAGE, activity.packageName)
            )
        } catch (error: RuntimeException) {
            Timber.w(error, "Unable to open notification settings; trying app details")
            try {
                settingsLauncher.launch(
                    Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                        "package:${activity.packageName}".toUri())
                )
            } catch (fallbackError: RuntimeException) {
                Timber.e(fallbackError, "Unable to open app details")
                requestInFlight = false
                launchFailed = true
            }
        }
    }

    DisposableEffect(activity) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) action = readAction()
        }
        activity.lifecycle.addObserver(observer)
        onDispose { activity.lifecycle.removeObserver(observer) }
    }

    if (action == NotificationPermissionAction.GRANTED && !requestInFlight) {
        content()
        return
    }

    BackHandler { /* The permission gate remains until authorization succeeds. */ }
    Box(Modifier.fillMaxSize().background(MiuixTheme.colorScheme.background))
    // Remove the window immediately before launching system UI, avoiding overlapping dialogs.
    if (!requestInFlight) {
        WindowDialog(
            show = true,
            title = stringResource(R.string.notification_permission_title),
            onDismissRequest = null,
        ) {
            Column {
                Text(
                    text = stringResource(R.string.notification_permission_description),
                    color = MiuixTheme.colorScheme.onSurface,
                )
                if (launchFailed) {
                    Spacer(Modifier.height(12.dp))
                    Text(
                        text = stringResource(R.string.notification_permission_failed),
                        color = MiuixTheme.colorScheme.onSurface,
                    )
                }
                Spacer(Modifier.height(16.dp))
                TextButton(
                    text = stringResource(
                        if (action != NotificationPermissionAction.SETTINGS) {
                            R.string.notification_permission_continue
                        } else {
                            R.string.notification_permission_settings
                        }
                    ),
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.textButtonColorsPrimary(),
                    onClick = {
                        if (!requestInFlight) {
                            action = readAction()
                            launchFailed = false
                            if (action != NotificationPermissionAction.GRANTED) {
                                if (action != NotificationPermissionAction.SETTINGS) {
                                    requestInFlight = true
                                    try {
                                        permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                                        // Record the attempt only after launch succeeds; every later attempt uses settings.
                                        ConfigStore.notificationPermissionRequested = true
                                    } catch (error: RuntimeException) {
                                        Timber.e(error, "Unable to request notification permission")
                                        requestInFlight = false
                                        launchFailed = true
                                    }
                                } else {
                                    openSettings()
                                }
                            }
                        }
                    },
                )
            }
        }
    }
}
