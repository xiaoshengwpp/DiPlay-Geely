package com.shilapi.xcertplay.hud

import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.util.concurrent.atomic.AtomicInteger

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [29], manifest = Config.NONE)
class BydClusterMapPauseTest {
    @Test fun geelyProfileDoesNotStartTheTickerWithOldPreferencesEnabled() {
        val context = RuntimeEnvironment.getApplication()
        val reads = AtomicInteger()
        val callbacks = AtomicInteger()
        BydClusterMapPause.readMode = { reads.incrementAndGet(); null }
        BydOutputSettings.setClusterStreamPause(context, true)
        BydClusterMapPause.clusterMapShown = true
        BydClusterMapPause.streamControl = { callbacks.incrementAndGet() }
        try {
            repeat(3) { BydClusterMapPause.initialize(context) }
            Thread.sleep(1_100) // The legacy ticker's first interval is 1 second.
            assertFalse(BydOutputSettings.clusterStreamPause(context))
            assertEquals(0, reads.get())
            assertEquals(0, callbacks.get())
        } finally {
            BydClusterMapPause.streamControl = null
            BydClusterMapPause.clusterMapShown = false
        }
    }
}
