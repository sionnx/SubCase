package ano.subcase.util

import android.widget.Toast
import androidx.compose.runtime.mutableStateOf
import ano.subcase.caseApp
import ano.subcase.engine.BackendFiles
import ano.subcase.engine.BackendScriptValidator
import ano.subcase.util.AppUtil.unzip
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.io.File
import java.nio.file.Files
import java.nio.file.Paths
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

object SubStore {

    val basePath = caseApp.filesDir

    private val updateMutex = Mutex()

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

    private val lastFrontendInstalledAtState = mutableStateOf(ConfigStore.lastFrontendInstalledAt)
    var lastFrontendInstalledAt: Long
        get() = lastFrontendInstalledAtState.value
        set(value) {
            ConfigStore.lastFrontendInstalledAt = value
            lastFrontendInstalledAtState.value = value
        }

    private val lastBackendInstalledAtState = mutableStateOf(ConfigStore.lastBackendInstalledAt)
    var lastBackendInstalledAt: Long
        get() = lastBackendInstalledAtState.value
        set(value) {
            ConfigStore.lastBackendInstalledAt = value
            lastBackendInstalledAtState.value = value
        }

    var remoteFrontendVersion = ConfigStore.localFrontendVersion
    var remoteBackendVersion = ConfigStore.localBackendVersion

    /** 升级用户若没有安装时间，用目录/脚本的 lastModified 补上并落盘。 */
    fun ensureInstallTimestamps() {
        if (lastFrontendInstalledAt <= 0L) {
            val frontendDir = File(caseApp.filesDir, "frontend")
            lastFrontendInstalledAt = frontendDir.takeIf { it.exists() }
                ?.lastModified()
                ?.takeIf { it > 0L }
                ?: System.currentTimeMillis()
        }
        if (lastBackendInstalledAt <= 0L) {
            val script = File(
                BackendFiles.versionDir(caseApp),
                BackendFiles.SCRIPT_NAMES.first(),
            )
            lastBackendInstalledAt = script.takeIf { it.isFile }
                ?.lastModified()
                ?.takeIf { it > 0L }
                ?: System.currentTimeMillis()
        }
    }

    /** 查询 GitHub 最新 tag，有更新则静默安装；互斥避免启动检查、手动检查和服务循环并发下载。 */
    suspend fun checkAndUpdate(showToast: Boolean = false): Result<Unit> = updateMutex.withLock {
        val updateAvailable = checkLatestVersionAwait(showToast)
        ConfigStore.lastSubStoreRemoteVersionCheckAt = System.currentTimeMillis()
        if (!updateAvailable) return@withLock Result.success(Unit)

        var succeeded = true
        if (remoteFrontendVersion.isNotEmpty() && remoteFrontendVersion != localFrontendVersion) {
            succeeded = updateFrontend(showToast).isSuccess && succeeded
        }
        if (remoteBackendVersion.isNotEmpty() && remoteBackendVersion != localBackendVersion) {
            succeeded = updateBackend(showToast).isSuccess && succeeded
        }
        if (succeeded) Result.success(Unit)
        else Result.failure(IllegalStateException("SubStore 更新失败"))
    }

    /** 分别拉取前后端最新版本；仅手动检查时 toast。返回是否有可安装的新版本。 */
    private suspend fun checkLatestVersionAwait(showToast: Boolean = false): Boolean {
        val backendResult = withContext(Dispatchers.IO) {
            GithubUtil.getLatestVersion(REPO_BACKEND)
        }
        if (backendResult.isSuccess) {
            remoteBackendVersion = backendResult.getOrThrow()
        } else {
            Timber.e(backendResult.exceptionOrNull(), "检测后端新版本失败")
            toastIf(showToast, "检测后端新版本失败,请检查您的网络环境")
        }

        val frontendResult = withContext(Dispatchers.IO) {
            GithubUtil.getLatestVersion(REPO_FRONTEND)
        }
        if (frontendResult.isSuccess) {
            remoteFrontendVersion = frontendResult.getOrThrow()
        } else {
            Timber.e(frontendResult.exceptionOrNull(), "检测前端新版本失败")
            toastIf(showToast, "检测前端新版本失败,请检查您的网络环境")
        }

        val updateAvailable =
            (remoteFrontendVersion.isNotEmpty() && remoteFrontendVersion != localFrontendVersion) ||
                (remoteBackendVersion.isNotEmpty() && remoteBackendVersion != localBackendVersion)
        if (!updateAvailable && frontendResult.isSuccess && backendResult.isSuccess) {
            toastIf(showToast, "当前已是最新版本")
        }
        return updateAvailable
    }

