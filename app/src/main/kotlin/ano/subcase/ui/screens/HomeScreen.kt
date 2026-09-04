package ano.subcase.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import ano.subcase.GlobalStatus
import ano.subcase.R
import ano.subcase.ui.HomeViewModel
import ano.subcase.ui.MainViewModel
import ano.subcase.ui.components.HomePanel
import ano.subcase.ui.components.SubStoreUpdateDialog
import ano.subcase.ui.components.SubStoreWebView
import ano.subcase.ui.components.buildSubStoreUrl
import ano.subcase.util.ConfigStore
import ano.subcase.util.SubStore
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme

@Composable
fun HomeScreen(navController: NavController) {
    val mViewModel = viewModel<MainViewModel>()
    val homeViewModel = viewModel<HomeViewModel>()
    var showSubStoreUpdateDialog by rememberSaveable { mutableStateOf(false) }
    val isServiceRunning = GlobalStatus.isServiceRunning.value

    LaunchedEffect(Unit) {
        SubStore.checkLatestVersionOnce {
            showSubStoreUpdateDialog = true
        }
    }

    Scaffold(
        containerColor = MiuixTheme.colorScheme.background,
    ) { contentPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(contentPadding)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(MiuixTheme.colorScheme.background)
            ) {
                if (isServiceRunning) {
                    SubStoreWebView(
                        url = buildSubStoreUrl(
                            allowLan = ConfigStore.isAllowLan,
                            lanIp = GlobalStatus.lanIP.value,
                        ),
                        homeViewModel = homeViewModel,
                        modifier = Modifier.fillMaxSize(),
                    )
                } else {
                    Text(
                        text = stringResource(R.string.sub_store_not_started),
                        color = MiuixTheme.colorScheme.onBackground,
                        modifier = Modifier.align(Alignment.Center),
                    )
                }
            }
            HomePanel(
                isServiceRunning = isServiceRunning,
                onSettingsClick = { navController.navigate("settings_screen") },
                onToggleService = {
                    ConfigStore.isServiceRunning = !isServiceRunning
                    if (isServiceRunning) {
                        mViewModel.stopService()
                    } else {
                        mViewModel.startService()
                    }
                },
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(end = 16.dp, bottom = 84.dp)
                    .zIndex(1f),
            )
        }
    }

    SubStoreUpdateDialog(
        show = showSubStoreUpdateDialog,
        onDismiss = { showSubStoreUpdateDialog = false }
    )
}
