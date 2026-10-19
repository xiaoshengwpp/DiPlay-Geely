package com.shilapi.xcertplay.network

import android.content.Context
import android.content.ContextWrapper
import com.shilapi.xcertplay.orchestration.ManualHotspotSecurity
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], manifest = Config.NONE)
class CarHotspotConfigWriteTest {
    @Test fun everySdkAndSecurityModeRejectsWithoutAccessingContextOrCallbacks() {
        val context = ContextWrapper(null)
        for (sdk in listOf(25, 28, 29, 30, 33, 35)) {
            assertFalse(CarHotspotConfigWrite.supported(sdk))
            for (security in ManualHotspotSecurity.entries) {
                val outcome = CarHotspotConfigWrite.write(context,
                    HotspotCredentials("test-only", "test-only-password", security),
                    isCancelled = { error("Unexpected work callback") }, sdk = sdk,
                    log = { error("Unexpected diagnostic callback") })
                assertEquals(CarHotspotConfigWrite.Result.UNSUPPORTED_SDK, outcome.result)
                assertNull(outcome.ssid)
            }
        }
    }

    @Test fun staleOptInNeverEnablesConfigurationWrites() {
        val context = RuntimeEnvironment.getApplication()
        val prefs = context.getSharedPreferences("diplay_car_hotspot", Context.MODE_PRIVATE)
        try {
            prefs.edit().putBoolean("config_write", true).commit()
            assertFalse(CarHotspotSettings.configWrite(context))
            CarHotspotSettings.setConfigWrite(context, true)
            assertFalse(CarHotspotSettings.configWrite(context))
        } finally {
            prefs.edit().clear().commit()
        }
    }
}
