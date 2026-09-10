package ano.subcase.service

import android.graphics.drawable.Icon
import android.os.Build
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import androidx.core.graphics.drawable.toBitmap
import ano.subcase.R
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

internal fun SubStoreServiceState.tileState(): Int = when (phase) {
    ServicePhase.Running -> Tile.STATE_ACTIVE
    ServicePhase.Stopped -> Tile.STATE_INACTIVE
    ServicePhase.Starting, ServicePhase.Stopping -> Tile.STATE_UNAVAILABLE
}

internal fun SubStoreServiceState.descriptionResource(): Int = when (phase) {
    ServicePhase.Starting -> R.string.tile_starting
    ServicePhase.Stopping -> R.string.tile_stopping
    ServicePhase.Running -> R.string.tile_running
    ServicePhase.Stopped -> when (failure) {
        ServiceFailure.Startup -> R.string.tile_start_failed
        ServiceFailure.Runtime -> R.string.tile_runtime_failed
        ServiceFailure.Shutdown -> R.string.tile_stop_failed
        null -> R.string.tile_stopped
    }
}

class SubCaseTileService : TileService() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var listeningJob: Job? = null

    override fun onStartListening() {
        super.onStartListening()
        listeningJob?.cancel()
        render(SubStoreServiceController.state.value)
        listeningJob = scope.launch {
            SubStoreServiceController.state.collect { render(it) }
        }
    }

    override fun onStopListening() {
        listeningJob?.cancel()
        listeningJob = null
        super.onStopListening()
    }

    override fun onClick() {
        super.onClick()
        when (SubStoreServiceController.state.value.phase) {
            ServicePhase.Stopped -> SubStoreServiceController.start(this)
            ServicePhase.Running -> SubStoreServiceController.stop(this)
            ServicePhase.Starting, ServicePhase.Stopping -> Unit
        }
    }

    private fun render(status: SubStoreServiceState) {
        val tile = qsTile ?: return
        val description = getString(status.descriptionResource())
        // ColorOS caches resource icons across APK updates; render the current vector.
        tile.icon = Icon.createWithBitmap(
            checkNotNull(getDrawable(R.drawable.ic_subcase_tile_logo)).toBitmap()
        )
        tile.label = getString(R.string.app_name)
        tile.state = status.tileState()
        tile.contentDescription = "${tile.label}, $description"
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) tile.subtitle = description
        tile.updateTile()
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }
}