    /** 下载 dist.zip 并替换 filesDir/frontend，成功后写入前端安装时间。 */
    private suspend fun updateFrontend(showToast: Boolean): Result<Unit> {
        Timber.d("Updating frontend to $remoteFrontendVersion")
        toastIf(showToast, "正在将前端更新到 v$remoteFrontendVersion")

        val result = withContext(Dispatchers.IO) {
            val download = GithubUtil.downloadFile(
                REPO_FRONTEND,
                remoteFrontendVersion,
                "dist.zip",
                caseApp.filesDir.absolutePath,
            )
            if (download.isFailure) {
                return@withContext Result.failure<Unit>(
                    download.exceptionOrNull() ?: Exception("Failed to download frontend"),
                )
            }

            val zipPath = caseApp.filesDir.path + "/dist.zip"
            unzip(File(zipPath), File(caseApp.filesDir.path))

            if (Files.exists(Paths.get(caseApp.filesDir.path + "/frontend"))) {
                File(caseApp.filesDir.path + "/frontend").deleteRecursively()

                Files.move(
                    Paths.get(caseApp.filesDir.path + "/dist"),
                    Paths.get(caseApp.filesDir.path + "/frontend"),
                )

                localFrontendVersion = remoteFrontendVersion
                lastFrontendInstalledAt = System.currentTimeMillis()
            }
            Result.success(Unit)
        }

        if (result.isSuccess) {
            Timber.d("Frontend updated to $remoteFrontendVersion")
            toastIf(showToast, "前端已更新到 v$remoteFrontendVersion")
            return Result.success(Unit)
        } else {
            Timber.w("前端文件下载失败,请检查网络环境")
            toastIf(showToast, "前端文件下载失败,请检查网络环境")
            return Result.failure(result.exceptionOrNull() ?: Exception("Failed to download frontend"))
        }
    }

    /** 校验并下载后端脚本到版本目录，成功后切换 localBackendVersion；下一请求即用新脚本，不必重启服务。 */
    private suspend fun updateBackend(showToast: Boolean): Result<Unit> {
        Timber.d("Updating backend to $remoteBackendVersion")
        toastIf(showToast, "正在将后端更新到 v$remoteBackendVersion")

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

                    localBackendVersion = remoteBackendVersion
                    lastBackendInstalledAt = System.currentTimeMillis()
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
            Timber.d("Backend updated to $remoteBackendVersion")
            toastIf(showToast, "后端已更新到 v$remoteBackendVersion")
        }.onFailure { error ->
            Timber.e(error, "后端更新失败")
            toastIf(showToast, "后端文件更新失败,请检查网络环境")
        }
        return result
    }

    /** 自动更新路径保持静默；手动检查才在主线程弹出结果。 */
    private suspend fun toastIf(showToast: Boolean, message: String) {
        if (!showToast) return
        withContext(Dispatchers.Main) {
            Toast.makeText(caseApp, message, Toast.LENGTH_SHORT).show()
        }
    }
}

private val lastInstalledAtFormatter: DateTimeFormatter =
    DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm").withZone(ZoneId.systemDefault())

/** 设置页 footer 用的安装时间；未记录时显示破折号。 */
fun formatSubStoreInstalledAt(millis: Long): String {
    if (millis <= 0L) return "—"
    return lastInstalledAtFormatter.format(Instant.ofEpochMilli(millis))
}
