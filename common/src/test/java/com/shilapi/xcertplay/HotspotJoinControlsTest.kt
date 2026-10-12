package com.shilapi.xcertplay

import android.app.AlertDialog
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import com.shilapi.xcertplay.host.R
import com.shilapi.xcertplay.network.HotspotJoinRepair
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import android.os.Looper
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowAlertDialog

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], qualifiers = "en", manifest = Config.NONE)
class HotspotJoinControlsTest {
    private class Backend : HotspotJoinControls.Backend {
        val actions = mutableListOf<HotspotJoinRepair.Action>()
        var session = false
        var queued: (() -> Unit)? = null
        var deferred = false
        var cancelled = false
        override fun run(action: HotspotJoinRepair.Action, token: String?): HotspotJoinRepair.Result {
            actions += action
            return HotspotJoinRepair.Result(when (action) {
                HotspotJoinRepair.Action.CHECK -> HotspotJoinRepair.Code.READY
                HotspotJoinRepair.Action.APPLY -> HotspotJoinRepair.Code.APPLIED
                HotspotJoinRepair.Action.RESTORE -> HotspotJoinRepair.Code.RESTORED
            }, "12345678-1234-1234-1234-123456789abc", action == HotspotJoinRepair.Action.APPLY)
        }
        override fun execute(block: () -> Unit) { if (deferred) queued = block else block() }
        override fun cancel() { cancelled = true }
    }
    @Test fun geelyRenderingAndDirectActionsNeverStartPrivilegedWork() {
        val activity = Robolectric.buildActivity(DiPlayActivity::class.java).get()
        val backend = Backend()
        var beforeAction = 0
        val controls = HotspotJoinControls(activity, { false }, backend, { beforeAction++ })
        val root = controls.build()
        assertTrue(views(root).filterIsInstance<Button>().isEmpty())
        assertTrue(views(root).filterIsInstance<android.widget.TextView>().any {
            it.text == activity.getString(R.string.byd_integration_disabled_geely)
        })
        for (action in HotspotJoinRepair.Action.entries) {
            invokeAction(controls, "confirm", action)
            invokeAction(controls, "run", action)
        }
        assertEquals(0, beforeAction)
        assertTrue(backend.actions.isEmpty())
        assertNull(backend.queued)
        assertNull(ShadowAlertDialog.getLatestAlertDialog())
        controls.close()
    }
    private fun invokeAction(controls: HotspotJoinControls, method: String, action: HotspotJoinRepair.Action) {
        HotspotJoinControls::class.java.getDeclaredMethod(method, HotspotJoinRepair.Action::class.java)
            .apply { isAccessible = true }.invoke(controls, action)
    }
    private fun views(view: View): List<View> = listOf(view) + if (view is ViewGroup)
        (0 until view.childCount).flatMap { views(view.getChildAt(it)) } else emptyList()

    @Test fun closingBeforeTheWorkerStartsCancelsAndDoesNotRunPrivilegedWork() {
        val activity = Robolectric.buildActivity(DiPlayActivity::class.java).get()
        val backend = Backend().apply { deferred = true }
        val controls = HotspotJoinControls(activity, { false }, backend)
        controls.build()
        invokeAction(controls, "run", HotspotJoinRepair.Action.CHECK)
        controls.close()
        invokeAction(controls, "run", HotspotJoinRepair.Action.APPLY)
        assertNull(backend.queued)
        assertTrue(backend.cancelled)
        assertTrue(backend.actions.isEmpty())
    }

    @Test fun sessionChangesCannotAuthorizeAWrite() {
        val activity = Robolectric.buildActivity(DiPlayActivity::class.java).get()
        val backend = Backend()
        val controls = HotspotJoinControls(activity, { backend.session }, backend)
        controls.build()
        invokeAction(controls, "confirm", HotspotJoinRepair.Action.APPLY)
        backend.session = true
        invokeAction(controls, "run", HotspotJoinRepair.Action.APPLY)
        shadowOf(Looper.getMainLooper()).idle()
        assertNull(ShadowAlertDialog.getLatestAlertDialog())
        assertTrue(backend.actions.isEmpty())
        controls.close()
    }

    @Test fun forgedRollbackResultCannotStartRestoreOrCheck() {
        val activity = Robolectric.buildActivity(DiPlayActivity::class.java).get()
        val backend = Backend().apply { deferred = true }
        val controls = HotspotJoinControls(activity, { false }, backend)
        controls.build()
        HotspotJoinControls::class.java.getDeclaredField("result").apply { isAccessible = true }.set(controls,
            HotspotJoinRepair.Result(HotspotJoinRepair.Code.APPLIED, "12345678-1234-1234-1234-123456789abc", true))
        invokeAction(controls, "confirm", HotspotJoinRepair.Action.RESTORE)
        invokeAction(controls, "run", HotspotJoinRepair.Action.RESTORE)
        invokeAction(controls, "run", HotspotJoinRepair.Action.CHECK)
        assertTrue(backend.actions.isEmpty())
        assertNull(backend.queued)
        assertNull(ShadowAlertDialog.getLatestAlertDialog())
        controls.close()
    }
}
