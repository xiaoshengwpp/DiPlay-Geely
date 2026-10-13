package com.shilapi.xcertplay

import org.junit.Assert.*
import org.junit.Test

class CarPlayMediaInputDeduplicatorTest {
    private val dedup = CarPlayMediaInputDeduplicator()
    private val owner = Any()

    @Test fun anyWinningRouteSuppressesItsOtherRouteEchoes() {
        for (first in CarPlayMediaInputRoute.values()) {
            dedup.clear()
            assertTrue(dedup.shouldSend(owner, 4, first, 1000))
            dedup.record(owner, 4, first, 1000)
            for (other in CarPlayMediaInputRoute.values()) {
                assertEquals(first == other, dedup.shouldSend(owner, 4, other, 1050))
            }
        }
    }

    @Test fun separateSameRouteTapsDifferentKeysAndExpiredEchoesWork() {
        dedup.record(owner, 4, CarPlayMediaInputRoute.FOREGROUND, 1000)
        assertTrue(dedup.shouldSend(owner, 4, CarPlayMediaInputRoute.FOREGROUND, 1050))
        assertTrue(dedup.shouldSend(owner, 5, CarPlayMediaInputRoute.GEELY_PRIVATE, 1050))
        assertTrue(dedup.shouldSend(owner, 4, CarPlayMediaInputRoute.MEDIA_SESSION, 1151))
    }

    @Test fun aSuppressedEchoDoesNotExtendTheWindow() {
        dedup.record(owner, 4, CarPlayMediaInputRoute.FOREGROUND, 1000)
        assertFalse(dedup.shouldSend(owner, 4, CarPlayMediaInputRoute.GEELY_PRIVATE, 1149))
        assertTrue(dedup.shouldSend(owner, 4, CarPlayMediaInputRoute.GEELY_PRIVATE, 1151))
    }

    @Test fun newSessionAndExplicitClearCannotInheritOldEchoes() {
        dedup.record(owner, 4, CarPlayMediaInputRoute.FOREGROUND, 1000)
        assertTrue(dedup.shouldSend(Any(), 4, CarPlayMediaInputRoute.MEDIA_SESSION, 1000))
        dedup.record(owner, 4, CarPlayMediaInputRoute.FOREGROUND, 1000)
        dedup.clear()
        assertTrue(dedup.shouldSend(owner, 4, CarPlayMediaInputRoute.MEDIA_SESSION, 1000))
    }
}
