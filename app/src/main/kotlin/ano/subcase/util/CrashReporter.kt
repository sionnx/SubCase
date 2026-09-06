package ano.subcase.util

import ano.subcase.BuildConfig
import com.google.firebase.crashlytics.FirebaseCrashlytics
import timber.log.Timber

// update crashlytics only when not debug and allow crash report
object CrashReporter {
    private val crashlytics: FirebaseCrashlytics? by lazy {
        if (BuildConfig.DEBUG) {
            null
        } else if (ConfigStore.isAllowCrashReport) {
            FirebaseCrashlytics.getInstance()
        } else {
            null
        }
    }
    
    fun initialize() {
        if (BuildConfig.DEBUG) {
            Timber.d("CrashReporter: Debug mode, Firebase Crashlytics disabled")
        } else if (ConfigStore.isAllowCrashReport) {
            crashlytics?.isCrashlyticsCollectionEnabled = true
            Timber.d("CrashReporter: Release mode, Firebase Crashlytics enabled")
        }
    }

    fun recordException(error: Throwable) {
        Timber.e(error)
        if (!BuildConfig.DEBUG && ConfigStore.isAllowCrashReport) {
            FirebaseCrashlytics.getInstance().recordException(error)
        }
    }

    fun log(message: String) {
        if (BuildConfig.DEBUG) {
            Timber.d("CrashLog: $message")
        } else if (ConfigStore.isAllowCrashReport) {
            crashlytics?.log(message)
        }
    }
    
    fun setCustomKey(key: String, value: String) {
        if (!BuildConfig.DEBUG) {
            crashlytics?.setCustomKey(key, value)
        }
    }
    
    fun setCustomKey(key: String, value: Boolean) {
        if (!BuildConfig.DEBUG) {
            crashlytics?.setCustomKey(key, value)
        }
    }
    
    fun setCustomKey(key: String, value: Int) {
        if (!BuildConfig.DEBUG) {
            crashlytics?.setCustomKey(key, value)
        }
    }
}