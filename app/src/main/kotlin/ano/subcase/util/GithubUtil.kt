package ano.subcase.util

import ano.subcase.model.AppRelease
import okhttp3.OkHttpClient
import okhttp3.Request
import org.jsoup.Jsoup
import org.jsoup.nodes.Element
import timber.log.Timber
import java.io.File
import java.net.URI

const val REPO_BACKEND = "https://github.com/sub-store-org/Sub-Store"
const val REPO_FRONTEND = "https://github.com/sub-store-org/Sub-Store-Front-End"
const val REPO_APP = "https://github.com/sub-store-org/subcase"

object GithubUtil {
    private val client = OkHttpClient()
    private val releaseApkRegex = Regex("^SubCase-(.+)-(\\d+)-release\\.apk$")

    fun getLatestVersion(repoUrl: String): Result<String> {
        val latestUrl = "$repoUrl/releases/latest"
        val request = Request.Builder().url(latestUrl).build()

        try {
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    return Result.failure(Exception("请求最新版本失败：HTTP ${response.code}"))
                }
                val latestReleaseUrl = response.request.url
                Timber.d("latest release url: $latestReleaseUrl")
                val latestVersion = latestReleaseUrl.pathSegments.lastOrNull().orEmpty()
                if (latestVersion.isBlank() || latestVersion == "latest") {
                    return Result.failure(Exception("GitHub 最新版本地址无效"))
                }
                return Result.success(latestVersion)
            }
        } catch (e: Exception) {
            Timber.e(e)
            return Result.failure(e)
        }
    }

    fun getLatestAppRelease(): Result<AppRelease> = runCatching {
        val tagName = getLatestVersion(REPO_APP).getOrThrow()
        check(tagName.isNotBlank()) { "GitHub Release 版本号为空" }

        val releasePageUrl = "$REPO_APP/releases/tag/$tagName"
        val releaseHtml = getText(releasePageUrl)
        val releaseDocument = Jsoup.parse(releaseHtml, releasePageUrl)
        val assetsUrl = releaseDocument
            .selectFirst("include-fragment[src*=/releases/expanded_assets/]")
            ?.absUrl("src")
            ?.takeIf(String::isNotBlank)
            ?: error("GitHub Release 页面缺少安装包列表")
        val assetsHtml = getText(assetsUrl)

        parseAppRelease(
            tagName = tagName,
            releasePageUrl = releasePageUrl,
            releaseHtml = releaseHtml,
            assetsPageUrl = assetsUrl,
            assetsHtml = assetsHtml,
        )
    }.onFailure { Timber.e(it, "读取 App Release 失败") }

    internal fun parseAppRelease(
        tagName: String,
        releasePageUrl: String,
        releaseHtml: String,
        assetsPageUrl: String,
        assetsHtml: String,
    ): AppRelease {
        val releaseDocument = Jsoup.parse(releaseHtml, releasePageUrl)
        val body = releaseDocument.selectFirst("div[data-test-selector=body-content]")
            ?: error("GitHub Release 页面缺少发布说明")
        val releaseNotes = formatReleaseNotes(body)
        check(releaseNotes.isNotBlank()) { "GitHub Release 发布说明为空" }

        val assetsDocument = Jsoup.parse(assetsHtml, assetsPageUrl)
        val releaseAssets = assetsDocument.select("a[href]").mapNotNull { link ->
            val href = link.absUrl("href").takeIf(String::isNotBlank) ?: return@mapNotNull null
            val fileName = URI(href).path.substringAfterLast('/')
            val match = releaseApkRegex.matchEntire(fileName) ?: return@mapNotNull null
            Triple(fileName, href, match)
        }
        check(releaseAssets.size == 1) {
            "GitHub Release 正式 APK 数量异常：${releaseAssets.size}"
        }

        val (apkName, downloadUrl, match) = releaseAssets.single()
        return AppRelease(
            tagName = tagName,
            versionName = match.groupValues[1],
            versionCode = match.groupValues[2].toInt(),
            releaseNotes = releaseNotes,
            apkName = apkName,
            downloadUrl = downloadUrl,
        )
    }

    private fun formatReleaseNotes(body: Element): String = body.children()
        .mapNotNull { element ->
            when (element.tagName()) {
                "ul", "ol" -> element.children()
                    .filter { it.tagName() == "li" }
                    .joinToString("\n") { "• ${it.text()}" }
                else -> element.text()
            }.trim().takeIf(String::isNotBlank)
        }
        .joinToString("\n\n")

    private fun getText(url: String): String {
        val request = Request.Builder()
            .url(url)
            .header("User-Agent", "SubCase")
            .build()
        client.newCall(request).execute().use { response ->
            check(response.isSuccessful) { "请求失败：HTTP ${response.code}" }
            return response.body.string()
        }
    }

    fun downloadFile(
        url: String,
        destination: File,
        onProgress: (Float?) -> Unit,
    ): Result<File> = runCatching {
        val request = Request.Builder()
            .url(url)
            .header("User-Agent", "SubCase")
            .build()
        client.newCall(request).execute().use { response ->
            check(response.isSuccessful) { "下载失败：HTTP ${response.code}" }
            destination.parentFile?.mkdirs()
            val totalBytes = response.body.contentLength()
            var copiedBytes = 0L
            var lastPercent = -1
            response.body.byteStream().use { input ->
                destination.outputStream().use { output ->
                    val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                    while (true) {
                        val count = input.read(buffer)
                        if (count < 0) break
                        output.write(buffer, 0, count)
                        copiedBytes += count
                        if (totalBytes > 0) {
                            val percent = ((copiedBytes * 100) / totalBytes).toInt()
                            if (percent != lastPercent) {
                                lastPercent = percent
                                onProgress(percent / 100f)
                            }
                        } else {
                            onProgress(null)
                        }
                    }
                }
            }
        }
        destination
    }.onFailure {
        destination.delete()
        Timber.e(it, "下载 App APK 失败")
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
