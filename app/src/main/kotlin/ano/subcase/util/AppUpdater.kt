package ano.subcase.util

import android.content.Context
import ano.subcase.model.AppRelease
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

object AppUpdater {
    suspend fun checkLatest(): Result<AppRelease> = withContext(Dispatchers.IO) {
        GithubUtil.getLatestAppRelease()
    }

    suspend fun download(
        context: Context,
        release: AppRelease,
        onProgress: (Float?) -> Unit,
    ): Result<File> = withContext(Dispatchers.IO) {
        val directory = File(context.cacheDir, "app-update")
        directory.listFiles()?.forEach(File::delete)
        val partFile = File(directory, "${release.apkName}.part")
        val apkFile = File(directory, release.apkName)
        partFile.delete()
        apkFile.delete()

        GithubUtil.downloadFile(release.downloadUrl, partFile, onProgress).mapCatching {
            check(partFile.renameTo(apkFile)) { "无法保存下载完成的安装包" }
            apkFile
        }
    }
}
