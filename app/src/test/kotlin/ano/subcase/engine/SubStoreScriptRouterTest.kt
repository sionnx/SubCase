package ano.subcase.engine

import ano.subcase.model.SubStoreScript
import org.junit.Assert.assertEquals
import org.junit.Test

class SubStoreScriptRouterTest {
    @Test
    fun `core regex routes matching request prefixes to core script`() {
        listOf(
            "/download",
            "/download/wd",
            "/download/collection/example/ClashMeta",
            "/api/preview",
            "/api/preview/extra",
            "/api/sync",
            "/api/sync/extra",
            "/api/utils/node-info",
            "/api/utils/node-info/extra",
        ).forEach { path ->
            assertEquals(
                SubStoreScript.CORE,
                SubStoreScriptRouter.select("https://sub.store$path?x=1"),
            )
        }
    }

    @Test
    fun `all other paths select simple script`() {
        listOf("/", "/api/settings", "/api/subs", "/api/utils/file-info").forEach { path ->
            assertEquals(
                SubStoreScript.SIMPLE,
                SubStoreScriptRouter.select("https://sub.store$path"),
            )
        }
    }

    @Test
    fun `backend version accepts release tag characters only`() {
        listOf("2.38.1", "v2.38.1", "release_2-38").forEach {
            assertEquals(true, BackendFiles.isSafeVersion(it))
        }
        listOf("", "../2.38.1", "2.38.1/next", "2.38 1").forEach {
            assertEquals(false, BackendFiles.isSafeVersion(it))
        }
    }
}
