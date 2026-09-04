package ano.subcase.util

import android.content.Context
import ano.subcase.engine.BackendFiles
import ano.subcase.engine.bridge.PersistentStoreBridge
import org.json.JSONArray
import org.json.JSONObject
import org.json.JSONTokener
import java.io.File

/** 将旧 Node 后端及其文件存储一次性迁移到当前执行环境。 */
object LegacyNodeMigration {
    /** 入口：迁移 KV、校验内置后端，再删除旧文件并写入完成标记。 */
    fun migrate(context: Context) {
        if (ConfigStore.legacyNodeMigrated) return

        val backendDir = BackendFiles.ensureInstalled(context)
        val dataDir = File(context.filesDir, "data")
        val subStoreFile = File(dataDir, "sub-store.json")
        val rootFile = File(dataDir, "root.json")

        migratePersistentStore(context, subStoreFile, rootFile)
        verifyBackend(backendDir)

        deleteLegacyFile(File(backendDir, "sub-store.bundle.js"))
        deleteLegacyFile(File(context.filesDir, "sub-store.bundle.js"))
        deleteLegacyFile(subStoreFile)
        deleteLegacyFile(rootFile)

        ConfigStore.legacyNodeMigrated = true
        check(ConfigStore.legacyNodeMigrated) { "写入旧 Node 迁移完成标记失败" }
    }

    /** 将 sub-store.json 与 root.json 写入同步字符串 KV，并回读校验。 */
    private fun migratePersistentStore(
        context: Context,
        subStoreFile: File,
        rootFile: File,
    ) {
        val expected = linkedMapOf<String, String?>()
        if (subStoreFile.exists()) {
            val raw = subStoreFile.readText()
            check(JSONTokener(raw).nextValue() is JSONObject) { "sub-store.json 不是有效 JSON 对象" }
            expected["sub-store"] = raw
        }
        if (rootFile.exists()) {
            val root = JSONObject(rootFile.readText())
            for (key in root.keys()) {
                expected[key] = root.opt(key).toPersistentString()
            }
        }

        val prefs = context.getSharedPreferences(
            PersistentStoreBridge.PREFERENCES_NAME,
            Context.MODE_PRIVATE,
        )
        val editor = prefs.edit()
        expected.forEach { (key, value) ->
            if (value == null) editor.remove(key) else editor.putString(key, value)
        }
        check(editor.commit()) { "写入 Sub-Store KV 失败" }
        expected.forEach { (key, value) ->
            check(prefs.getString(key, null) == value) { "回读 Sub-Store KV 失败: $key" }
        }
    }

    /** 确认 runner、桥接脚本与当前版本后端脚本均已释放。 */
    private fun verifyBackend(backendDir: File) {
        check(File(backendDir, "runner.html").isFile) { "runner.html 未释放" }
        check(File(backendDir, "loon-bridge.js").isFile) { "loon-bridge.js 未释放" }
        check(BackendFiles.hasScripts(File(backendDir, ConfigStore.localBackendVersion))) {
            "APK 内置后端脚本不完整"
        }
    }

    /** 删除单个旧 Node 文件；不存在则跳过，删除失败则中断。 */
    private fun deleteLegacyFile(file: File) {
        check(!file.exists() || file.delete()) { "删除旧 Node 文件失败: $file" }
    }

    /** 将 JSON 值转为 PersistentStore 可写入的字符串；null 表示删除该键。 */
    private fun Any?.toPersistentString(): String? = when (this) {
        null, JSONObject.NULL -> null
        is String -> this
        is JSONObject, is JSONArray -> toString()
        else -> toString()
    }
}
