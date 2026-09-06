package ano.subcase.ui.screens

import android.widget.Toast
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import ano.subcase.debug.DebugPort
import ano.subcase.ui.DebugViewModel
import ano.subcase.ui.components.Section
import ano.subcase.ui.theme.switchColors
import ano.subcase.util.currentServerConfig
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.SmallTopAppBar
import top.yukonga.miuix.kmp.basic.Switch
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme

@Composable
fun DebugScreen(navController: NavController, model: DebugViewModel) {
    val states by model.ports.collectAsState()
    val config = currentServerConfig()
    val frontend = states.getValue(DebugPort.FRONTEND).occupied ?: config.frontend
    val backend = states.getValue(DebugPort.BACKEND).occupied ?: config.backend
    val context = LocalContext.current
    LaunchedEffect(model) {
        model.messages.collect { Toast.makeText(context, it, Toast.LENGTH_LONG).show() }
    }
    Scaffold(
        containerColor = MiuixTheme.colorScheme.background,
        topBar = {
            SmallTopAppBar(
                title = "调试",
                color = Color.Transparent,
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "返回上一页")
                    }
                },
            )
        },
    ) { padding ->
        Column(
            Modifier.padding(padding).fillMaxWidth()
                .verticalScroll(rememberScrollState()).padding(20.dp),
        ) {
            Section(
                header = "测试端口占用",
                footer = "前端：$frontend\n后端：$backend\n开启后返回首页启动服务，可测试端口冲突。关闭开关或结束应用界面会释放占用。",
            ) {
                DebugPort.entries.forEach { port ->
                    item {
                        val state = states.getValue(port)
                        Text(if (port == DebugPort.FRONTEND) "临时占用前端端口" else "临时占用后端端口")
                        Switch(
                            checked = state.occupied != null,
                            enabled = !state.busy,
                            onCheckedChange = { model.setOccupied(port, it) },
                            colors = switchColors(),
                            modifier = Modifier.scale(0.9f),
                        )
                    }
                }
            }
        }
    }
}
