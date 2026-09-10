package ano.subcase.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
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
import ano.subcase.ui.components.Section
import ano.subcase.ui.components.SectionDefaults
import ano.subcase.ui.components.buildSubStoreUrl
import ano.subcase.ui.theme.Blue
import ano.subcase.ui.theme.switchColors
import ano.subcase.util.CrashReporter
import ano.subcase.util.ConfigStore
import ano.subcase.util.SubStore
import ano.subcase.util.SubStoreUpdatePolicy
import ano.subcase.util.SubStoreUpdateScheduler
import ano.subcase.util.formatSubStoreInstalledAt
import kotlinx.coroutines.launch
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.CircularProgressIndicator
import top.yukonga.miuix.kmp.basic.DropdownArrowEndAction
import top.yukonga.miuix.kmp.basic.DropdownDefaults
import top.yukonga.miuix.kmp.basic.DropdownEntry
import top.yukonga.miuix.kmp.basic.DropdownItem
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.SmallTopAppBar
import top.yukonga.miuix.kmp.basic.Switch
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.popup.OverlayDropdownPopup
import top.yukonga.miuix.kmp.theme.MiuixTheme

@Composable
fun SettingsScreen(navController: NavController) {
    val mViewModel = viewModel<MainViewModel>()

    Scaffold(
        containerColor = MiuixTheme.colorScheme.background,
        topBar = {
            SettingsTopBar(navController = navController)
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
            Spacer(modifier = Modifier.padding(10.dp))
            AllowLanSpan(mViewModel)
            Spacer(modifier = Modifier.padding(10.dp))
            SubStoreUpdatePolicySection()
            Spacer(modifier = Modifier.padding(10.dp))
            AllowCrashReport(mViewModel)
            if (BuildConfig.DEBUG) {
                Spacer(modifier = Modifier.padding(10.dp))
                Section(modifier = Modifier.clickable { navController.navigate("debug_screen") }) {
                    item {
                        Text("调试")
                        Icon(Icons.AutoMirrored.Outlined.KeyboardArrowRight, contentDescription = null)
                    }
                }
            }
            Spacer(modifier = Modifier.padding(10.dp))
            OpenSubStore(mViewModel)
            Spacer(modifier = Modifier.padding(10.dp))
            FooterSpan()
        }
    }
}

@Composable
fun FrontEndCard(mViewModel: MainViewModel) {
    val clipboardManager = LocalClipboardManager.current
    val urlHandler = LocalUriHandler.current

    Section(
        header = stringResource(R.string.frontend),
        footer = stringResource(
            R.string.substore_last_updated,
            formatSubStoreInstalledAt(SubStore.lastFrontendInstalledAt),
        ),
    ) {
        item {
            Text(stringResource(R.string.address))
            // 局域网模式对外暴露真实地址，本机模式固定使用回环地址。
            val host = if (mViewModel.allowLan) GlobalStatus.lanIP.value else "127.0.0.1"
            val address = "http://${host}:8080"
            Text(
                address,
                modifier = Modifier.clickable {
                    clipboardManager.setText(AnnotatedString(address))
                },
            )
        }
        item {
            Text(stringResource(R.string.version))
            Text(
                SubStore.localFrontendVersion,
                modifier = Modifier.clickable {
                    urlHandler.openUri("https://github.com/sub-store-org/Sub-Store-Front-End/releases")
                },
                color = Blue,
            )
        }
    }
}

@Composable
fun BackEndCard(mViewModel: MainViewModel) {
    val clipboardManager = LocalClipboardManager.current
    val urlHandler = LocalUriHandler.current

    Section(
        header = stringResource(R.string.backend),
        footer = stringResource(
            R.string.substore_last_updated,
            formatSubStoreInstalledAt(SubStore.lastBackendInstalledAt),
        ),
    ) {
        item {
            Text(stringResource(R.string.address))
            val host = if (mViewModel.allowLan) GlobalStatus.lanIP.value else "127.0.0.1"
            val address = "http://${host}:8081"
            Text(
                address,
                modifier = Modifier.clickable {
                    clipboardManager.setText(AnnotatedString(address))
                },
            )
        }
        item {
            Text(stringResource(R.string.version))
            Text(
                SubStore.localBackendVersion,
                modifier = Modifier.clickable {
                    urlHandler.openUri("https://github.com/sub-store-org/Sub-Store/releases")
                },
                color = Blue,
            )
        }
    }
}

@Composable
fun AllowLanSpan(mViewModel: MainViewModel) {
    Section(
        footer = if (mViewModel.allowLan && !GlobalStatus.isWifi.value) {
            "当前不是WIFI环境,无法获取准确的局域网IP!"
        } else {
            null
        },
        footerColor = SectionDefaults.WarningColor,
    ) {
        item {
            Text(stringResource(R.string.allow_lan))
            Switch(
                checked = mViewModel.allowLan,
                onCheckedChange = {
                    mViewModel.allowLan = it
                    ConfigStore.isAllowLan = it
                },
                colors = switchColors(),
                modifier = Modifier.scale(0.9f),
            )
        }
    }
}

