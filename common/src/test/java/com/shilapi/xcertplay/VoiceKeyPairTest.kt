package com.shilapi.xcertplay

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class VoiceKeyPairTest {
    private val session = Any()

    @Test fun theGenericKeyAfterACarPlayKeyIsTheSamePress() {
        val pair = VoiceKeyPair()
        var sent = 0
        assertTrue(pair.sendCarPlayKey(10_000, session) { sent++; true })
        assertFalse(pair.sendGenericKey(10_460, session) { sent++; true })
        assertEquals(1, sent)
    }

    @Test fun theCarPlayKeyAfterAGenericKeyIsTheSamePress() {
        val pair = VoiceKeyPair()
        var sent = 0
        assertTrue(pair.sendGenericKey(10_000, session) { sent++; true })
        assertFalse(pair.sendCarPlayKey(10_300, session) { sent++; true })
        assertEquals(1, sent)
    }

    @Test fun aLaterPressOpensSiriAgain() {
        val pair = VoiceKeyPair()
        assertTrue(pair.sendCarPlayKey(10_000, session) { true })
        assertFalse(pair.sendGenericKey(10_460, session) { true })
        assertTrue(pair.sendCarPlayKey(10_000 + VoiceKeyPair.WINDOW_MILLIS, session) { true })
        assertFalse(pair.sendGenericKey(10_000 + VoiceKeyPair.WINDOW_MILLIS + 460, session) { true })
    }

    @Test fun pressesOfOneFamilyAreNotSuppressedByEachOther() {
        val pair = VoiceKeyPair()
        assertTrue(pair.sendGenericKey(10_000, session) { true })
        assertTrue(pair.sendGenericKey(10_100, session) { true })
        assertTrue(pair.sendCarPlayKey(20_000, session) { true })
        assertTrue(pair.sendCarPlayKey(20_100, session) { true })
    }

    @Test fun aReplacementSessionDoesNotInheritThePreviousPhonesSuppression() {
        val pair = VoiceKeyPair()
        assertTrue(pair.sendCarPlayKey(10_000, session) { true })
        val replacement = Any()
        assertTrue(pair.sendGenericKey(10_100, replacement) { true })
        assertFalse(pair.sendCarPlayKey(10_200, replacement) { true })
    }

    @Test fun aRejectedRequestDoesNotSuppressTheOtherSource() {
        val pair = VoiceKeyPair()
        assertFalse(pair.sendCarPlayKey(10_000, session) { false })
        assertTrue(pair.sendGenericKey(10_460, session) { true })
        val other = VoiceKeyPair()
        assertFalse(other.sendGenericKey(20_000, session) { false })
        assertTrue(other.sendCarPlayKey(20_460, session) { true })
    }

    @Test fun ignoredDuplicatesDoNotExtendTheSuppressionWindow() {
        val pair = VoiceKeyPair()
        assertTrue(pair.sendCarPlayKey(10_000, session) { true })
        assertFalse(pair.sendGenericKey(11_499, session) { true })
        assertTrue(pair.sendGenericKey(11_500, session) { true })
    }

    @Test fun anEarlierTimestampCannotSuppressAFuturePress() {
        val pair = VoiceKeyPair()
        assertTrue(pair.sendCarPlayKey(20_000, session) { true })
        assertTrue(pair.sendGenericKey(10_000, session) { true })
    }
}
