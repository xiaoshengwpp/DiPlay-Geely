package com.shilapi.xcertplay.hud

import android.content.Context
import android.content.ContextWrapper
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], manifest = Config.NONE)
class BydCarMusicRhythmTest {
    private val context = ContextWrapper(null)

    @Test fun liveEntryPointsNeverReachEvenInjectedBackends() {
        val forbidden = object : BydCarMusicRhythmCall {
            override fun supported(context: Context): Boolean = error("Unexpected probe")
            override fun start(context: Context): Boolean = error("Unexpected start")
            override fun stop(context: Context): Unit = error("Unexpected stop")
            override fun sendBands(context: Context, bands: IntArray): Boolean = error("Unexpected send")
        }
        BydCarMusicRhythm.direct = forbidden
        BydCarMusicRhythm.helper = forbidden
        try {
            BydCarMusicRhythm.setPlaying(context, true)
            BydCarMusicRhythm.sendBands(context, IntArray(16) { 5 })
            BydCarMusicRhythm.setPlaying(context, false)
            val support = BydCarMusicRhythm.checkSupport(context)
            assertTrue(support.isDone)
            assertFalse(support.get())
        } finally {
            BydCarMusicRhythm.direct = BydCarMusicRhythmDirect
            BydCarMusicRhythm.helper = BydCarMusicRhythmHelper
        }
    }

    @Test fun directHelperAndExecutableEntryPointsAreAlsoInert() {
        for (backend in listOf(BydCarMusicRhythmDirect, BydCarMusicRhythmHelper)) {
            assertFalse(backend.supported(context))
            assertFalse(backend.start(context))
            backend.stop(context)
            assertFalse(backend.sendBands(context, IntArray(16) { 5 }))
        }
        BydCarMusicRhythmTool.main(arrayOf("--probe"))
        BydCarMusicRhythmTool.main(arrayOf("--stdin"))
        assertEquals(-1, BydCarMusicRhythmBands.Feed().send(IntArray(16) { 5 }))
    }

    @Test fun pureBandValidationRemainsAvailable() {
        assertArrayEquals(IntArray(16) { 5 }, BydCarMusicRhythmBands.parse(List(16) { "5" }.joinToString(" ")))
        assertNull(BydCarMusicRhythmBands.parse("1 2 3"))
        assertFalse(BydCarMusicRhythmBands.valid(IntArray(16) { 0 }))
        assertFalse(BydCarMusicRhythmBands.valid(IntArray(16) { 16 }))
        assertEquals(16, BydCarMusicRhythmBands.featureIds().size)
        assertEquals(774897672, BydCarMusicRhythmBands.featureIds().first())
        assertEquals(774897732, BydCarMusicRhythmBands.featureIds().last())
    }
}