@Composable
private fun SubStoreUpdatePolicySection() {
    val haptic = LocalHapticFeedback.current
    val scope = rememberCoroutineScope()
    var policy by remember { mutableStateOf(ConfigStore.subStoreUpdatePolicy) }
    var showPolicyMenu by rememberSaveable { mutableStateOf(false) }
    var isCheckingUpdate by rememberSaveable { mutableStateOf(false) }
    val actionColor = MiuixTheme.colorScheme.onSurfaceVariantActions
    val policyLabels = SubStoreUpdatePolicy.entries.map { stringResource(it.labelRes) }
    val policyEntry = remember(policy, policyLabels) {
        DropdownEntry(
            items = SubStoreUpdatePolicy.entries.mapIndexed { index, option ->
                DropdownItem(
                    text = policyLabels[index],
                    selected = option == policy,
                    onClick = {
                        policy = option
                        ConfigStore.subStoreUpdatePolicy = option
                        SubStoreUpdateScheduler.notifyPolicyChanged()
                    },
                )
            },
        )
    }

    Section(
        header = stringResource(R.string.substore_update_policy_section),
        footer = stringResource(R.string.substore_update_policy_interval_hint),
    ) {
        item(
            onClick = {
                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                showPolicyMenu = true
            },
        ) {
            Text(stringResource(R.string.substore_auto_update_policy))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = stringResource(policy.labelRes),
                    color = actionColor,
                    modifier = Modifier.padding(end = 8.dp),
                )
                DropdownArrowEndAction(actionColor = actionColor)
                // 行布局保持原样，只把选项列表做成锚定 overlay 下拉。
                OverlayDropdownPopup(
                    entry = policyEntry,
                    show = showPolicyMenu,
                    onDismiss = { showPolicyMenu = false },
                    onDismissFinished = {},
                    maxHeight = null,
                    dropdownColors = DropdownDefaults.dropdownColors(),
                    renderInRootScaffold = true,
                )
            }
        }
        item(
            enabled = !isCheckingUpdate,
            onClick = {
                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                isCheckingUpdate = true
                scope.launch {
                    try {
                        SubStore.checkAndUpdate(showToast = true)
                    } finally {
                        isCheckingUpdate = false
                    }
                }
            },
        ) {
            Text(stringResource(R.string.check_sub_store_updates))
            if (isCheckingUpdate) {
                CircularProgressIndicator(
                    size = 20.dp,
                    strokeWidth = 2.dp,
                )
            }
        }
    }
}

@Composable
fun AllowCrashReport(mViewModel: MainViewModel) {
    Section(footer = "仅在应用崩溃时发送,我们不会收集任何其他信息") {
        item {
            Text(stringResource(R.string.allow_crash_report))
            Switch(
                checked = mViewModel.allowCrashReport,
                onCheckedChange = {
                    CrashReporter.setReportingEnabled(it)
                    mViewModel.allowCrashReport = it
                },
                colors = switchColors(),
                modifier = Modifier.scale(0.9f),
            )
        }
    }
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
fun SettingsTopBar(navController: NavController) {
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
    )
}

@Preview
@Composable
fun FooterSpan() {
    val uriHandler = LocalUriHandler.current
    val iconButtonSize = 36.dp
    val iconSize = 18.dp
    val iconSpacing = -(iconButtonSize - iconSize) / 2

    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            "v" + BuildConfig.VERSION_NAME + " (" + BuildConfig.VERSION_CODE + ")",
            color = Color.Gray,
            fontWeight = FontWeight.Medium,
            fontSize = 14.sp,
        )

        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(iconSpacing, Alignment.CenterHorizontally)
        ) {
            IconButton(
                modifier = Modifier.size(iconButtonSize),
                onClick = {
                    uriHandler.openUri("https://github.com/angus-cx/SubCase")
                }
            ) {
                Icon(
                    painter = painterResource(id = R.drawable.ic_github),
                    contentDescription = "github",
                    tint = Color.Gray,
                    modifier = Modifier.size(iconSize),
                )
            }

            IconButton(
                modifier = Modifier.size(iconButtonSize),
                onClick = {
                    uriHandler.openUri("https://t.me/sion_channel")
                }
            ) {
                Icon(
                    painter = painterResource(id = R.drawable.ic_telegram),
                    contentDescription = "telegram",
                    tint = Color.Gray,
                    modifier = Modifier.size(iconSize),
                )
            }
        }
    }
}
