package com.shilapi.xcertplay.hud

import android.content.Context
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [29], manifest = Config.NONE)
class BydClusterMapPauseTest {
    @Test fun forgedPauseAndModeFollowingCannotStartReadsOrCallbacks() {
        val app = RuntimeEnvironment.getApplication()
        val prefs = app.getSharedPreferences("diplay_byd_outputs", Context.MODE_PRIVATE)
        prefs.edit().putBoolean("cluster_stream_pause", true).commit()
        val original = BydClusterMapPause.readMode
        BydClusterMapPause.readMode = { error("No vendor mode read is authorized") }
        BydClusterMapPause.streamControl = { error("No vendor stream callback is authorized") }
        BydClusterMapPause.onNaviMode = { error("No vendor mode callback is authorized") }
        BydClusterMapPause.clusterMapShown = true
        try {
            repeat(3) {
                BydClusterMapPause.initialize(app)
                BydClusterMapPause.javaClass.getDeclaredMethod("tick").apply { isAccessible = true }.invoke(BydClusterMapPause)
            }
            assertFalse(BydOutputSettings.clusterStreamPause(app))
            assertNull(BydClusterMapPause.lastNaviMode)
        } finally {
            prefs.edit().clear().commit()
            BydClusterMapPause.readMode = original
            BydClusterMapPause.streamControl = null
            BydClusterMapPause.onNaviMode = null
            BydClusterMapPause.clusterMapShown = false
        }
    }
}
