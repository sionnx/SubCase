package ano.subcase.ui.components

import org.junit.Assert.assertEquals
import org.junit.Test

class SubStoreUrlTest {
    @Test
    fun localMode_usesLoopbackAddress() {
        assertEquals(
            "http://127.0.0.1:8080/subs?api=http://127.0.0.1:8081",
            buildSubStoreUrl(allowLan = false, lanIp = "192.168.1.10"),
        )
    }

    @Test
    fun lanMode_usesLanAddress() {
        assertEquals(
            "http://192.168.1.10:8080/subs?api=http://192.168.1.10:8081",
            buildSubStoreUrl(allowLan = true, lanIp = "192.168.1.10"),
        )
    }

    @Test
    fun lanModeWithBlankAddress_fallsBackToLoopbackAddress() {
        assertEquals(
            "http://127.0.0.1:8080/subs?api=http://127.0.0.1:8081",
            buildSubStoreUrl(allowLan = true, lanIp = ""),
        )
    }
}
