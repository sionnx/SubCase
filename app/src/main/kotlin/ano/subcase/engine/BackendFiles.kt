package ano.subcase.engine

import android.content.Context
import ano.subcase.util.ConfigStore
import java.io.File

/** 管理 APK 内置后端资源和正式运行目录。 */
object BackendFiles {
    const val BUNDLED_VERSION = "2.38.2"

    fun directory(context: Context): File = File(context.filesDir, "backend")

    /** 确保 runner、Bridge 和当前版本脚本位于 filesDir/backend。 */
    fun ensureInstalled(context: Context): File {
        val backendDir = directory(context).apply { mkdirs() }
        copyAsset(context, "backend/runner.html", File(backendDir, "runner.html"), overwrite = true)
        copyAsset(context, "backend/loon-bridge.js", File(backendDir, "loon-bridge.js"), overwrite = true)

        val configuredVersion = ConfigStore.localBackendVersion
        val configuredDir = File(backendDir, configuredVersion)
        val configuredReady = isSafeVersion(configuredVersion) && hasScripts(configuredDir)
        if (!configuredReady) {
            val bundledDir = File(backendDir, BUNDLED_VERSION).apply { mkdirs() }
            for (name in SCRIPT_NAMES) {
                copyAsset(
                    context,
                    "backend/$BUNDLED_VERSION/$name",
                    File(bundledDir, name),
                    overwrite = false,
                )
            }
            check(hasScripts(bundledDir)) { "APK 内置后端脚本不完整" }
            ConfigStore.localBackendVersion = BUNDLED_VERSION
        }
        return backendDir
    }

    fun versionDir(context: Context, version: String = ConfigStore.localBackendVersion): File =
        File(context.filesDir, "backend/$version")

    fun hasScripts(directory: File): Boolean = SCRIPT_NAMES.all { File(directory, it).isFile }

    fun isSafeVersion(version: String): Boolean =
        version.isNotBlank() && !version.contains("..") && version.matches(Regex("[A-Za-z0-9._-]+"))

    private fun copyAsset(context: Context, assetPath: String, target: File, overwrite: Boolean) {
        if (!overwrite && target.isFile) return
        target.parentFile?.mkdirs()
        context.assets.open(assetPath).use { input ->
            target.outputStream().use(input::copyTo)
        }
    }

    val SCRIPT_NAMES = listOf("sub-store-0.min.js", "sub-store-1.min.js")
}
