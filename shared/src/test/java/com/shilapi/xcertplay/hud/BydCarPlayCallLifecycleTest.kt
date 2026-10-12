package com.shilapi.xcertplay.hud

import android.content.Context
import android.content.ContextWrapper
import android.content.SharedPreferences
import com.shilapi.xcertplay.iap2.message.Iap2Messages
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [29], manifest = Config.NONE)
class BydCarPlayCallLifecycleTest {
    private val rejectingContext = object : ContextWrapper(null) {
        override fun getApplicationContext(): Context = error("BYD context accessed")
        override fun getSharedPreferences(name: String, mode: Int): SharedPreferences = error("BYD preferences accessed")
    }
    private fun field(name: String) = BydCarPlayCall::class.java.getDeclaredField(name).apply { isAccessible = true }
    private fun call(status: Int, id: String) = Iap2Messages.buildRaw(CarPlayCallState.CALL_STATE_UPDATE) {
        u8(2, status); string(4, id); string(1, "Caller")
    }
    private fun assertInert() {
        assertNull(BydCarPlayCall.current())
        for (name in listOf("context", "shown", "watcherToken", "retry", "retryCard")) assertNull(name, field(name).get(BydCarPlayCall))
        for (name in listOf("watcherRunning", "cleanupPending", "prepared", "sessionActive")) assertEquals(name, false, field(name).get(BydCarPlayCall))
        for (name in listOf("shell", "writer")) {
            assertFalse("$name initialized", (field(name + "\$delegate").get(BydCarPlayCall) as Lazy<*>).isInitialized())
        }
    }

    @Test fun disabledLifecycleNeverAttachesContextOrAllocatesCallWorkers() {
        BydCarPlayCall.attach(rejectingContext)
        repeat(3) {
            BydCarPlayCall.sessionStarted()
            BydCarPlayCall.settingChanged(true)
            BydCarPlayCall.onFrame(call(2, "ringing"))
            BydCarPlayCall.onFrame(call(4, "ringing"))
            BydCarPlayCall.onFrame(call(0, "ringing"))
            BydCarPlayCall.settingChanged(false)
            BydCarPlayCall.end()
        }
        assertInert()
    }

    @Test fun forgedEnabledPreferenceCannotPrepareLaunchOrRetryAWatcher() {
        val app = RuntimeEnvironment.getApplication()
        val prefs = app.getSharedPreferences("diplay_byd_outputs", Context.MODE_PRIVATE)
        prefs.edit().putBoolean("carplay_calls", true).commit()
        try {
            BydCarPlayCall.attach(app)
            BydCarPlayCall.sessionStarted()
            BydCarPlayCall.onFrame(call(2, "first"))
            BydCarPlayCall.onFrame(call(4, "first"))
            BydCarPlayCall.end()
            BydCarPlayCall.onFrame(call(2, "second"))
            BydCarPlayCall.settingChanged(true)
            assertInert()
        } finally {
            prefs.edit().clear().commit()
        }
    }

    @Test fun directCleanupAndRetryEntryPointsStayInertBeforeReadingContext() {
        for (name in listOf("arm", "disarm")) {
            BydCarPlayCall::class.java.getDeclaredMethod(name, Context::class.java)
                .apply { isAccessible = true }.invoke(BydCarPlayCall, rejectingContext)
        }
        for (name in listOf("apply", "scheduleRetry")) {
            BydCarPlayCall::class.java.getDeclaredMethod(name, Context::class.java, CarPlayCallCard::class.java)
                .apply { isAccessible = true }.invoke(BydCarPlayCall, rejectingContext,
                    CarPlayCallCard(CarPlayCallCard.Phase.ACTIVE, "Caller"))
        }
        assertInert()
    }

    @Test fun independentCallParserStillTracksTheIphonesCalls() {
        val state = CarPlayCallState { 1000L }
        assertTrue(state.accept(call(2, "one")))
        assertEquals(CarPlayCallCard.Phase.RINGING, state.current()?.phase)
        assertTrue(state.accept(call(4, "one")))
        assertEquals(CarPlayCallCard(CarPlayCallCard.Phase.ACTIVE, "Caller", 1000L), state.current())
        assertTrue(state.accept(call(0, "one")))
        assertNull(state.current())
        assertInert()
    }
}
