package ano.subcase.ui

import android.app.Application
import android.widget.Toast
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import ano.subcase.BuildConfig
import ano.subcase.model.AppRelease
import ano.subcase.util.AppUpdater
import ano.subcase.util.SubStore
import kotlinx.coroutines.async
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.File

enum class StartupDialog {
    NONE,
    APP_UPDATE,
    SUB_STORE_UPDATE,
}

enum class AppUpdateDecision {
    PENDING,
    SKIPPED,
    UPDATING,
    INSTALLER_LAUNCHED,
    UNAVAILABLE,
}

sealed interface AppDownloadState {
    data object Idle : AppDownloadState
    data class Downloading(val progress: Float?) : AppDownloadState
    data class Failed(val message: String) : AppDownloadState
    data class PermissionRequired(val file: File) : AppDownloadState
}

data class StartupUpdateUiState(
    val appCheckFinished: Boolean = false,
    val appRelease: AppRelease? = null,
    val appDecision: AppUpdateDecision = AppUpdateDecision.PENDING,
    val subStoreCheckFinished: Boolean = false,
    val hasSubStoreUpdate: Boolean = false,
    val activeDialog: StartupDialog = StartupDialog.NONE,
    val downloadState: AppDownloadState = AppDownloadState.Idle,
)

internal fun resolveStartupDialog(state: StartupUpdateUiState): StartupDialog {
    if (!state.appCheckFinished) return StartupDialog.NONE
    if (state.appRelease != null && state.appDecision == AppUpdateDecision.PENDING) {
        return StartupDialog.APP_UPDATE
    }
    if (state.appDecision == AppUpdateDecision.UPDATING) return StartupDialog.APP_UPDATE
    if (state.appDecision == AppUpdateDecision.INSTALLER_LAUNCHED) return StartupDialog.NONE
    if (state.subStoreCheckFinished && state.hasSubStoreUpdate) {
        return StartupDialog.SUB_STORE_UPDATE
    }
    return StartupDialog.NONE
}

class StartupUpdateViewModel(application: Application) : AndroidViewModel(application) {
    private val _uiState = MutableStateFlow(StartupUpdateUiState())
    val uiState = _uiState.asStateFlow()

    private val installRequestChannel = Channel<File>(Channel.BUFFERED)
    val installRequests = installRequestChannel.receiveAsFlow()

    init {
        checkUpdates()
    }

    private fun checkUpdates() {
        viewModelScope.launch {
            val appCheck = async { AppUpdater.checkLatest() }
            val subStoreCheck = async { SubStore.checkLatestVersionAwait() }

            val appResult = appCheck.await()
            appResult.onFailure { error ->
                Toast.makeText(
                    getApplication(),
                    "检测 App 更新失败：${error.message ?: "未知错误"}",
                    Toast.LENGTH_LONG,
                ).show()
            }
            val release = appResult.getOrNull()?.takeIf {
                isAppUpdateAvailable(it.versionCode, BuildConfig.VERSION_CODE)
            }
            updateState {
                it.copy(
                    appCheckFinished = true,
                    appRelease = release,
                    appDecision = if (release == null) {
                        AppUpdateDecision.UNAVAILABLE
                    } else {
                        AppUpdateDecision.PENDING
                    },
                )
            }

            val hasSubStoreUpdate = subStoreCheck.await()
            updateState {
                it.copy(
                    subStoreCheckFinished = true,
                    hasSubStoreUpdate = hasSubStoreUpdate,
                )
            }
        }
    }

    fun skipAppUpdate() {
        updateState { it.copy(appDecision = AppUpdateDecision.SKIPPED) }
    }

    fun startAppUpdate() {
        val release = _uiState.value.appRelease ?: return
        updateState {
            it.copy(
                appDecision = AppUpdateDecision.UPDATING,
                activeDialog = StartupDialog.APP_UPDATE,
                downloadState = AppDownloadState.Downloading(null),
            )
        }
        viewModelScope.launch {
            val result = AppUpdater.download(getApplication(), release) { progress ->
                _uiState.update { state ->
                    state.copy(downloadState = AppDownloadState.Downloading(progress))
                }
            }
            result.onSuccess { installRequestChannel.send(it) }
                .onFailure { error ->
                    _uiState.update { state ->
                        state.copy(
                            activeDialog = StartupDialog.APP_UPDATE,
                            downloadState = AppDownloadState.Failed(
                                error.message ?: "安装包下载失败"
                            ),
                        )
                    }
                }
        }
    }

    fun requireInstallPermission(file: File) {
        _uiState.update {
            it.copy(
                activeDialog = StartupDialog.APP_UPDATE,
                downloadState = AppDownloadState.PermissionRequired(file),
            )
        }
    }

    fun retryInstall() {
        val file = (_uiState.value.downloadState as? AppDownloadState.PermissionRequired)?.file
            ?: return
        installRequestChannel.trySend(file)
    }

    fun installerLaunched() {
        _uiState.update {
            it.copy(
                appDecision = AppUpdateDecision.INSTALLER_LAUNCHED,
                activeDialog = StartupDialog.NONE,
                downloadState = AppDownloadState.Idle,
            )
        }
    }

    fun installFailed(message: String) {
        _uiState.update {
            it.copy(
                activeDialog = StartupDialog.APP_UPDATE,
                downloadState = AppDownloadState.Failed(message),
            )
        }
    }

    fun dismissSubStoreUpdate() {
        _uiState.update { it.copy(activeDialog = StartupDialog.NONE) }
    }

    private fun updateState(transform: (StartupUpdateUiState) -> StartupUpdateUiState) {
        _uiState.update { state ->
            transform(state).let { it.copy(activeDialog = resolveStartupDialog(it)) }
        }
    }
}

internal fun isAppUpdateAvailable(remoteVersionCode: Int, currentVersionCode: Int): Boolean =
    remoteVersionCode > currentVersionCode
