package com.shilapi.xcertplay

import org.junit.Assert.*
import org.junit.Test

class NavigationChannelSelectionTest {
    @Test fun openingAndKeepingRetainedSelectionDoesNotApplyOrPreviewLegacyStream() {
        val selection = NavigationChannelSelection(null)
        assertEquals(0, selection.index)
        assertNull(selection.value)
        assertFalse(selection.changed)
        assertNull(selection.select(0))
        assertFalse(selection.changed)
    }
    @Test fun allNewChannelsAreExplicitAndPreviewOnlyZeroThroughTwenty() {
        for (channel in 0..20) {
            val selection = NavigationChannelSelection(null)
            assertEquals(channel, selection.select(channel + 1))
            assertEquals(channel, selection.value)
            assertTrue(selection.changed)
        }
    }
    @Test fun returningToInitialChoiceIsNotAChange() {
        for (channel in 0..20) {
            val selection = NavigationChannelSelection(channel)
            selection.select(0)
            assertTrue(selection.changed)
            selection.select(channel + 1)
            assertFalse(selection.changed)
        }
    }
    @Test fun invalidLegacyNumberIsRetainedWithoutPreview() {
        val selection = NavigationChannelSelection(21)
        assertNull(selection.value)
        assertEquals(0, selection.index)
        assertFalse(selection.changed)
    }
}
