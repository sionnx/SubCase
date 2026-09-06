package ano.subcase.ui

import android.os.SystemClock
import androidx.lifecycle.ViewModelProvider
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import ano.subcase.debug.DebugPort
import ano.subcase.util.currentServerConfig
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.net.InetSocketAddress
import java.net.ServerSocket

@RunWith(AndroidJUnit4::class)
class DebugLifecycleTest {
    @Test
    fun recreationRetainsOccupancyAndActivityFinishReleasesIt() {
        val config = currentServerConfig()
        lateinit var original: DebugViewModel
        val scenario = ActivityScenario.launch(MainActivity::class.java)
        try {
            scenario.onActivity { activity ->
                original = ViewModelProvider(activity)[DebugViewModel::class.java]
                original.setOccupied(DebugPort.FRONTEND, true)
                original.setOccupied(DebugPort.BACKEND, true)
            }
            waitUntil { original.ports.value.values.all { it.occupied != null && !it.busy } }
            scenario.recreate()
            scenario.onActivity { activity ->
                assertSame(original, ViewModelProvider(activity)[DebugViewModel::class.java])
                assertTrue(original.ports.value.values.all { it.occupied != null })
            }
        } finally {
            scenario.close()
        }
        waitUntil {
            try {
                ServerSocket().use { a ->
                    a.bind(InetSocketAddress(config.frontend.host, config.frontend.port))
                    ServerSocket().use { b ->
                        b.bind(InetSocketAddress(config.backend.host, config.backend.port))
                    }
                }
                true
            } catch (_: java.net.BindException) { false }
        }
        ActivityScenario.launch(MainActivity::class.java).use { next ->
            next.onActivity { activity ->
                val model = ViewModelProvider(activity)[DebugViewModel::class.java]
                assertNotSame(original, model)
                assertTrue(model.ports.value.values.all { it.occupied == null && !it.busy })
            }
        }
    }

    private fun waitUntil(condition: () -> Boolean) {
        val deadline = SystemClock.uptimeMillis() + 10_000
        while (!condition()) {
            check(SystemClock.uptimeMillis() < deadline) { "Timed out waiting for debug socket lifecycle" }
            SystemClock.sleep(50)
        }
    }
}
