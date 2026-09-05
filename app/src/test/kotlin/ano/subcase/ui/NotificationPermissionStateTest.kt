package ano.subcase.ui

import org.junit.Assert.assertEquals
import org.junit.Test

class NotificationPermissionStateTest {
    @Test
    fun `first request uses system dialog`() {
        for (sdk in listOf(33, 35, 37)) {
            assertEquals(NotificationPermissionAction.REQUEST,
                resolveNotificationPermissionAction(sdk, false, false, previouslyRequested = false))
        }
    }

    @Test
    fun `denial dismissal and restart all use settings once requested`() {
        // The persisted attempt is the only history needed; callback values do not affect routing.
        assertEquals(NotificationPermissionAction.SETTINGS,
            resolveNotificationPermissionAction(35, false, false, previouslyRequested = true))
    }

    @Test
    fun `authorization releases gate and later revocation returns to settings`() {
        val states = listOf(true to true, false to false, true to true)
        assertEquals(
            listOf(NotificationPermissionAction.GRANTED, NotificationPermissionAction.SETTINGS,
                NotificationPermissionAction.GRANTED),
            states.map { (granted, enabled) ->
                resolveNotificationPermissionAction(35, granted, enabled, previouslyRequested = true)
            },
        )
    }

    @Test
    fun `modern devices require runtime permission and notification switch`() {
        assertEquals(NotificationPermissionAction.SETTINGS,
            resolveNotificationPermissionAction(35, true, false, previouslyRequested = false))
        assertEquals(NotificationPermissionAction.SETTINGS,
            resolveNotificationPermissionAction(35, false, true, previouslyRequested = true))
        for (requested in listOf(false, true)) {
            assertEquals(NotificationPermissionAction.GRANTED,
                resolveNotificationPermissionAction(35, true, true, requested))
        }
    }

    @Test
    fun `legacy devices follow notification switch regardless of request history`() {
        for (sdk in listOf(26, 32)) {
            for (requested in listOf(false, true)) {
                assertEquals(NotificationPermissionAction.GRANTED,
                    resolveNotificationPermissionAction(sdk, false, true, requested))
                assertEquals(NotificationPermissionAction.SETTINGS,
                    resolveNotificationPermissionAction(sdk, false, false, requested))
            }
        }
    }
}
