package ano.subcase.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.collectAsState
import ano.subcase.service.SubStoreServiceController
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import ano.subcase.GlobalStatus
import ano.subcase.R
import ano.subcase.ui.HomeViewModel
import ano.subcase.ui.MainViewModel
import ano.subcase.ui.components.HomePanel
import ano.subcase.ui.components.SubStoreWebView
import ano.subcase.ui.components.buildSubStoreUrl
import ano.subcase.util.ConfigStore
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme
import kotlin.math.roundToInt

@Composable
fun HomeScreen(navController: NavController) {
    val mViewModel = viewModel<MainViewModel>()
    val homeViewModel = viewModel<HomeViewModel>()
    val serviceState by SubStoreServiceController.state.collectAsState()
    val isServiceRunning = serviceState.isRunning

    val density = LocalDensity.current
    val edgePx = with(density) { 16.dp.toPx() }
    val initialBottomPx = with(density) { 84.dp.toPx() }
    var containerHeight by remember { mutableIntStateOf(0) }
    var panelHeight by remember { mutableIntStateOf(0) }
    val maxTop = (containerHeight - panelHeight - edgePx).coerceAtLeast(0f)
    val minTop = edgePx.coerceAtMost(maxTop)
    val travel = maxTop - minTop
    val restingTop = homeViewModel.panelVerticalFraction?.let { minTop + travel * it }
        ?: (containerHeight - panelHeight - initialBottomPx).coerceIn(minTop, maxTop)
    var dragTop by remember(containerHeight, panelHeight, density) { mutableStateOf<Float?>(null) }
    LifecycleEventEffect(Lifecycle.Event.ON_STOP) {
        dragTop = null
    }

    Scaffold(
        containerColor = MiuixTheme.colorScheme.background,
    ) { contentPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(contentPadding)
                .onSizeChanged { containerHeight = it.height }
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
                serviceEnabled = !serviceState.isBusy,
                onSettingsClick = { navController.navigate("settings_screen") },
                onToggleService = {
                    if (isServiceRunning) {
                        mViewModel.stopService()
                    } else {
                        mViewModel.startService()
                    }
                },
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(end = 16.dp)
                    .offset { IntOffset(0, (dragTop ?: restingTop).roundToInt()) }
                    .onSizeChanged { panelHeight = it.height }
                    .zIndex(1f)
                    .pointerInput(containerHeight, panelHeight, density) {
                        try {
                            detectVerticalDragGestures(
                                onDragStart = {
                                    dragTop = homeViewModel.panelVerticalFraction
                                        ?.let { minTop + travel * it }
                                        ?: (containerHeight - panelHeight - initialBottomPx)
                                            .coerceIn(minTop, maxTop)
                                },
                                onVerticalDrag = { change, amount ->
                                    change.consume()
                                    dragTop = dragTop?.let { (it + amount).coerceIn(minTop, maxTop) }
                                },
                                onDragEnd = {
                                    dragTop?.let { top ->
                                        if (travel > 0f) {
                                            homeViewModel.savePanelPosition((top - minTop) / travel)
                                        }
                                    }
                                    dragTop = null
                                },
                                onDragCancel = { dragTop = null },
                            )
                        } finally {
                            dragTop = null
                        }
                    },
            )
        }
    }
}
