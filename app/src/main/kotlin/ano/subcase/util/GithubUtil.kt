package ano.subcase.util

import okhttp3.OkHttpClient
import okhttp3.Request
import timber.log.Timber
import java.io.File

const val REPO_BACKEND = "https://github.com/sub-store-org/Sub-Store"
const val REPO_FRONTEND = "https://github.com/sub-store-org/Sub-Store-Front-End"

object GithubUtil {
    private val client = OkHttpClient()

    fun getLatestVersion(repoUrl: String): Result<String> {

        val latestUrl = "$repoUrl/releases/latest"

        val request = Request.Builder().url(latestUrl).build()

        try {
            val response = client.newCall(request).execute()

            val latestReleaseUrl = response.networkResponse?.request?.url.toString()
            Timber.d("latest release url: $latestReleaseUrl")

            response.close()

            val latestVersion = latestReleaseUrl.substringAfterLast("/")
            println("LatestVersion: $latestVersion , RepoUrl: $repoUrl")

            return Result.success(latestVersion)
        } catch (e: Exception) {
            Timber.e(e)
            return Result.failure(e)
        }
    }

    fun downloadFile(
        projectUrl: String,
        version: String,
        fileName: String,
        destPath: String
    ): Result<String> {
        try {
            // get download url
            val fileRequest =
                Request.Builder().url("$projectUrl/releases/download/$version/$fileName").build()
            client.newCall(fileRequest).execute().use { fileResponse ->
                if (!fileResponse.isSuccessful) {
                    Timber.d("Failed to download file, response Code: ${fileResponse.code}")
                    return Result.failure(Exception("Failed to download file"))
                }

                // 下载目标由调用方指定；后端更新目标固定在 cacheDir。
                val file = File(destPath, fileName)
                file.parentFile?.mkdirs()
                file.outputStream().use { output ->
                    fileResponse.body.byteStream().use { input ->
                        input.copyTo(output)
                    }
                }
            }
            return Result.success("")
        } catch (e: Exception) {
            Timber.e(e)
            return Result.failure(e)
        }
    }

}
