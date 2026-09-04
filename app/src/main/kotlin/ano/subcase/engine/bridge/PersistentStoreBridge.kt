package ano.subcase.engine.bridge

import android.content.Context
import android.webkit.JavascriptInterface
import androidx.annotation.Keep

/** Loon 字符串 KV 的最小同步接口。 */
interface LoonPersistentStore {
    fun read(key: String): String?
    fun write(value: String?, key: String): Boolean
}

/** 通过 SharedPreferences 向 WebView 暴露同步的 `$persistentStore`。 */
@Keep
class PersistentStoreBridge(context: Context) : LoonPersistentStore {
    private val prefs = context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)

    /**
     * 同步读取 Sub-Store 的字符串 KV。
     * `@JavascriptInterface` 方法由 WebView 的 Bridge 后台线程调用。
     */
    @JavascriptInterface
    override fun read(key: String): String? = synchronized(lock) {
        prefs.getString(key, null)
    }

    /**
     * 同步提交 KV；null 表示删除。
     * commit() 返回时数据已提交，紧随其后的 read() 可以看到新值。
     */
    @JavascriptInterface
    override fun write(value: String?, key: String): Boolean = synchronized(lock) {
        val editor = prefs.edit()
        if (value == null) editor.remove(key) else editor.putString(key, value)
        editor.commit()
    }

    companion object {
        const val PREFERENCES_NAME = "sub_store_persistent_store"
        // 两个 WebView Bridge 共用进程锁，确保同一个 key 的提交顺序明确。
        private val lock = Any()
    }
}
