package com.shilapi.xcertplay.media

import android.Manifest
import android.media.AudioRecord
import com.shilapi.xcertplay.airplay.MicrophoneConfig
import com.shilapi.xcertplay.airplay.AudioCodecKind
import java.net.InetAddress
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowAudioRecord
import org.robolectric.util.ReflectionHelpers

/** A recorder that returns no audio is restarted instead of being read in a tight loop (#566). */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [25, 29], manifest = Config.NONE)
class MicrophoneNoAudioRestartTest {
    private val diagnostics = CopyOnWriteArrayList<String>()
    private val reads = AtomicInteger()
    private val recorder = AtomicReference<AudioRecord?>()
    private var uplink: MicrophoneUplink? = null

    @Before fun setUp() {
        shadowOf(RuntimeEnvironment.getApplication()).grantPermissions(Manifest.permission.RECORD_AUDIO)
    }

    @After fun tearDown() {
        uplink?.close()
        ShadowAudioRecord.clearSource()
    }

    @Test fun aRecorderThatReturnsNoAudioIsRestartedAFewTimesWithoutSpinning() {
        provide { 0 }
        start()

        Thread.sleep(600)

        val restarts = diagnostics.filter { it.startsWith("Microphone: recording restarted") }
        assertEquals(diagnostics.joinToString("\n"), 3, restarts.size)
        assertTrue(restarts.first(), restarts.first().contains("attempt=1 reason=no-audio routedDeviceType="))
        assertEquals(AudioRecord.RECORDSTATE_RECORDING, requireNotNull(recorder.get()).recordingState)
        // About one read per 5 ms pause, not hundreds of thousands per second.
        assertTrue("reads=${reads.get()}", reads.get() < 300)
    }

    @Test fun aRecorderThatDeliversAudioIsNeverRestarted() {
        provide { size ->
            Thread.sleep(5)
            size
        }
        start()

        Thread.sleep(400)

        assertTrue(diagnostics.joinToString("\n"), diagnostics.none { it.startsWith("Microphone: recording restarted") })
    }

    @Test fun transientReadErrorsRetryAndThenKeepCapturing() {
        val recovered = CountDownLatch(1)
        provide { _ ->
            if (reads.get() <= 2) AudioRecord.ERROR_INVALID_OPERATION else {
                recovered.countDown()
                Thread.sleep(5)
                0
            }
        }
        start()
        assertTrue(recovered.await(5, TimeUnit.SECONDS))
        assertEquals(2, diagnostics.count { it.contains("reason=read-error") })
    }

    @Test fun repeatedReadErrorsExhaustTheBoundedRetryBudget() {
        provide { AudioRecord.ERROR_INVALID_OPERATION }
        start()
        awaitEnded()
        assertEquals(4, reads.get())
        assertEquals(3, diagnostics.count { it.contains("reason=read-error") })
    }

    @Test fun aDeadRecorderIsReleasedWithoutTryingToRestartIt() {
        provide { AudioRecord.ERROR_DEAD_OBJECT }
        start()
        awaitEnded()
        assertEquals(1, reads.get())
        assertTrue(diagnostics.none { it.startsWith("Microphone: recording restarted") })
        assertTrue(diagnostics.any { it.contains("stage=READ") && it.endsWith("code=${AudioRecord.ERROR_DEAD_OBJECT}") })
    }

