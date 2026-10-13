package com.shilapi.xcertplay.orchestration

import com.shilapi.xcertplay.iap2.message.Iap2Messages
import org.junit.After
import org.junit.Assert.*
import org.junit.Test

class CarPlayCallGuardTest {
    private val owner = Any()
    private val session = Any()
    @After fun cleanUp() { CarPlayCallGuard.end(owner) }

    private fun frame(status: Int, id: String = "a") = Iap2Messages.buildRaw(0x4155) {
        u8(2, status)
        string(4, id)
    }

    @Test fun incomingOutgoingActiveAndHeldCallsBlockUntilAllCallsEnd() {
        CarPlayCallGuard.start(owner, session)
        for (status in 1..5) {
            CarPlayCallGuard.accept(owner, session, frame(status))
            assertTrue("status=$status", CarPlayCallGuard.inCall())
        }
        CarPlayCallGuard.accept(owner, session, frame(2, "waiting"))
        CarPlayCallGuard.accept(owner, session, frame(0))
        assertTrue(CarPlayCallGuard.inCall())
        CarPlayCallGuard.accept(owner, session, frame(6, "waiting"))
        assertFalse(CarPlayCallGuard.inCall())
    }

    @Test fun replacementAndDisconnectClearStateAndRejectStaleFramesAndEnds() {
        CarPlayCallGuard.start(owner, session)
        CarPlayCallGuard.accept(owner, session, frame(4))
        CarPlayCallGuard.start(owner, session)
        assertTrue(CarPlayCallGuard.inCall())
        val replacement = Any()
        CarPlayCallGuard.start(owner, replacement)
        assertFalse(CarPlayCallGuard.inCall())
        CarPlayCallGuard.accept(owner, session, frame(2))
        assertFalse(CarPlayCallGuard.inCall())
        CarPlayCallGuard.accept(owner, replacement, frame(5))
        CarPlayCallGuard.end(owner, session)
        assertTrue(CarPlayCallGuard.inCall())
        CarPlayCallGuard.end(owner, replacement)
        CarPlayCallGuard.accept(owner, replacement, frame(4))
        assertFalse(CarPlayCallGuard.inCall())
    }

    @Test fun oldControllerCannotRepopulateOrClearNewControllerCallState() {
        val oldOwner = Any()
        CarPlayCallGuard.start(oldOwner, session)
        CarPlayCallGuard.start(owner, session)
        CarPlayCallGuard.accept(oldOwner, session, frame(2))
        assertFalse(CarPlayCallGuard.inCall())
        CarPlayCallGuard.accept(owner, session, frame(4))
        CarPlayCallGuard.end(oldOwner)
        assertTrue(CarPlayCallGuard.inCall())
    }

    @Test fun unrelatedOrMalformedMessagesDoNotCreateCalls() {
        CarPlayCallGuard.start(owner, session)
        CarPlayCallGuard.accept(owner, session, Iap2Messages.buildRaw(0x4155) { string(4, "a") })
        CarPlayCallGuard.accept(owner, session, Iap2Messages.buildRaw(0x5001) { u8(2, 4) })
        assertFalse(CarPlayCallGuard.inCall())
    }
}
