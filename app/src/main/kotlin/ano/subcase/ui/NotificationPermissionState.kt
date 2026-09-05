package ano.subcase.ui

internal enum class NotificationPermissionAction { GRANTED, REQUEST, SETTINGS }

internal fun resolveNotificationPermissionAction(
    sdkInt: Int,
    runtimePermissionGranted: Boolean,
    notificationsEnabled: Boolean,
    previouslyRequested: Boolean,
): NotificationPermissionAction {
    if (notificationsEnabled && (sdkInt < 33 || runtimePermissionGranted)) {
        return NotificationPermissionAction.GRANTED
    }
    if (sdkInt >= 33 && !runtimePermissionGranted && !previouslyRequested) {
        return NotificationPermissionAction.REQUEST
    }
    return NotificationPermissionAction.SETTINGS
}
