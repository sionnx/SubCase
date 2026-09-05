package ano.subcase.ui.screens

import android.webkit.WebView
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import ano.subcase.BuildConfig
import ano.subcase.GlobalStatus
import ano.subcase.R
import ano.subcase.ui.MainViewModel
import ano.subcase.ui.components.SubStoreUpdateDialog
import ano.subcase.ui.components.buildSubStoreUrl
import ano.subcase.ui.theme.Blue
import ano.subcase.ui.theme.switchColors
import ano.subcase.util.ConfigStore
import ano.subcase.util.SubStore
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.CardDefaults
import top.yukonga.miuix.kmp.basic.CircularProgressIndicator
import top.yukonga.miuix.kmp.basic.HorizontalDivider
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.SmallTopAppBar
import top.yukonga.miuix.kmp.basic.Switch
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.theme.MiuixTheme

@Composable
fun SettingsScreen(navController: NavController) {

    val mViewModel = viewModel<MainViewModel>()
    var showSubStoreUpdateDialog by rememberSaveable { mutableStateOf(false) }

    Scaffold(
        containerColor = MiuixTheme.colorScheme.background,
        topBar = {
            SettingsTopBar(
                navController = navController,
                onUpdateAvailable = { showSubStoreUpdateDialog = true }
            )
        }
    ) {
        Column(
            modifier = Modifier
                .padding(it)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(start = 20.dp, end = 20.dp, bottom = 20.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(modifier = Modifier.padding(10.dp))
            FrontEndCard(mViewModel)
            Spacer(modifier = Modifier.padding(10.dp))
            BackEndCard(mViewModel)
            HintSpan("你可以点击backend地址,来快速复制")
            Spacer(modifier = Modifier.padding(10.dp))
            AllowLanSpan(mViewModel)
            if (mViewModel.allowLan) {
                AllowLanHint()
            }
            Spacer(modifier = Modifier.padding(10.dp))
            AllowCrashReport(mViewModel)
            HintSpan("仅在应用崩溃时发送,我们不会收集任何其他信息")
            Spacer(modifier = Modifier.padding(10.dp))
            OpenSubStore(mViewModel)
            Spacer(modifier = Modifier.padding(10.dp))
            FooterSpan()
        }
    }

    SubStoreUpdateDialog(
        show = showSubStoreUpdateDialog,
        onDismiss = { showSubStoreUpdateDialog = false }
    )
}

@Composable
fun FrontEndCard(mViewModel: MainViewModel) {
    val clipboardManager = LocalClipboardManager.current
    val urlHandler = LocalUriHandler.current

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 10.dp),
        horizontalArrangement = Arrangement.Start,
    ) {
        Text(
            stringResource(R.string.frontend),
            fontSize = 14.sp
        )
    }

    Spacer(modifier = Modifier.height(3.dp))

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.defaultColors(
            color = MiuixTheme.colorScheme.surface
        )
    ) {
        Column {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp)
                    .padding(horizontal = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    stringResource(R.string.address),
                )

                // 局域网模式对外暴露真实地址，本机模式固定使用回环地址。
                val host = if (mViewModel.allowLan) GlobalStatus.lanIP.value else "127.0.0.1"

                Text(
                    "http://${host}:8080",
                    modifier = Modifier.clickable {
                        clipboardManager.setText(AnnotatedString("http://${host}:8080"))
                    },
                )
            }

            HorizontalDivider(
                modifier = Modifier.padding(start = 10.dp),
                color = Color.LightGray,
                thickness = 0.5.dp
            )

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp)
                    .padding(horizontal = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    stringResource(R.string.version),
                )

                Text(
                    SubStore.localFrontendVersion,
                    modifier = Modifier.clickable {
                        urlHandler.openUri("https://github.com/sub-store-org/Sub-Store-Front-End/releases")
                    },
                    color = Blue
                )
            }

        }
    }
}

@Composable
fun BackEndCard(mViewModel: MainViewModel) {
    val urlHandler = LocalUriHandler.current

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 10.dp),
        horizontalArrangement = Arrangement.Start,
    ) {
        Text(
            stringResource(R.string.backend),
            color = MiuixTheme.colorScheme.onBackground,
            fontSize = 14.sp
        )
    }

    Spacer(modifier = Modifier.height(3.dp))

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.defaultColors(
            color = MiuixTheme.colorScheme.surface
        )
    ) {
        Column {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp)
                    .padding(horizontal = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    stringResource(R.string.address),
                )

                val clipboardManager = LocalClipboardManager.current

                val host: String = if (mViewModel.allowLan) {
                    GlobalStatus.lanIP.value
                } else {
                    "127.0.0.1"
                }

                Text(
                    "http://${host}:8081",
                    modifier = Modifier.clickable {
                        clipboardManager.setText(AnnotatedString("http://${host}:8081"))
                    },
                )
            }

            HorizontalDivider(
                modifier = Modifier.padding(start = 10.dp),
                color = Color.LightGray,
                thickness = 0.5.dp
            )

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp)
                    .padding(horizontal = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    stringResource(R.string.version),
                )

                Text(
                    SubStore.localBackendVersion,
                    modifier = Modifier.clickable {
                        urlHandler.openUri("https://github.com/sub-store-org/Sub-Store/releases")
                    },
                    color = Blue
                )
            }
        }
    }
}

