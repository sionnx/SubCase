package ano.subcase.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GithubUtilTest {
    private val releaseHtml = """
        <html><body>
          <div data-test-selector="body-content" class="markdown-body">
            <h3>功能优化</h3>
            <ul>
              <li>启动前检查 <code>8080</code> 和 <code>8081</code> 端口。</li>
              <li>首页支持垂直滚动。</li>
            </ul>
            <h3>安装包优化</h3>
            <p>减小 APK 文件体积。</p>
          </div>
          <include-fragment src="/sub-store-org/subcase/releases/expanded_assets/v0.4.2" />
        </body></html>
    """.trimIndent()

    private val assetsHtml = """
        <html><body>
          <a href="/sub-store-org/subcase/releases/download/v0.4.2/mapping.txt">mapping.txt</a>
          <a href="/sub-store-org/subcase/releases/download/v0.4.2/SubCase-0.4.2-52-debug.apk">
            SubCase-0.4.2-52-debug.apk
          </a>
          <a href="/sub-store-org/subcase/releases/download/v0.4.2/SubCase-0.4.2-52-release.apk">
            SubCase-0.4.2-52-release.apk
          </a>
        </body></html>
    """.trimIndent()

    @Test
    fun `release html provides notes and the release apk`() {
        val release = GithubUtil.parseAppRelease(
            tagName = "v0.4.2",
            releasePageUrl = "https://github.com/sub-store-org/subcase/releases/tag/v0.4.2",
            releaseHtml = releaseHtml,
            assetsPageUrl = "https://github.com/sub-store-org/subcase/releases/expanded_assets/v0.4.2",
            assetsHtml = assetsHtml,
        )

        assertEquals("v0.4.2", release.tagName)
        assertEquals("0.4.2", release.versionName)
        assertEquals(52, release.versionCode)
        assertEquals("SubCase-0.4.2-52-release.apk", release.apkName)
        assertTrue(release.releaseNotes.contains("功能优化"))
        assertTrue(release.releaseNotes.contains("• 启动前检查 8080 和 8081 端口。"))
        assertFalse(release.downloadUrl.contains("debug"))
        assertFalse(release.downloadUrl.contains("mapping.txt"))
    }

    @Test(expected = IllegalStateException::class)
    fun `missing release apk is reported`() {
        GithubUtil.parseAppRelease(
            tagName = "v0.4.2",
            releasePageUrl = "https://github.com/sub-store-org/subcase/releases/tag/v0.4.2",
            releaseHtml = releaseHtml,
            assetsPageUrl = "https://github.com/sub-store-org/subcase/releases/expanded_assets/v0.4.2",
            assetsHtml = "<a href='/mapping.txt'>mapping.txt</a>",
        )
    }
}
