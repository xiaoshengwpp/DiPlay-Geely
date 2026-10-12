package com.shilapi.xcertplay.media

import com.shilapi.xcertplay.airplay.AudioCodecKind
import com.shilapi.xcertplay.airplay.AudioFormat
import com.shilapi.xcertplay.airplay.AudioStreamId
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, sdk = [28])
class GeelyNavigationRendererTest {
    private fun actualRoute(legacy: Int, advanced: Boolean, explicit: Int?): Int {
        val sink = AndroidMediaSink(navigationStreamType = legacy, advancedAudioChannelMapping = advanced,
            navigationChannel = explicit)
        try {
            val format = AudioFormat(AudioCodecKind.LPCM, 44100, 2, 101, "default")
            val factory = AndroidMediaSink::class.java.getDeclaredMethod("audioRenderer", AudioStreamId::class.java, AudioFormat::class.java)
                .apply { isAccessible = true }
            val renderer = factory.invoke(sink, AudioStreamId(101, "default"), format)
            val selection = renderer.javaClass.getDeclaredMethod("mappedSelection")
                .apply { isAccessible = true }.invoke(renderer) as AudioChannelSelection
            return renderer.javaClass.getDeclaredMethod("channelOverride", AudioChannelSelection::class.java)
                .apply { isAccessible = true }.invoke(renderer, selection) as Int
        } finally { sink.close() }
    }
    @Test fun savedLegacyChoiceReachesActualRendererWhenNewChoiceAbsent() {
        for (legacy in listOf(3, 14)) assertEquals(legacy, actualRoute(legacy, false, null))
    }
    @Test fun advancedUnsetRetainsUsageRouting() {
        for (legacy in listOf(3, 14)) assertEquals(0, actualRoute(legacy, true, null))
    }
    @Test fun explicitZeroAndChannelsOverrideSavedLegacyInBothModes() {
        for (advanced in listOf(false, true)) for (legacy in listOf(3, 14)) for (channel in 0..20) {
            assertEquals(channel, actualRoute(legacy, advanced, channel))
        }
    }
}
