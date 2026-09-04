package ano.subcase.util

import android.widget.Toast
import androidx.compose.runtime.mutableStateOf
import ano.subcase.caseApp
import ano.subcase.util.AppUtil.unzip
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.io.File
import java.nio.file.Files
import java.nio.file.Paths

object SubStore {

    val basePath = caseApp.filesDir

    private val localFrontendVersionState = mutableStateOf(ConfigStore.localFrontendVersion)
    var localFrontendVersion: String
        get() = localFrontendVersionState.value
        set(value) {
            ConfigStore.localFrontendVersion = value
            localFrontendVersionState.value = value
        }

    private val localBackendVersionState = mutableStateOf(ConfigStore.localBackendVersion)
    var localBackendVersion: String
        get() = localBackendVersionState.value
        set(value) {
            ConfigStore.localBackendVersion = value
            localBackendVersionState.value = value
        }

    var remoteFrontendVersion = ConfigStore.localFrontendVersion
    var remoteBackendVersion = ConfigStore.localBackendVersion

    private var hasCheckedLatestVersion = false

    @Synchronized
    fun checkLatestVersionOnce(onUpdateAvailable: () -> Unit) {
        if (hasCheckedLatestVersion) {
            return
        }

        hasCheckedLatestVersion = true
        checkLatestVersion(onUpdateAvailable = onUpdateAvailable)
    }

    @OptIn(DelicateCoroutinesApi::class)
    fun checkLatestVersion(
        showToast: Boolean = false,
        onUpdateAvailable: () -> Unit,
        onFinished: () -> Unit = {}
    ) {
        GlobalScope.launch(Dispatchers.IO) {
            try {
                val backendResult = GithubUtil.getLatestVersion(REPO_BACKEND)
                if (backendResult.isSuccess) {
                    remoteBackendVersion = backendResult.getOrNull()!!
                } else {
                    Timber.e(backendResult.exceptionOrNull())
                    withContext(Dispatchers.Main) {
                        Toast.makeText(
                            caseApp,
                            "检测后端新版本失败,请检查您的网络环境",
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                }

                val frontendResult = GithubUtil.getLatestVersion(REPO_FRONTEND)
                if (frontendResult.isSuccess) {
                    remoteFrontendVersion = frontendResult.getOrNull()!!
                } else {
                    Timber.e(frontendResult.exceptionOrNull())
                    withContext(Dispatchers.Main) {
                        Toast.makeText(
                            caseApp,
                            "检测前端新版本失败,请检查您的网络环境",
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                }

                if ((remoteFrontendVersion.isNotEmpty() && remoteFrontendVersion != localFrontendVersion) || (remoteBackendVersion.isNotEmpty() && remoteBackendVersion != localBackendVersion)) {
                    withContext(Dispatchers.Main) {
                        onUpdateAvailable()
                    }
                } else if (frontendResult.isSuccess && backendResult.isSuccess && showToast) {
                    withContext(Dispatchers.Main) {
                        Toast.makeText(
                            caseApp,
                            "当前已是最新版本",
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                }
            } finally {
                withContext(Dispatchers.Main) {
                    onFinished()
                }
            }
        }
    }

    @OptIn(DelicateCoroutinesApi::class)
    suspend fun updateFrontend(): Result<Unit> {
        withContext(Dispatchers.Main) {
            Timber.d("Updating frontend to ${remoteFrontendVersion}")
            Toast.makeText(
                caseApp,
                "正在将前端更新到 v${remoteFrontendVersion}",
                Toast.LENGTH_SHORT
            ).show()
        }

        // Start download frontend
        val result = GithubUtil.downloadFile(
            REPO_FRONTEND,
            remoteFrontendVersion,
            "dist.zip",
            caseApp.filesDir.absolutePath
        )

        if (result.isSuccess) {
            val zipPath = caseApp.filesDir.path + "/dist.zip"
            unzip(File(zipPath), File(caseApp.filesDir.path))

            if (Files.exists(Paths.get(caseApp.filesDir.path + "/frontend"))) {
                File(caseApp.filesDir.path + "/frontend").deleteRecursively()

                Files.move(
                    Paths.get(caseApp.filesDir.path + "/dist"),
                    Paths.get(caseApp.filesDir.path + "/frontend")
                )

                localFrontendVersion = remoteFrontendVersion

                val msg = "Frontend updated to $remoteFrontendVersion"
                Timber.d(msg)
                withContext(Dispatchers.Main) {
                    Toast.makeText(
                        caseApp,
                        "前端已更新到 v${remoteFrontendVersion}",
                        Toast.LENGTH_SHORT
                    )
                        .show()
                }
            }
            return Result.success(Unit)
        } else {
            Timber.w("前端文件下载失败,请检查网络环境")
            withContext(Dispatchers.Main) {
                Toast.makeText(caseApp, "前端文件下载失败,请检查网络环境", Toast.LENGTH_SHORT)
                    .show()
            }
            return Result.failure(Exception("Failed to download frontend"))
        }
    }

    @OptIn(DelicateCoroutinesApi::class)
    suspend fun updateBackend(): Result<Unit> {
        withContext(Dispatchers.Main) {
            Timber.d("Updating backend to ${remoteBackendVersion}")
            Toast.makeText(
                caseApp,
                "正在将后端更新到 v${remoteBackendVersion}",
                Toast.LENGTH_SHORT
            ).show()
        }

        // Start download backend
        val backendScript = GithubUtil.downloadFile(
            REPO_BACKEND,
            remoteBackendVersion,
            "sub-store.bundle.js",
            caseApp.filesDir.absolutePath
        )

        if (backendScript.isSuccess) {
            Files.deleteIfExists(Paths.get(caseApp.filesDir.path + "/backend/sub-store.bundle.js"))

            Files.move(
                Paths.get(caseApp.filesDir.path + "/sub-store.bundle.js"),
                Paths.get(caseApp.filesDir.path + "/backend/sub-store.bundle.js")
            )

            localBackendVersion = remoteBackendVersion

            val msg = "Backend updated to ${remoteBackendVersion}"
            Timber.d(msg)
            withContext(Dispatchers.Main) {
                Toast.makeText(caseApp, msg, Toast.LENGTH_SHORT).show()
            }

            return Result.success(Unit)
        } else {
            Timber.w("后端文件下载失败,请检查网络环境")
            withContext(Dispatchers.Main) {
                Toast.makeText(caseApp, "后端文件下载失败,请检查网络环境", Toast.LENGTH_SHORT)
                    .show()
            }

            return Result.failure(Exception("Failed to download backend"))
        }
    }
}