@Composable
fun AllowLanSpan(mViewModel: MainViewModel) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.defaultColors(
            color = MiuixTheme.colorScheme.surface
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(50.dp)
                .padding(horizontal = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                stringResource(R.string.allow_lan),
            )

            Switch(
                checked = mViewModel.allowLan,
                onCheckedChange = {
                    mViewModel.allowLan = it
                    ConfigStore.isAllowLan = it
                },
                colors = switchColors(),
                modifier = Modifier.scale(0.9f)
            )
        }
    }
}

@Composable
fun AllowCrashReport(mViewModel: MainViewModel) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.defaultColors(
            color = MiuixTheme.colorScheme.surface
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(50.dp)
                .padding(horizontal = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                stringResource(R.string.allow_crash_report),
            )

            Switch(
                checked = mViewModel.allowCrashReport,
                onCheckedChange = {
                    mViewModel.allowCrashReport = it
                    ConfigStore.isAllowCrashReport = it
                },
                colors = switchColors(),
                modifier = Modifier.scale(0.9f)
            )
        }
    }
}

@Composable
fun HintSpan(hint: String) {
    Text(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 10.dp, top = 5.dp),
        text = hint,
        color = Color(0xFF909399),
        fontSize = 14.sp
    )
}

@Composable
fun AllowLanHint() {

    if (GlobalStatus.isWifi.value) {
        return
    }

    val text = "当前不是WIFI环境,无法获取准确的局域网IP!"

    Text(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 10.dp, top = 5.dp),
        text = text,
        color = Color(0xFFfaad14),
        fontSize = 14.sp
    )
}

@Composable
fun OpenSubStore(mViewModel: MainViewModel) {
    val subStoreUrl = buildSubStoreUrl(
        allowLan = mViewModel.allowLan,
        lanIp = GlobalStatus.lanIP.value,
    )

    val urlHandler = LocalUriHandler.current

    TextButton(
        text = stringResource(R.string.open_sub_store_in_external_browser),
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 10.dp, top = 5.dp),
        onClick = {
            urlHandler.openUri(subStoreUrl)
        },
        colors = ButtonDefaults.textButtonColorsPrimary()
    )
}

@Composable
fun SettingsTopBar(
    navController: NavController,
    onUpdateAvailable: () -> Unit
) {
    val haptic = LocalHapticFeedback.current
    var isCheckingUpdate by rememberSaveable { mutableStateOf(false) }

    // SmallTopAppBar 内建系统栏 Insets，为边到边布局提供安全间距。
    SmallTopAppBar(
        title = stringResource(id = R.string.settings),
        color = Color.Transparent,
        navigationIcon = {
            IconButton(onClick = { navController.popBackStack() }) {
                Icon(
                    Icons.AutoMirrored.Outlined.ArrowBack,
                    contentDescription = stringResource(R.string.navigate_back)
                )
            }
        },
        actions = {
            IconButton(
                enabled = !isCheckingUpdate,
                onClick = {
                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    isCheckingUpdate = true
                    SubStore.checkLatestVersion(
                        showToast = true,
                        onUpdateAvailable = onUpdateAvailable,
                        onFinished = { isCheckingUpdate = false }
                    )
                },
            ) {
                if (isCheckingUpdate) {
                    CircularProgressIndicator(
                        size = 20.dp,
                        strokeWidth = 2.dp
                    )
                } else {
                    Icon(
                        Icons.Outlined.Refresh,
                        contentDescription = stringResource(R.string.check_sub_store_updates),
                        modifier = Modifier.size(24.dp)
                    )
                }
            }
        },
    )
}

@Preview
@Composable
fun FooterSpan() {
    val uriHandler = LocalUriHandler.current

    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        val webViewPackage = WebView.getCurrentWebViewPackage()
        val webViewVersion = webViewPackage?.versionName ?: "unavailable"
        val webViewChannel = webViewChannel(webViewPackage?.packageName)
        Text(
            "WebView($webViewChannel.$webViewVersion)",
            color = Color.Gray,
            fontWeight = FontWeight.Medium,
            fontSize = 14.sp,
        )

        Spacer(modifier = Modifier.height(4.dp))

        Text(
            "v" + BuildConfig.VERSION_NAME + " (" + BuildConfig.VERSION_CODE + ")",
            color = Color.Gray,
            fontWeight = FontWeight.Medium,
            fontSize = 14.sp,
        )

        IconButton(
            onClick = {
                uriHandler.openUri("https://github.com/angus-cx/SubCase")
            }
        ) {
            Icon(
                painter = painterResource(id = R.drawable.github),
                contentDescription = "github",
                tint = Color.Gray,
            )
        }
    }
}

/** 根据当前 WebView 提供方包名判断渠道。 */
internal fun webViewChannel(packageName: String?): String {
    if (packageName == null) return "unavailable"
    return when (packageName.substringAfterLast('.')) {
        "beta" -> "beta"
        "dev" -> "dev"
        "canary" -> "canary"
        else -> "stable"
    }
}
