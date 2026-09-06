package ano.subcase.util

import ano.subcase.BuildConfig
import com.google.firebase.crashlytics.FirebaseCrashlytics
import timber.log.Timber

// update crashlytics only when not debug and allow crash report
object CrashReporter {
    private val crashlytics by lazy { FirebaseCrashlytics.getInstance() }

    private val isEnabled: Boolean
        get() = !BuildConfig.DEBUG && ConfigStore.isAllowCrashReport

    fun initialize() {
        crashlytics.setCrashlyticsCollectionEnabled(isEnabled)
        Timber.d("CrashReporter: collection enabled=%s", isEnabled)
    }

    fun setReportingEnabled(enabled: Boolean) {
        ConfigStore.isAllowCrashReport = enabled
        initialize()
    }

    fun recordException(error: Throwable) {
        Timber.e(error)
        if (isEnabled) {
            crashlytics.recordException(error)
        }
    }

    fun log(message: String) {
        if (BuildConfig.DEBUG) {
            Timber.d("CrashLog: $message")
        } else if (isEnabled) {
            crashlytics.log(message)
        }
    }
    
    fun setCustomKey(key: String, value: String) {
        if (isEnabled) {
            crashlytics.setCustomKey(key, value)
        }
    }
    
    fun setCustomKey(key: String, value: Boolean) {
        if (isEnabled) {
            crashlytics.setCustomKey(key, value)
        }
    }
    
    fun setCustomKey(key: String, value: Int) {
        if (isEnabled) {
            crashlytics.setCustomKey(key, value)
        }
    }
}