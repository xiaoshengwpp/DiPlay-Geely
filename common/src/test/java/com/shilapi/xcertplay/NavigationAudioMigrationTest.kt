package com.shilapi.xcertplay

import android.content.Context
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [29], manifest = Config.NONE)
class NavigationAudioMigrationTest {
    private val context get() = RuntimeEnvironment.getApplication()
    private val prefs get() = context.getSharedPreferences("xcertplay_airplay", Context.MODE_PRIVATE)
    @Before fun clear() { prefs.edit().clear().commit() }

    @Test fun freshInstallRetainsLegacyNavigationDefaultWithoutWritingNewKey() {
        assertNull(AirPlayPersistence.loadNavigationAudioChannel(context))
        assertEquals(14, AirPlayPersistence.loadNavigationStreamType(context))
        assertFalse(AirPlayPersistence.loadAdvancedAudioChannelMapping(context))
        assertFalse(prefs.contains("navigation_audio_channel"))
    }

    @Test fun legacyStreamsAndAdvancedChoicesSurviveRepeatedReads() {
        for (advanced in listOf(false, true)) for (stream in listOf(3, 14)) {
            AirPlayPersistence.saveAdvancedAudioChannelMapping(context, advanced)
            AirPlayPersistence.saveNavigationStreamType(context, stream)
            repeat(3) { assertNull(AirPlayPersistence.loadNavigationAudioChannel(context)) }
            assertEquals(stream, AirPlayPersistence.loadNavigationStreamType(context))
            assertEquals(advanced, AirPlayPersistence.loadAdvancedAudioChannelMapping(context))
            assertFalse(prefs.contains("navigation_audio_channel"))
        }
    }

    @Test fun everyExplicitNewChoiceIncludingZeroIsDistinguishedFromUnset() {
        AirPlayPersistence.saveNavigationStreamType(context, 14)
        AirPlayPersistence.saveAdvancedAudioChannelMapping(context, true)
        for (channel in 0..20) {
            AirPlayPersistence.saveNavigationAudioChannel(context, channel)
            assertEquals(channel, AirPlayPersistence.loadNavigationAudioChannel(context))
            assertTrue(prefs.contains("navigation_audio_channel"))
            assertEquals(14, AirPlayPersistence.loadNavigationStreamType(context))
            assertTrue(AirPlayPersistence.loadAdvancedAudioChannelMapping(context))
        }
    }

    @Test fun invalidNewValuesCannotOverrideLegacyRoute() {
        for (invalid in listOf(-1, 21, 99)) {
            prefs.edit().putInt("navigation_audio_channel", invalid).commit()
            assertNull(AirPlayPersistence.loadNavigationAudioChannel(context))
            try { AirPlayPersistence.saveNavigationAudioChannel(context, invalid); fail("invalid channel saved") }
            catch (_: IllegalArgumentException) { }
        }
    }

    @Test fun selectingRetainedRemovesOnlyTheNewOverride() {
        AirPlayPersistence.saveNavigationStreamType(context, 3)
        AirPlayPersistence.saveNavigationAudioChannel(context, 8)
        AirPlayPersistence.saveNavigationAudioChannel(context, null)
        assertNull(AirPlayPersistence.loadNavigationAudioChannel(context))
        assertFalse(prefs.contains("navigation_audio_channel"))
        assertEquals(3, AirPlayPersistence.loadNavigationStreamType(context))
    }

    @Test fun gpsStaysOptInAndPreservesSavedChoice() {
        assertFalse(AirPlayPersistence.loadLocationReportingEnabled(context))
        for (enabled in listOf(true, false)) {
            AirPlayPersistence.saveLocationReportingEnabled(context, enabled)
            assertEquals(enabled, AirPlayPersistence.loadLocationReportingEnabled(context))
        }
    }
}
