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
class DiLink3ClusterRecoveryTest {
    @Test fun historicalRecoveryJournalsCannotTriggerStockCarWrites() {
        val app = RuntimeEnvironment.getApplication()
        val prefs = app.getSharedPreferences("diplay_dilink3_cluster", Context.MODE_PRIVATE)
        val route = app.getSharedPreferences("xcertplay_airplay", Context.MODE_PRIVATE)
        prefs.edit().putBoolean("restore_stock_mode", true).commit()
        try {
            for (forgedRoute in listOf(false, true)) {
                route.edit().putBoolean("adb_cluster_activity_enabled", forgedRoute)
                    .putBoolean("platform21_cluster_enabled", forgedRoute).commit()
                BydNavigationOutputs.onAppOpened(app)
                BydDiLink3ClusterOutput.restoreIfNeeded(app)
                BydDiLink3ClusterOutput.prepareDisplay(app) { error("Display probe must not run") }
                BydDiLink3ClusterOutput.setDesired(app, true, true)
                BydDiLink3ClusterOutput.setDesired(app, false, false)
                BydDiLink3ClusterOutput.javaClass.getDeclaredMethod("applyLatest")
                    .apply { isAccessible = true }.invoke(BydDiLink3ClusterOutput)
                assertFalse(BydDiLink3ClusterOutput.adbClusterRouteSelected(app))
                assertTrue(prefs.getBoolean("restore_stock_mode", false))
            }
            for (name in listOf("shell", "worker")) {
                val delegate = BydDiLink3ClusterOutput.javaClass.getDeclaredField(name + "\$delegate")
                    .apply { isAccessible = true }.get(BydDiLink3ClusterOutput) as Lazy<*>
                assertFalse(delegate.isInitialized())
            }
        } finally { prefs.edit().clear().commit(); route.edit().clear().commit() }
    }
}
