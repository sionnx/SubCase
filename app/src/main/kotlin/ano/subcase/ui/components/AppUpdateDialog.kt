package ano.subcase.ui.components

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import androidx.core.net.toUri
import ano.subcase.BuildConfig
import ano.subcase.R
import ano.subcase.ui.AppDownloadState
import ano.subcase.ui.StartupDialog
import ano.subcase.ui.StartupUpdateViewModel
import kotlinx.coroutines.flow.collectLatest
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.LinearProgressIndicator
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.window.WindowDialog
import android.content.Intent
import android.provider.Settings
import androidx.compose.runtime.LaunchedEffect
import java.io.File

@Composable
fun StartupUpdateCoordinator(viewModel: StartupUpdateViewModel) {
    val state by viewModel.uiState.collectAsState()
    val currentState by rememberUpdatedState(state)
    val context = LocalContext.current
    val installFailedMessage = stringResource(R.string.app_update_install_failed)

    fun launchInstaller(file: File) {
        runCatching {
            val uri = FileProvider.getUriForFile(
                context,
                "${BuildConfig.APPLICATION_ID}.fileprovider",
                file,
            )
            context.startActivity(
                Intent(Intent.ACTION_VIEW).apply {
                    setDataAndType(uri, "application/vnd.android.package-archive")
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
            )
        }.onSuccess {
            viewModel.installerLaunched()
        }.onFailure {
            viewModel.installFailed(it.message ?: installFailedMessage)
        }
    }

    val unknownSourcesLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) {
        val permissionState = currentState.downloadState as? AppDownloadState.PermissionRequired
            ?: return@rememberLauncherForActivityResult
        if (context.packageManager.canRequestPackageInstalls()) {
            launchInstaller(permissionState.file)
        }
    }

    LaunchedEffect(viewModel) {
        viewModel.installRequests.collectLatest { file ->
            if (!context.packageManager.canRequestPackageInstalls()) {
                viewModel.requireInstallPermission(file)
                unknownSourcesLauncher.launch(
                    Intent(
                        Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                        "package:${context.packageName}".toUri(),
                    )
                )
            } else {
                launchInstaller(file)
            }
        }
    }

    AppUpdateDialog(
        show = state.activeDialog == StartupDialog.APP_UPDATE,
        release = state.appRelease,
        downloadState = state.downloadState,
        onSkip = viewModel::skipAppUpdate,
        onUpdate = viewModel::startAppUpdate,
        onInstallPermission = viewModel::retryInstall,
    )

    SubStoreUpdateDialog(
        show = state.activeDialog == StartupDialog.SUB_STORE_UPDATE,
        onDismiss = viewModel::dismissSubStoreUpdate,
    )
}

@Composable
private fun AppUpdateDialog(
    show: Boolean,
    release: ano.subcase.model.AppRelease?,
    downloadState: AppDownloadState,
    onSkip: () -> Unit,
    onUpdate: () -> Unit,
    onInstallPermission: () -> Unit,
) {
    val currentRelease = release ?: return
    val isDownloading = downloadState is AppDownloadState.Downloading
    val actionText = when (downloadState) {
        AppDownloadState.Idle -> stringResource(R.string.app_update_update)
        is AppDownloadState.Downloading -> {
            downloadState.progress?.let {
                stringResource(R.string.app_update_downloading_progress, (it * 100).toInt())
            } ?: stringResource(R.string.app_update_downloading)
        }
        is AppDownloadState.Failed -> stringResource(R.string.app_update_retry)
        is AppDownloadState.PermissionRequired -> stringResource(R.string.app_update_open_settings)
    }

    WindowDialog(
        show = show,
        title = stringResource(R.string.app_update_title, currentRelease.versionName),
        onDismissRequest = null,
    ) {
        Column {
            Text(
                text = stringResource(
                    R.string.app_update_version,
                    BuildConfig.VERSION_NAME,
                    currentRelease.versionName,
                ),
                color = MiuixTheme.colorScheme.onSurface,
            )
            Spacer(Modifier.height(12.dp))
            Text(
                text = currentRelease.releaseNotes,
                color = MiuixTheme.colorScheme.onSurface,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 320.dp)
                    .verticalScroll(rememberScrollState()),
            )

            when (downloadState) {
                is AppDownloadState.Downloading -> {
                    Spacer(Modifier.height(16.dp))
                    LinearProgressIndicator(progress = downloadState.progress)
                }
                is AppDownloadState.Failed -> {
                    Spacer(Modifier.height(12.dp))
                    Text(downloadState.message, color = Color(0xFFD32F2F))
                }
                is AppDownloadState.PermissionRequired -> {
                    Spacer(Modifier.height(12.dp))
                    Text(
                        stringResource(R.string.app_update_permission_required),
                        color = Color(0xFFD97706),
                    )
                }
                AppDownloadState.Idle -> Unit
            }

            Spacer(Modifier.height(16.dp))
            Row(modifier = Modifier.fillMaxWidth()) {
                TextButton(
                    text = stringResource(R.string.app_update_skip),
                    enabled = !isDownloading,
                    onClick = onSkip,
                    modifier = Modifier.weight(1f),
                )
                Spacer(Modifier.width(12.dp))
                TextButton(
                    text = actionText,
                    enabled = !isDownloading,
                    onClick = {
                        when (downloadState) {
                            is AppDownloadState.PermissionRequired -> onInstallPermission()
                            else -> onUpdate()
                        }
                    },
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.textButtonColorsPrimary(),
                )
            }
        }
    }
}
