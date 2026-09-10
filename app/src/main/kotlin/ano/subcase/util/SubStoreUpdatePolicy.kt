package ano.subcase.util

import androidx.annotation.StringRes
import ano.subcase.R
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withTimeoutOrNull
import timber.log.Timber

enum class SubStoreUpdatePolicy(
    @StringRes val labelRes: Int,
) {
    MANUAL(R.string.substore_policy_manual),
    ON_START(R.string.substore_policy_on_start),
    EVERY_3H(R.string.substore_policy_every_3h),
    EVERY_6H(R.string.substore_policy_every_6h),
    EVERY_12H(R.string.substore_policy_every_12h),
    EVERY_24H(R.string.substore_policy_every_24h),
    EVERY_48H(R.string.substore_policy_every_48h),
    EVERY_7D(R.string.substore_policy_every_7d);

    val intervalMillis: Long?
        get() = when (this) {
            MANUAL, ON_START -> null
            EVERY_3H -> 3 * HOUR_MS
            EVERY_6H -> 6 * HOUR_MS
            EVERY_12H -> 12 * HOUR_MS
            EVERY_24H -> 24 * HOUR_MS
            EVERY_48H -> 48 * HOUR_MS
            EVERY_7D -> 7 * 24 * HOUR_MS
        }

    companion object {
        fun fromStored(value: String?): SubStoreUpdatePolicy {
            if (value.isNullOrBlank()) return ON_START
            return entries.find { it.name == value } ?: run {
                Timber.w("未知 SubStore 更新策略 %s，回退为启动时更新", value)
                ON_START
            }
        }
    }
}

private const val HOUR_MS = 60L * 60L * 1000L

internal fun millisUntilNextSubStoreVersionCheck(
    nowMillis: Long,
    lastSubStoreRemoteVersionCheckAt: Long,
    intervalMillis: Long,
): Long {
    require(intervalMillis > 0L) { "间隔必须为正" }
    if (lastSubStoreRemoteVersionCheckAt <= 0L) return 0L
    return (lastSubStoreRemoteVersionCheckAt + intervalMillis - nowMillis).coerceAtLeast(0L)
}

object SubStoreUpdateScheduler {
    private val policyChangedFlow = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val policyChanged = policyChangedFlow.asSharedFlow()

    fun notifyPolicyChanged() {
        policyChangedFlow.tryEmit(Unit)
    }
}

suspend fun runSubStoreUpdateLoop() {
    while (currentCoroutineContext().isActive) {
        val intervalMillis = ConfigStore.subStoreUpdatePolicy.intervalMillis
        if (intervalMillis == null) {
            SubStoreUpdateScheduler.policyChanged.first()
            continue
        }
        val waitMs = millisUntilNextSubStoreVersionCheck(
            nowMillis = System.currentTimeMillis(),
            lastSubStoreRemoteVersionCheckAt = ConfigStore.lastSubStoreRemoteVersionCheckAt,
            intervalMillis = intervalMillis,
        )
        if (waitMs > 0L) {
            val policyChanged = withTimeoutOrNull(waitMs) {
                SubStoreUpdateScheduler.policyChanged.first()
            }
            if (policyChanged != null) continue
        }
        SubStore.checkAndUpdate(showToast = false)
            .onFailure { error -> Timber.e(error, "前台服务周期更新 SubStore 失败") }
    }
}
