package com.shilapi.xcertplay.media

import org.junit.Assert.*
import org.junit.Test

class AudioInputWaitTest {
    private fun audioTimeout(): Long = Class.forName("com.shilapi.xcertplay.media.AudioRenderer")
        .getDeclaredField("AUDIO_INPUT_WAIT_NS").apply { isAccessible = true }.getLong(null)

    @Test fun audioStopsAfter100msWhileVideoKeeps500msDefault() {
        val audioWait = audioTimeout()
        assertEquals(100_000_000L, audioWait)
        for (audio in listOf(true, false)) {
            var time = 0L
            var drains = 0
            var attempts = 0
            val dequeue = { attempts++; time += 10_000_000L; -1 }
            val result = if (audio) DecoderInputPump.acquire(
                running = { true }, drain = { drains++ }, dequeue = dequeue,
                nanoTime = { time }, timeoutNs = audioWait,
            ) else DecoderInputPump.acquire(
                running = { true }, drain = { drains++ }, dequeue = dequeue, nanoTime = { time },
            )
            assertEquals(-1, result)
            assertEquals(if (audio) 10 else 50, attempts)
            assertEquals(attempts, drains)
        }
    }

    @Test fun audioDrainsAndRecoversBeforeDeadlineAndStopsOnShutdown() {
        var time = 0L
        var attempts = 0
        var drains = 0
        assertEquals(4, DecoderInputPump.acquire(
            running = { true }, drain = { drains++ },
            dequeue = { time += 10_000_000L; if (++attempts == 2) 4 else -1 },
            nanoTime = { time }, timeoutNs = audioTimeout(),
        ))
        assertEquals(2, drains)
        assertEquals(-1, DecoderInputPump.acquire(
            running = { false }, drain = { fail("Must not drain after shutdown") },
            dequeue = { fail("Must not dequeue after shutdown"); -1 }, timeoutNs = audioTimeout(),
        ))
    }
}
