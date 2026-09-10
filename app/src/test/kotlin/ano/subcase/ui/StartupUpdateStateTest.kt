package ano.subcase.ui

import ano.subcase.model.AppRelease
import org.junit.Assert.assertEquals
import org.junit.Test

class StartupUpdateStateTest {
    private val release = AppRelease(
        tagName = "v0.5.0",
        versionName = "0.5.0",
        versionCode = 99,
        releaseNotes = "说明",
        apkName = "SubCase-0.5.0-99-release.apk",
        downloadUrl = "https://example.com/SubCase-0.5.0-99-release.apk",
    )

    @Test
    fun `app result gates all startup dialogs`() {
        val state = StartupUpdateUiState()
        assertEquals(StartupDialog.NONE, resolveStartupDialog(state))
    }

    @Test
    fun `pending app update shows app dialog`() {
        val state = StartupUpdateUiState(
            appCheckFinished = true,
            appRelease = release,
        )
        assertEquals(StartupDialog.APP_UPDATE, resolveStartupDialog(state))
    }

    @Test
    fun `skipping app update dismisses startup dialogs`() {
        val state = StartupUpdateUiState(
            appCheckFinished = true,
            appRelease = release,
            appDecision = AppUpdateDecision.SKIPPED,
        )
        assertEquals(StartupDialog.NONE, resolveStartupDialog(state))
    }

    @Test
    fun `updating app keeps app dialog`() {
        val state = StartupUpdateUiState(
            appCheckFinished = true,
            appRelease = release,
            appDecision = AppUpdateDecision.UPDATING,
        )
        assertEquals(StartupDialog.APP_UPDATE, resolveStartupDialog(state))
    }

    @Test
    fun `latest app shows no startup dialog`() {
        val state = StartupUpdateUiState(
            appCheckFinished = true,
            appDecision = AppUpdateDecision.UNAVAILABLE,
        )
        assertEquals(StartupDialog.NONE, resolveStartupDialog(state))
    }

    @Test
    fun `version code comparison accepts upgrades only`() {
        assertEquals(true, isAppUpdateAvailable(remoteVersionCode = 58, currentVersionCode = 57))
        assertEquals(false, isAppUpdateAvailable(remoteVersionCode = 57, currentVersionCode = 57))
        assertEquals(false, isAppUpdateAvailable(remoteVersionCode = 52, currentVersionCode = 57))
    }

    @Test
    fun `installer launch permanently suppresses startup dialogs`() {
        val state = StartupUpdateUiState(
            appCheckFinished = true,
            appRelease = release,
            appDecision = AppUpdateDecision.INSTALLER_LAUNCHED,
        )
        assertEquals(StartupDialog.NONE, resolveStartupDialog(state))
    }
}
