package ano.subcase.util

import android.widget.Toast
import androidx.compose.runtime.mutableStateOf
import ano.subcase.caseApp
import ano.subcase.engine.BackendFiles
import ano.subcase.engine.BackendScriptValidator
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

        if (remoteBackendVersion == localBackendVersion) return Result.success(Unit)
        if (!BackendFiles.isSafeVersion(remoteBackendVersion)) {
            return Result.failure(IllegalArgumentException("无效的后端版本号"))
        }

        val result = withContext(Dispatchers.IO) {
            runCatching {
                val cacheDirectory = File(
                    caseApp.cacheDir,
                    "substore-update-$remoteBackendVersion",
                )
                cacheDirectory.deleteRecursively()
                check(cacheDirectory.mkdirs()) { "无法创建后端更新缓存目录" }
                try {
                    for (scriptName in BackendFiles.SCRIPT_NAMES) {
                        GithubUtil.downloadFile(
                            REPO_BACKEND,
                            remoteBackendVersion,
                            scriptName,
                            cacheDirectory.absolutePath,
                        ).getOrThrow()
                    }
                    check(BackendFiles.hasScripts(cacheDirectory)) { "下载的后端脚本不完整" }
                    BackendScriptValidator.validate(cacheDirectory).getOrThrow()
                    cacheDirectory.listFiles()
                        ?.filter { it.name !in BackendFiles.SCRIPT_NAMES }
                        ?.forEach(File::deleteRecursively)
                    check(
                        cacheDirectory.listFiles()?.map(File::getName)?.toSet() ==
                            BackendFiles.SCRIPT_NAMES.toSet(),
                    ) { "后端更新缓存包含非正式文件" }

                    val target = File(caseApp.filesDir, "backend/$remoteBackendVersion")
                    target.deleteRecursively()
                    target.parentFile?.mkdirs()
                    Files.move(cacheDirectory.toPath(), target.toPath())

                    // 更新判定只使用 release version；激活过程不生成或比较摘要。
                    localBackendVersion = remoteBackendVersion
                    File(caseApp.filesDir, "backend").listFiles()
                        ?.filter {
                            it.isDirectory &&
                                it.name != localBackendVersion &&
                                BackendFiles.hasScripts(it)
                        }
                        ?.forEach(File::deleteRecursively)
                    Unit
                } finally {
                    cacheDirectory.deleteRecursively()
                }
            }
        }

        result.onSuccess {
            val msg = "Backend updated to $remoteBackendVersion"
            Timber.d(msg)
            withContext(Dispatchers.Main) {
                Toast.makeText(caseApp, msg, Toast.LENGTH_SHORT).show()
            }
        }.onFailure { error ->
            Timber.e(error, "后端更新失败")
            withContext(Dispatchers.Main) {
                Toast.makeText(caseApp, "后端文件更新失败,请检查网络环境", Toast.LENGTH_SHORT).show()
            }
        }
        return result
    }
}
