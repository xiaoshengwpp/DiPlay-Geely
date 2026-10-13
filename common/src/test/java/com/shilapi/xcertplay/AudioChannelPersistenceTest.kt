package com.shilapi.xcertplay

import android.content.Context
import org.junit.Assert.*
import com.shilapi.xcertplay.setup.DiLinkGeneration
import com.shilapi.xcertplay.setup.SetupGuide
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], manifest = Config.NONE)
class AudioChannelPersistenceTest {
    private val context get() = RuntimeEnvironment.getApplication()
    private val prefs get() = context.getSharedPreferences("xcertplay_airplay", Context.MODE_PRIVATE)

    @Before fun clearPreferences() {
        prefs.edit().clear().apply()
    }

    @Test fun audioFocusAutoYieldCanBeSavedAndRestored() {
        assertTrue(AirPlayPersistence.loadAudioFocusAutoYield(context))
        AirPlayPersistence.saveAudioFocusAutoYield(context, false)
        assertFalse(AirPlayPersistence.loadAudioFocusAutoYield(context))
        AirPlayPersistence.saveAudioFocusAutoYield(context, true)
        assertTrue(AirPlayPersistence.loadAudioFocusAutoYield(context))
    }

    @Test fun freshInstallRetainsLegacyNavigationRouting() {
        assertEquals(0, AirPlayPersistence.loadMediaAudioChannel(context))
        assertNull(AirPlayPersistence.loadNavigationAudioChannel(context))
    }

    @Test fun legacyNavigationChannelRemainsSeparateWithoutChangingMedia() {
        AirPlayPersistence.saveNavigationStreamType(context, 15)
        assertNull(AirPlayPersistence.loadNavigationAudioChannel(context))
        assertEquals(0, AirPlayPersistence.loadMediaAudioChannel(context))
    }

    @Test fun explicitNavigationChannelTakesPrecedenceOverLegacySetting() {
        AirPlayPersistence.saveNavigationStreamType(context, 14)
        AirPlayPersistence.saveNavigationAudioChannel(context, 15)
        assertEquals(15, AirPlayPersistence.loadNavigationAudioChannel(context))
    }

    @Test fun explicitAutomaticRoutingTakesPrecedenceOverLegacySetting() {
        AirPlayPersistence.saveNavigationStreamType(context, 15)
        AirPlayPersistence.saveNavigationAudioChannel(context, 0)
        assertEquals(0, AirPlayPersistence.loadNavigationAudioChannel(context))
    }

    @Test fun extendedChannelsCanBeSavedForMediaAndNavigation() {
        for (channel in listOf(11, 14, 15, 20)) {
            AirPlayPersistence.saveMediaAudioChannel(context, channel)
            AirPlayPersistence.saveNavigationAudioChannel(context, channel)
            assertEquals(channel, AirPlayPersistence.loadMediaAudioChannel(context))
            assertEquals(channel, AirPlayPersistence.loadNavigationAudioChannel(context))
        }
    }

    @Test fun existingExtendedChannelsRemainAvailable() {
        prefs.edit().putInt("media_audio_channel", 15).putInt("navigation_audio_channel", 15).apply()
        assertEquals(15, AirPlayPersistence.loadMediaAudioChannel(context))
        assertEquals(15, AirPlayPersistence.loadNavigationAudioChannel(context))
    }

    @Test fun invalidSavedChannelsUseAutomaticRouting() {
        for (channel in listOf(-1, 21)) {
            AirPlayPersistence.saveMediaAudioChannel(context, channel)
            try { AirPlayPersistence.saveNavigationAudioChannel(context, channel); fail("invalid override accepted") }
            catch (_: IllegalArgumentException) { }
            assertEquals(0, AirPlayPersistence.loadMediaAudioChannel(context))
            assertNull(AirPlayPersistence.loadNavigationAudioChannel(context))
        }
    }

    @Test fun invalidStoredChannelsUseAutomaticRouting() {
        AirPlayPersistence.saveNavigationStreamType(context, 15)
        for (channel in listOf(-1, 21)) {
            prefs.edit().putInt("media_audio_channel", channel).putInt("navigation_audio_channel", channel).apply()
            assertEquals(0, AirPlayPersistence.loadMediaAudioChannel(context))
            assertNull(AirPlayPersistence.loadNavigationAudioChannel(context))
        }
    }

    @Test fun invalidLegacyChannelUsesAutomaticRouting() {
        AirPlayPersistence.saveNavigationStreamType(context, 21)
        assertNull(AirPlayPersistence.loadNavigationAudioChannel(context))
    }

    @Test fun bydGenerationDefaultsNeverReplaceTheGeelyLegacyRoute() {
        for (generation in DiLinkGeneration.entries) {
            assertFalse(SetupGuide.applyGenerationDefaults(context, generation))
            assertFalse(prefs.contains("navigation_audio_channel"))
            assertNull(AirPlayPersistence.loadNavigationAudioChannel(context))
        }
        assertEquals(14, AirPlayPersistence.loadNavigationStreamType(context))
    }

    @Test fun firstSetupAlsoLeavesTheNavigationChoiceUnset() {
        val guide = context.getSharedPreferences("diplay", Context.MODE_PRIVATE)
        guide.edit().clear().commit()
        try {
            for (generation in DiLinkGeneration.entries) {
                assertFalse(SetupGuide.applyFirstSetupDefaults(context, generation, phoneChosen = false))
                assertFalse(SetupGuide.applyFirstSetupDefaults(context, generation, phoneChosen = true))
                assertFalse(prefs.contains("navigation_audio_channel"))
            }
            SetupGuide.markSeen(context)
            assertFalse(SetupGuide.applyFirstSetupDefaults(context, DiLinkGeneration.DILINK_4, phoneChosen = false))
            assertNull(AirPlayPersistence.loadNavigationAudioChannel(context))
        } finally {
            guide.edit().clear().commit()
        }
    }

    @Test fun setupGuidePreservesBothExplicitAndLegacyAudioChoices() {
        for (channel in listOf(0, 14, 20)) {
            AirPlayPersistence.saveNavigationAudioChannel(context, channel)
            assertFalse(SetupGuide.applyGenerationDefaults(context, DiLinkGeneration.DILINK_5))
            assertEquals(channel, AirPlayPersistence.loadNavigationAudioChannel(context))
        }
        prefs.edit().clear().apply()
        AirPlayPersistence.saveNavigationStreamType(context, 15)
        assertFalse(SetupGuide.applyGenerationDefaults(context, DiLinkGeneration.DILINK_3))
        assertNull(AirPlayPersistence.loadNavigationAudioChannel(context))
        assertEquals(15, AirPlayPersistence.loadNavigationStreamType(context))
    }
}
