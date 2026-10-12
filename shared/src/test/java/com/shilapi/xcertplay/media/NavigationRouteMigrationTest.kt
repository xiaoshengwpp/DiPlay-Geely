package com.shilapi.xcertplay.media

import org.junit.Assert.assertEquals
import org.junit.Test

class NavigationRouteMigrationTest {
    private fun selection(stream: Int = 14, channel: AudioChannel = AudioChannel.NAVIGATION) =
        AudioChannelSelection(channel, AudioContentType.SPEECH, stream)

    @Test fun unsetNewChoicePreservesLegacyNavigationStreams() {
        for (stream in listOf(3, 14)) assertEquals(stream,
            AudioChannelMapper.outputStreamOverride(selection(stream), false, 0, null))
    }
    @Test fun advancedMappingKeepsUsageRoutingUntilExplicitSelection() {
        for (stream in listOf(3, 14)) assertEquals(0,
            AudioChannelMapper.outputStreamOverride(selection(stream), true, 0, null))
    }
    @Test fun explicitChannelsIncludingZeroOverrideLegacyAndAdvancedRoutes() {
        for (advanced in listOf(false, true)) for (stream in listOf(3, 14)) for (channel in 0..20) {
            assertEquals(channel, AudioChannelMapper.outputStreamOverride(selection(stream), advanced, 0, channel))
        }
    }
    @Test fun invalidOverrideFallsBackToLegacyOrAdvancedChoice() {
        for (invalid in listOf(-1, 21, 99)) {
            assertEquals(14, AudioChannelMapper.outputStreamOverride(selection(), false, 0, invalid))
            assertEquals(0, AudioChannelMapper.outputStreamOverride(selection(), true, 0, invalid))
        }
    }
    @Test fun newNavigationSelectionDoesNotChangeOtherAudioTypes() {
        assertEquals(6, AudioChannelMapper.outputStreamOverride(selection(channel=AudioChannel.MEDIA), false, 6, 9))
        for (channel in listOf(AudioChannel.PHONE, AudioChannel.ASSISTANT)) {
            assertEquals(0, AudioChannelMapper.outputStreamOverride(selection(channel=channel), false, 6, 9))
        }
    }
    @Test fun mapperPropagatesSavedLegacyStreamIntoActualSelection() {
        for (stream in listOf(3, 14)) for (type in listOf("default", "alert", "compatibility", "unknown")) {
            val mapped = AudioChannelMapper.map(type, 101, AudioChannelMappingMode.MOBILE_COMPATIBLE, stream)
            assertEquals(stream, AudioChannelMapper.outputStreamOverride(mapped, false, 0, null))
        }
    }
}
