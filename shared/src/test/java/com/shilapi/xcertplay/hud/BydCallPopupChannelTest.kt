package com.shilapi.xcertplay.hud

import android.content.Context
import android.content.ContextWrapper
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** The live shell adapter was removed; the independent pure lease tests retain state-machine coverage. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [32], manifest = Config.NONE)
class BydCallPopupChannelTest {
    private fun channel(): BydCallPopupChannel = BydCallPopupChannel.prepare("/data/app/base.apk") {
        error("Disabled adapter must not launch a helper or send shell commands")
    }

    @Test fun absentStartReplyCannotTriggerStartOrCompensatingWrites() {
        val channel = channel()
        BydCallPopupGuard.runSession(channel, { error("session queried") }, { error("heartbeat") }, { error("report") })
        assertEquals("CANCELLED", channel.exchange("START"))
        channel.close()
    }

    @Test fun absentStopReplyCannotScheduleRecoveryOrClaimRestoration() {
        val channel = channel()
        BydCallPopupGuard.runSession(channel, { true }, {}, { error("restoration claimed") })
        assertEquals("CANCELLED", channel.exchange("STOP"))
        channel.close()
    }

    @Test fun cancelledBeforeStartDoesNotSendEvenACleanupCommand() {
        val channel = channel()
        repeat(3) {
            BydCallPopupGuard.runSession(channel, { false }, { error("heartbeat") }, { error("report") })
            channel.close()
        }
        assertEquals("CANCELLED", channel.exchange("PING"))
    }

    @Test fun disabledForegroundOwnerNeverStartsOrMutatesThePreparedChannel() {
        val context = object : ContextWrapper(null) {
            override fun getApplicationContext(): Context = error("vendor context accessed")
        }
        val owner = Any()
        repeat(3) {
            BydCallPopupGuard.update(context, owner, true) { error("connection callback") }
            BydCallPopupGuard.runPrepared(Long.MAX_VALUE, channel())
            BydCallPopupGuard.update(context, owner, false) { error("connection callback") }
        }
        assertEquals("OFF", BydCallPopupGuard.status)
    }

    @Test fun malformedInputsNeverReachAShellOrRestoreHardware() {
        val channel = BydCallPopupChannel.prepare("' malformed path") { error("shell invoked") }
        for (command in listOf("START", "PING", "STOP", "invalid RESTORED", "")) {
            assertEquals("CANCELLED", channel.exchange(command))
        }
        channel.close()
    }
}
