package com.shilapi.xcertplay

import android.content.Context
import android.graphics.Bitmap
import android.os.Handler
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import com.shilapi.xcertplay.backup.SettingsBackupEndpoint
import com.shilapi.xcertplay.backup.SettingsBackupServer
import com.shilapi.xcertplay.host.R
import java.util.concurrent.ExecutorService
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mockito.mock
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.util.ReflectionHelpers

/** Latest host/UI improvements retain the Geely evidence, consent and session boundaries. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [29], qualifiers = "en", manifest = Config.NONE)
class GeelyLatestHostSettingsIntegrationTest {
    @Test fun sharedDiagnosticReportRetainsGeelyCapabilitiesAndIncludesTheCrashRecord() {
        val activity = Robolectric.buildActivity(DiPlayActivity::class.java).get()
        val report = ReflectionHelpers.callInstanceMethod<String>(activity, "diagnosticReportText",
            ReflectionHelpers.ClassParameter.from(Context::class.java, activity.applicationContext))
        assertTrue(report.contains("Geely capability diagnostics (read-only; no hardware support claim)"))
        assertTrue(report.contains("manual input opt-in="))
        assertTrue(report.contains("Siri microphone preference="))
        assertTrue(report.contains("source=Android_DisplayManager readOnly=true screenRoles=unconfirmed"))
        assertTrue(report.contains("automatic grants remain unavailable in this product"))
        assertTrue(report.contains("Last crash recorded by DiPlay"))
    }

    @Test fun backupAndReportPagesBothDiscloseLocalHttpAndCodeAccess() {
        val activity = Robolectric.buildActivity(DiPlayActivity::class.java).get()
        activity.setTheme(android.R.style.Theme_Material_NoActionBar)
        activity.backupAddressCandidates = { listOf("127.0.0.1") }
        val server = mock(SettingsBackupServer::class.java)
        ReflectionHelpers.setField(activity, "backupServer", server)
        ReflectionHelpers.setField(activity, "backupEndpoint", SettingsBackupEndpoint(1234, "test-only", "123456"))
        try {
            for (category in listOf(SettingsCategory.ABOUT, SettingsCategory.DIAGNOSTICS)) {
                ReflectionHelpers.setField(activity, "backupOpenedFrom", category)
                ReflectionHelpers.setField(activity, "backupReportEnabled", category == SettingsCategory.DIAGNOSTICS)
                val page = LinearLayout(activity)
                ReflectionHelpers.callInstanceMethod<Unit>(activity, "backup",
                    ReflectionHelpers.ClassParameter.from(LinearLayout::class.java, page))
                val text = descendants(page).filterIsInstance<TextView>().map { it.text.toString() }.toList()
                assertTrue(text.contains(activity.getString(R.string.backup_access_warning)))
                val title = if (category == SettingsCategory.DIAGNOSTICS)
                    R.string.settings_report_on_phone else R.string.backup_title
                assertEquals(activity.getString(title), (page.getChildAt(0) as TextView).text.toString())
            }
        } finally {
            ReflectionHelpers.callInstanceMethod<Unit>(activity, "stopBackupServer")
        }
    }

    @Test fun endingTheSessionClearsTheHeldPictureAndPendingRefreshes() {
        val activity = Robolectric.buildActivity(CarPlayHostActivity::class.java).get()
        val hold = ImageView(activity).apply {
            setImageBitmap(Bitmap.createBitmap(2, 2, Bitmap.Config.ARGB_8888))
            visibility = View.VISIBLE
        }
        ReflectionHelpers.setField(activity, "lastPicture", Bitmap.createBitmap(2, 2, Bitmap.Config.ARGB_8888))
        ReflectionHelpers.setField(activity, "pictureHold", hold)
        val handler = ReflectionHelpers.getField<Handler>(activity, "mainHandler")
        val retry = ReflectionHelpers.getField<Runnable>(activity, "pictureRetry")
        val tick = ReflectionHelpers.getField<Runnable>(activity, "pictureHoldTick")
        handler.postDelayed(retry, 1_000)
        handler.postDelayed(tick, 1_000)
        try {
            ReflectionHelpers.callInstanceMethod<Unit>(activity, "discardLastPicture")
            assertNull(ReflectionHelpers.getField<Bitmap?>(activity, "lastPicture"))
            assertEquals(View.GONE, hold.visibility)
            assertNull(hold.drawable)
            assertFalse(handler.hasCallbacks(retry))
            assertFalse(handler.hasCallbacks(tick))
        } finally {
            handler.removeCallbacksAndMessages(null)
            ReflectionHelpers.getField<ExecutorService>(activity, "teardownExecutor").shutdownNow()
            ReflectionHelpers.getField<ExecutorService>(activity, "airPlayCommandExecutor").shutdownNow()
        }
    }

    private fun descendants(view: View): Sequence<View> = sequence {
        yield(view)
        if (view is ViewGroup) for (index in 0 until view.childCount) yieldAll(descendants(view.getChildAt(index)))
    }
}
