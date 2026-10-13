package com.shilapi.xcertplay

import android.os.Bundle
import com.shilapi.xcertplay.backup.SettingsBackupEndpoint
import com.shilapi.xcertplay.backup.SettingsBackupServer
import java.net.HttpURLConnection
import java.net.URL
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.util.ReflectionHelpers

/** A settings-backup link/code never silently gains permission to disclose diagnostic logs. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [29], qualifiers = "en")
class GeelyReportSharingConsentTest {
    @Test fun reportAccessRequiresAnExplicitFreshPageAndOrdinaryReopenRevokesIt() {
        val lifecycle = Robolectric.buildActivity(DiPlayActivity::class.java).setup()
        val activity = lifecycle.get()
        activity.backupAddressCandidates = { listOf("127.0.0.1") }
        try {
            call(activity, "openBackup")
            val backup = endpoint(activity)
            assertFalse(ReflectionHelpers.getField<Boolean>(activity, "backupReportEnabled"))
            assertEquals(200, request(backup, "export").first)
            assertEquals(404, request(backup, "report").first)
            assertPlainPage(backup)
            assertEquals(200, enterCode(backup).first)
            assertEquals(404, request(backup, "report").first)

            // The explicit Diagnostics action closes the old server and issues fresh credentials.
            val oldServer = ReflectionHelpers.getField<SettingsBackupServer>(activity, "backupServer")
            call(activity, "openReportOnPhone")
            val reports = endpoint(activity)
            assertTrue(ReflectionHelpers.getField<Boolean>(activity, "backupReportEnabled"))
            assertNotSame(oldServer, ReflectionHelpers.getField<SettingsBackupServer>(activity, "backupServer"))
            assertNotEquals(backup.token, reports.token)
            assertEquals(404, rawRequest(reports, "/b/${backup.token}/report").first)
            assertTrue(request(reports, "").second.contains("var reportEnabled = true;"))
            val downloaded = request(reports, "report")
            assertEquals(200, downloaded.first)
            assertTrue(downloaded.second.contains("Geely capability diagnostics"))
            assertTrue(enterCode(reports).second.contains("#report"))

            call(activity, "stopBackupServer")
            assertFalse(ReflectionHelpers.getField<Boolean>(activity, "backupReportEnabled"))
            call(activity, "openBackup")
            val reopened = endpoint(activity)
            assertNotEquals(reports.token, reopened.token)
            assertEquals(404, rawRequest(reopened, "/b/${reports.token}/report").first)
            assertEquals(200, enterCode(reopened).first)
            assertEquals(404, request(reopened, "report").first)
            assertPlainPage(reopened)
        } finally {
            lifecycle.pause().stop().destroy()
        }
    }

    @Test fun leavingTheVisibleReportScreenRevokesItsServerAndIsNotSaved() {
        val lifecycle = Robolectric.buildActivity(DiPlayActivity::class.java).setup()
        val activity = lifecycle.get()
        activity.backupAddressCandidates = { listOf("127.0.0.1") }
        call(activity, "openReportOnPhone")
        assertTrue(ReflectionHelpers.getField<Boolean>(activity, "backupReportEnabled"))
        val state = Bundle()
        lifecycle.saveInstanceState(state).pause().stop()
        try {
            assertNull(ReflectionHelpers.getField<SettingsBackupServer?>(activity, "backupServer"))
            assertFalse(ReflectionHelpers.getField<Boolean>(activity, "backupReportEnabled"))
            assertEquals("settings", ReflectionHelpers.getField<String>(activity, "page"))
            assertEquals(SettingsCategory.DIAGNOSTICS, ReflectionHelpers.getField<SettingsCategory>(activity, "settingsCategory"))
            assertFalse(state.keySet().contains("backupReportEnabled"))
            assertFalse(state.keySet().contains("backup_report_enabled"))
        } finally {
            lifecycle.destroy()
        }
    }

    private fun assertPlainPage(endpoint: SettingsBackupEndpoint) {
        val html = request(endpoint, "").second
        assertTrue(html.contains("id=\"report\" hidden"))
        assertTrue(html.contains("var reportEnabled = false;"))
        assertTrue(html.contains("${'$'}(\"report\").hidden = !reportEnabled;"))
        assertFalse(html.contains("/*DIPLAY_REPORT_ENABLED*/"))
    }

    private fun endpoint(activity: DiPlayActivity): SettingsBackupEndpoint =
        ReflectionHelpers.getField(activity, "backupEndpoint")

    private fun call(activity: DiPlayActivity, method: String) =
        ReflectionHelpers.callInstanceMethod<Unit>(activity, method)

    private fun request(endpoint: SettingsBackupEndpoint, route: String) =
        rawRequest(endpoint, "/b/${endpoint.token}/$route")

    private fun enterCode(endpoint: SettingsBackupEndpoint) = rawRequest(endpoint, "/code", endpoint.code)

    private fun rawRequest(endpoint: SettingsBackupEndpoint, path: String, code: String? = null): Pair<Int, String> {
        val connection = URL("http://127.0.0.1:${endpoint.port}$path").openConnection() as HttpURLConnection
        connection.connectTimeout = 5_000
        connection.readTimeout = 5_000
        try {
            if (code != null) {
                connection.requestMethod = "POST"
                connection.doOutput = true
                val bytes = code.toByteArray(Charsets.UTF_8)
                connection.setFixedLengthStreamingMode(bytes.size)
                connection.outputStream.use { it.write(bytes) }
            }
            val status = connection.responseCode
            val body = (if (status < 400) connection.inputStream else connection.errorStream)
                ?.use { it.readBytes().toString(Charsets.UTF_8) }.orEmpty()
            return status to body
        } finally {
            connection.disconnect()
        }
    }
}
