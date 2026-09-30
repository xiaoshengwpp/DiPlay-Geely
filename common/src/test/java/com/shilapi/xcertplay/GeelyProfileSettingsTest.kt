package com.shilapi.xcertplay

import android.content.Context
import com.shilapi.xcertplay.hud.BydNavigationOutputs
import com.shilapi.xcertplay.hud.BydOutputSettings
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [29], manifest = Config.NONE)
class GeelyProfileSettingsTest {
    private val context get() = RuntimeEnvironment.getApplication()

    @Test fun oldEnabledPreferencesCannotReactivateBydFeatures() {
        BydOutputSettings.setEnabled(context, true)
        BydOutputSettings.setClusterStreamPause(context, true)
        BydOutputSettings.setBatteryToIphone(context, true)
        AirPlayPersistence.saveClusterMapEnabled(context, true)
        assertFalse(BydOutputSettings.enabled(context))
        assertFalse(BydOutputSettings.available(context))
        assertFalse(BydOutputSettings.clusterStreamPause(context))
        assertFalse(BydOutputSettings.batteryToIphone(context))
        assertFalse(AirPlayPersistence.loadClusterMapEnabled(context))
        assertFalse(DiLink51ClusterLayout.automatic(context))
        assertNull(BydNavigationOutputs.batteryStatus(context).snapshot())
    }

    @Test fun audioDefaultsAndSavedChoicesRemainUnchanged() {
        val prefs = context.getSharedPreferences("xcertplay_airplay", Context.MODE_PRIVATE)
        prefs.edit().clear().commit()
        assertFalse(AirPlayPersistence.loadAdvancedAudioChannelMapping(context))
        assertEquals(14, AirPlayPersistence.loadNavigationStreamType(context))
        for (advanced in listOf(false, true)) for (stream in listOf(3, 14)) {
            AirPlayPersistence.saveAdvancedAudioChannelMapping(context, advanced)
            AirPlayPersistence.saveNavigationStreamType(context, stream)
            BydNavigationOutputs.onAppOpened(context)
            BydNavigationOutputs.start(context)
            BydNavigationOutputs.endNow()
            assertEquals(advanced, AirPlayPersistence.loadAdvancedAudioChannelMapping(context))
            assertEquals(stream, AirPlayPersistence.loadNavigationStreamType(context))
            assertEquals(advanced, prefs.getBoolean("advanced_audio_channel_mapping", !advanced))
            assertFalse(prefs.contains("advanced_audio_channel_mapping_v2"))
        }
    }
}
