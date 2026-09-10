package ano.subcase.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SubStoreUpdatePolicyTest {
    @Test
    fun `manual and on start have no interval`() {
        assertNull(SubStoreUpdatePolicy.MANUAL.intervalMillis)
        assertNull(SubStoreUpdatePolicy.ON_START.intervalMillis)
    }

    @Test
    fun `interval policies map to expected durations`() {
        val hour = 60L * 60L * 1000L
        assertEquals(3 * hour, SubStoreUpdatePolicy.EVERY_3H.intervalMillis)
        assertEquals(6 * hour, SubStoreUpdatePolicy.EVERY_6H.intervalMillis)
        assertEquals(12 * hour, SubStoreUpdatePolicy.EVERY_12H.intervalMillis)
        assertEquals(24 * hour, SubStoreUpdatePolicy.EVERY_24H.intervalMillis)
        assertEquals(48 * hour, SubStoreUpdatePolicy.EVERY_48H.intervalMillis)
        assertEquals(7 * 24 * hour, SubStoreUpdatePolicy.EVERY_7D.intervalMillis)
    }

    @Test
    fun `unknown stored value falls back to on start`() {
        assertEquals(SubStoreUpdatePolicy.ON_START, SubStoreUpdatePolicy.fromStored(null))
        assertEquals(SubStoreUpdatePolicy.ON_START, SubStoreUpdatePolicy.fromStored(""))
        assertEquals(SubStoreUpdatePolicy.ON_START, SubStoreUpdatePolicy.fromStored("WEEKLY"))
        assertEquals(
            SubStoreUpdatePolicy.EVERY_12H,
            SubStoreUpdatePolicy.fromStored("EVERY_12H"),
        )
    }

    @Test
    fun `never checked is immediately due`() {
        assertEquals(
            0L,
            millisUntilNextSubStoreVersionCheck(
                nowMillis = 10_000L,
                lastSubStoreRemoteVersionCheckAt = 0L,
                intervalMillis = 3 * 60 * 60 * 1000L,
            ),
        )
    }

    @Test
    fun `remaining wait is interval minus elapsed`() {
        val interval = 3 * 60 * 60 * 1000L
        val lastCheck = 1_000L
        val now = lastCheck + 60 * 60 * 1000L
        assertEquals(
            2 * 60 * 60 * 1000L,
            millisUntilNextSubStoreVersionCheck(now, lastCheck, interval),
        )
    }

    @Test
    fun `overdue check waits zero`() {
        val interval = 3 * 60 * 60 * 1000L
        val lastCheck = 1_000L
        val now = lastCheck + interval + 1
        assertEquals(
            0L,
            millisUntilNextSubStoreVersionCheck(now, lastCheck, interval),
        )
    }
}
