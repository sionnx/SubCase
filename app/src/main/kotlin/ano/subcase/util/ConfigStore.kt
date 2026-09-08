package ano.subcase.util

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit
import ano.subcase.caseApp
import ano.subcase.util.PreferencesKeys.ALLOW_CRASH_REPORT
import ano.subcase.util.PreferencesKeys.ALLOW_LAN
import ano.subcase.util.PreferencesKeys.APP_IS_FIRST_OPEN
import ano.subcase.util.PreferencesKeys.BACKEND_LOCAL_VER
import ano.subcase.util.PreferencesKeys.FRONTEND_LOCAL_VER
import ano.subcase.util.PreferencesKeys.LEGACY_NODE_MIGRATED

object PreferencesKeys {
    const val HOME_PANEL_VERTICAL_FRACTION = "home_panel_vertical_fraction"
    const val NOTIFICATION_PERMISSION_REQUESTED = "notification_permission_requested"

    const val APP_IS_FIRST_OPEN = "app_first_open"

    const val ALLOW_LAN = "allow_lan"
    const val ALLOW_CRASH_REPORT = "allow_crash_report"

    const val BACKEND_LOCAL_VER = "backend_local_ver"
    const val FRONTEND_LOCAL_VER = "frontend_local_ver"

    const val LEGACY_NODE_MIGRATED = "legacy_node_migrated"
}

object ConfigStore {
    val homePanelVerticalFraction: Float?
        get() = getInstance()
            .getFloat(PreferencesKeys.HOME_PANEL_VERTICAL_FRACTION, Float.NaN)
            .takeIf { it.isFinite() }
            ?.coerceIn(0f, 1f)

    /** Call from an IO dispatcher; commit reports disk write failures. */
    fun saveHomePanelVerticalFraction(fraction: Float) {
        require(fraction.isFinite() && fraction in 0f..1f)
        check(getInstance().edit()
            .putFloat(PreferencesKeys.HOME_PANEL_VERTICAL_FRACTION, fraction)
            .commit()) { "Failed to save home panel position" }
    }

    private var prefs: SharedPreferences? = null

    fun getInstance(): SharedPreferences {
        if (prefs == null) {
            prefs = caseApp.getSharedPreferences("settings", Context.MODE_PRIVATE)
        }
        return prefs!!
    }

    var notificationPermissionRequested: Boolean
        get() = getInstance().getBoolean(PreferencesKeys.NOTIFICATION_PERMISSION_REQUESTED, false)
        set(value) {
            getInstance().edit { putBoolean(PreferencesKeys.NOTIFICATION_PERMISSION_REQUESTED, value) }
        }

    var isFirstOpen: Boolean
        get() = getInstance().getBoolean(APP_IS_FIRST_OPEN, true)
        set(value) {
            getInstance().edit { putBoolean(APP_IS_FIRST_OPEN, value) }
        }

    var isAllowLan: Boolean
        get() = getInstance().getBoolean(ALLOW_LAN, false)
        set(value) {
            getInstance().edit { putBoolean(ALLOW_LAN, value) }
        }

    var isAllowCrashReport: Boolean
        get() = getInstance().getBoolean(ALLOW_CRASH_REPORT, true)
        set(value) {
            getInstance().edit { putBoolean(ALLOW_CRASH_REPORT, value) }
        }

    var localFrontendVersion: String
        get() = getInstance().getString(FRONTEND_LOCAL_VER, "")!!
        set(value) {
            getInstance().edit { putString(FRONTEND_LOCAL_VER, value) }
        }

    var localBackendVersion: String
        get() = getInstance().getString(BACKEND_LOCAL_VER, "")!!
        set(value) {
            getInstance().edit { putString(BACKEND_LOCAL_VER, value) }
        }

    var legacyNodeMigrated: Boolean
        get() = getInstance().getBoolean(LEGACY_NODE_MIGRATED, false)
        set(value) {
            check(getInstance().edit().putBoolean(LEGACY_NODE_MIGRATED, value).commit())
        }
}
