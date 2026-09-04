package ano.subcase.model

data class AppRelease(
    val tagName: String,
    val versionName: String,
    val versionCode: Int,
    val releaseNotes: String,
    val apkName: String,
    val downloadUrl: String,
)