    @Test fun closeDuringTheRestartPauseCannotRestartCapture() {
        val paused = CountDownLatch(1)
        val resume = CountDownLatch(1)
        provide { AudioRecord.ERROR_INVALID_OPERATION }
        val next = MicrophoneUplink(config(), onDiagnostic = diagnostics::add, restartPause = {
            paused.countDown()
            resume.await(5, TimeUnit.SECONDS)
        })
        uplink = next
        assertTrue(next.start())
        assertTrue(paused.await(5, TimeUnit.SECONDS))
        val closing = Thread { next.close() }.apply { start() }
        try {
            val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5)
            while (!synchronized(next) { ReflectionHelpers.getField<Boolean>(next, "closed") } && System.nanoTime() < deadline) {
                Thread.sleep(1)
            }
            assertTrue(synchronized(next) { ReflectionHelpers.getField<Boolean>(next, "closed") })
        } finally {
            resume.countDown()
            closing.join(5000)
        }
        assertFalse(closing.isAlive)
        assertEquals(AudioRecord.STATE_UNINITIALIZED, requireNotNull(recorder.get()).state)
        assertTrue(diagnostics.none { it.startsWith("Microphone: recording restarted") })
        assertFalse("A closed uplink cannot be reopened by a late callback", next.start())
    }

    @Test fun readErrorRestartDropsTheOldPartialPcmFrame() = verifyPartialFrameIsDropped(noAudio = false)

    @Test fun noAudioRestartDropsTheOldPartialPcmFrame() = verifyPartialFrameIsDropped(noAudio = true)

    @Test fun aRestartClearsTheCaptureTimestampFramePosition() {
        val clock = CaptureClock(16_000)
        ReflectionHelpers.setField(clock, "samplesRead", 32_000L)
        ReflectionHelpers.setField(clock, "lastReadSamples", 320)
        ReflectionHelpers.setField(clock, "lastReadEndNs", 2_000_000_000L)
        clock.reset()
        assertEquals(0L, ReflectionHelpers.getField<Long>(clock, "samplesRead"))
        assertEquals(0, ReflectionHelpers.getField<Int>(clock, "lastReadSamples"))
        assertEquals(0L, clock.frameEndNs(0))
    }

    private fun verifyPartialFrameIsDropped(noAudio: Boolean) {
        val captureConfig = config().copy(sampleRate = 48_000, codec = AudioCodecKind.OPUS)
        val expected = ByteArray(captureConfig.frameBytes) { 2 }
        val encoded = AtomicReference<ByteArray?>()
        val encodedFrame = CountDownLatch(1)
        ShadowAudioRecord.setSourceProvider { record ->
            recorder.set(record)
            object : ShadowAudioRecord.AudioRecordSource {
                override fun readInByteArray(buffer: ByteArray, offset: Int, size: Int, blocking: Boolean): Int {
                    val read = reads.incrementAndGet()
                    if (read == 1) {
                        buffer.fill(1, offset, offset + captureConfig.frameBytes / 2)
                        return captureConfig.frameBytes / 2
                    }
                    if (diagnostics.none { it.startsWith("Microphone: recording restarted") }) {
                        return if (noAudio) 0 else AudioRecord.ERROR_INVALID_OPERATION
                    }
                    if (encoded.get() == null) {
                        expected.copyInto(buffer, offset)
                        return expected.size
                    }
                    Thread.sleep(5)
                    return 0
                }
            }
        }
        val encoder = object : MicrophoneOpusEncoder {
            override val implementation = "test"
            override val available = true
            override fun encode(pcm: ByteArray): List<ByteArray> {
                encoded.compareAndSet(null, pcm.copyOf())
                encodedFrame.countDown()
                return emptyList()
            }
            override fun close() {}
        }
        val next = MicrophoneUplink(captureConfig, onDiagnostic = diagnostics::add,
            opusEncoderFactory = { encoder }, noAudioRestartMillis = 10)
        uplink = next
        assertTrue(next.start())
        assertTrue(encodedFrame.await(5, TimeUnit.SECONDS))
        assertArrayEquals(expected, encoded.get())
    }

    private fun awaitEnded() {
        val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5)
        while (diagnostics.none { it.endsWith("ended=true") } && System.nanoTime() < deadline) Thread.sleep(5)
        assertTrue(diagnostics.joinToString("\n"), diagnostics.any { it.endsWith("ended=true") })
        uplink!!.close()
        assertEquals(AudioRecord.STATE_UNINITIALIZED, requireNotNull(recorder.get()).state)
    }

    private fun provide(read: (Int) -> Int) {
        ShadowAudioRecord.setSourceProvider { record ->
            recorder.compareAndSet(null, record)
            object : ShadowAudioRecord.AudioRecordSource {
                override fun readInByteArray(buffer: ByteArray, offset: Int, size: Int, blocking: Boolean): Int {
                    reads.incrementAndGet()
                    return read(size)
                }
            }
        }
    }

    private fun start() {
        val next = MicrophoneUplink(config(), onDiagnostic = diagnostics::add, noAudioRestartMillis = 50)
        uplink = next
        assertTrue(next.start())
    }

    private fun config() = MicrophoneConfig(
        audioType = "speechrecognition",
        sampleRate = 16_000,
        channels = 1,
        payloadType = 100,
        frameMillis = 20,
        host = InetAddress.getLoopbackAddress(),
        port = 9,
        key = ByteArray(32),
    )
}
