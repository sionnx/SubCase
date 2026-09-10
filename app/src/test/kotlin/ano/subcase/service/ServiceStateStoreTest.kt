package ano.subcase.service

import android.service.quicksettings.Tile
import ano.subcase.R
import org.junit.Assert.*
import org.junit.Test

class ServiceStateStoreTest {
    @Test fun duplicateRequestsAreCoalescedUntilServiceCompletes() {
        val store = ServiceStateStore()
        var starts = 0
        var stops = 0
        repeat(2) { store.start({ starts++ }, { throw it }) }
        store.stop({ stops++; true }, { throw it })
        assertEquals(1, starts)
        assertEquals(0, stops)
        assertEquals(ServicePhase.Starting, store.state.value.phase)
        store.running()
        repeat(2) { store.stop({ stops++; true }, { throw it }) }
        assertEquals(1, stops)
        assertEquals(ServicePhase.Stopping, store.state.value.phase)
        store.stopped()
        assertEquals(SubStoreServiceState(), store.state.value)
    }

    @Test fun failedStartCanBeRetriedAndClearsError() {
        val store = ServiceStateStore()
        val failure = IllegalStateException("start rejected")
        var reported: Exception? = null
        store.start({ throw failure }, { reported = it })
        assertSame(failure, reported)
        assertEquals(ServiceFailure.Startup, store.state.value.failure)
        assertEquals(ServicePhase.Stopped, store.state.value.phase)
        store.start({}, { throw it })
        assertEquals(SubStoreServiceState(ServicePhase.Starting), store.state.value)
    }

    @Test fun engineFailureSurvivesDestroyAndAllowsRetry() {
        val store = ServiceStateStore()
        store.running()
        store.stopped(ServiceFailure.Runtime)
        store.stopped()
        assertEquals(ServiceFailure.Runtime, store.state.value.failure)
        store.start({}, { throw it })
        store.running()
        assertEquals(SubStoreServiceState(ServicePhase.Running), store.state.value)
    }

    @Test fun missingServiceCompletesStopAndRejectedStopRemainsRetryable() {
        val store = ServiceStateStore()
        store.running()
        store.stop({ false }, { throw it })
        assertEquals(ServicePhase.Stopped, store.state.value.phase)
        store.running()
        var reported = false
        store.stop({ throw IllegalStateException("stop rejected") }, { reported = true })
        assertTrue(reported)
        assertTrue(store.state.value.isRunning)
        store.stop({ true }, { throw it })
        assertEquals(ServicePhase.Stopping, store.state.value.phase)
    }

    @Test fun tileShowsActualStateAndFailureRemainsClickable() {
        assertEquals(Tile.STATE_INACTIVE, SubStoreServiceState().tileState())
        assertEquals(Tile.STATE_ACTIVE, SubStoreServiceState(ServicePhase.Running).tileState())
        for (phase in listOf(ServicePhase.Starting, ServicePhase.Stopping)) {
            assertEquals(Tile.STATE_UNAVAILABLE, SubStoreServiceState(phase).tileState())
        }
        for ((failure, description) in listOf(
            ServiceFailure.Startup to R.string.tile_start_failed,
            ServiceFailure.Runtime to R.string.tile_runtime_failed,
            ServiceFailure.Shutdown to R.string.tile_stop_failed,
        )) {
            val state = SubStoreServiceState(failure = failure)
            assertEquals(Tile.STATE_INACTIVE, state.tileState())
            assertEquals(description, state.descriptionResource())
        }
        assertEquals(ServicePhase.Stopped, ServiceStateStore().state.value.phase)
    }
}
