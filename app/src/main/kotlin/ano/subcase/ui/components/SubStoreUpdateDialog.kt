package ano.subcase.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import ano.subcase.util.SubStore
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.window.WindowDialog

@OptIn(DelicateCoroutinesApi::class)
@Composable
fun SubStoreUpdateDialog(show: Boolean, onDismiss: () -> Unit) {
    val isUpdating = remember { mutableStateOf(false) }

    WindowDialog(
        show = show,
        title = "检测到 SubStore 有新版本",
        // 更新过程由弹窗内按钮结束，返回键和遮罩点击保持当前状态展示。
        onDismissRequest = null,
    ) {
        Column {
            if (SubStore.remoteFrontendVersion != SubStore.localFrontendVersion) {
                Row(
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 5.dp)
                ) {
                    Text(
                        text = "前端",
                        color = MiuixTheme.colorScheme.onSurface,
                    )
                    Text(
                        text = "(${SubStore.localFrontendVersion} -> ${SubStore.remoteFrontendVersion})",
                        color = MiuixTheme.colorScheme.onSurface,
                    )
                }
            }

            if (SubStore.remoteBackendVersion != SubStore.localBackendVersion) {
                Row(
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 10.dp)
                ) {
                    Text(
                        text = "后端",
                        color = MiuixTheme.colorScheme.onSurface,
                    )
                    Text(
                        text = "(${SubStore.localBackendVersion} -> ${SubStore.remoteBackendVersion})",
                        color = MiuixTheme.colorScheme.onSurface,
                    )
                }
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            TextButton(
                text = "取消",
                enabled = !isUpdating.value,
                onClick = onDismiss,
                modifier = Modifier.weight(1f)
            )
            Spacer(modifier = Modifier.width(12.dp))
            TextButton(
                text = "更新",
                enabled = !isUpdating.value,
                onClick = {
                    // 锁定操作按钮，确保每次只运行一组前后端更新任务。
                    isUpdating.value = true
                    GlobalScope.launch {
                        var updateSucceeded = true
                        if (SubStore.remoteFrontendVersion != SubStore.localFrontendVersion) {
                            updateSucceeded = SubStore.updateFrontend().isSuccess
                        }
                        if (SubStore.remoteBackendVersion != SubStore.localBackendVersion) {
                            updateSucceeded =
                                SubStore.updateBackend().isSuccess && updateSucceeded
                        }
                        withContext(Dispatchers.Main) {
                            isUpdating.value = false
                            if (updateSucceeded) {
                                onDismiss()
                            }
                        }
                    }
                },
                modifier = Modifier.weight(1f),
                colors = ButtonDefaults.textButtonColorsPrimary()
            )
        }
    }
}
