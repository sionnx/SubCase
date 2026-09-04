package ano.subcase.model

/** 传递给 Sub-Store Loon 脚本的请求。 */
data class LoonRequest(
    val url: String,
    val method: String,
    val headers: Map<String, String>,
    val body: String?,
)

/** Loon `$done({ response: ... })` 返回的 HTTP 响应。 */
data class LoonResponse(
    val status: Int = 200,
    val headers: Map<String, String> = emptyMap(),
    val body: String = "",
)

/** 后端脚本类型；每种类型由一个固定的 WebView 执行。 */
enum class SubStoreScript(
    val fileName: String,
    val tag: String,
) {
    SIMPLE("sub-store-0.min.js", "Sub-Store Simple"),
    CORE("sub-store-1.min.js", "Sub-Store Core"),
}
