package com.shilapi.xcertplay.media

import android.media.AudioAttributes
import android.media.AudioTrack
import com.shilapi.xcertplay.airplay.AudioCodecKind
import com.shilapi.xcertplay.airplay.AudioFormat
import java.io.Closeable
import java.util.concurrent.BlockingQueue
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.Implementation
import org.robolectric.annotation.Implements

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], manifest = Config.NONE, shadows = [AudioRebufferDiagnosticsTest.TestTrack::class])
class AudioRebufferDiagnosticsTest {
    @Before fun resetTrack() {
        TestTrack.headFrames = 0
        TestTrack.writtenBytes = 0
        TestTrack.underruns = 0
        TestTrack.playing = false
    }

    @Test fun rebufferingReportsRetainedPcmAndRecoversWithoutFlushingIt() {
        val reports = mutableListOf<String>()
        RendererFixture(reports::add).use { renderer ->
            renderer.write(renderer.threshold)
            assertTrue(TestTrack.playing)
            assertTrue(reports.isEmpty()) // Initial playback is not recovery.
            val retained = renderer.threshold / 4
            renderer.starve(retained)
            assertFalse(TestTrack.playing)
            assertEquals(retained, renderer.get("prebufferBytes"))
            val retainedMillis = retained * 1000L / (48_000 * 4)
            assertEquals(listOf("Audio: rebuffer paused audioType=media rebufferCount=1 retainedMs=$retainedMillis"), reports)

            renderer.write(renderer.threshold - retained)
            assertTrue(TestTrack.playing)
            assertEquals("Audio: rebuffer recovered audioType=media rebufferCount=1", reports.last())
        }
    }

    @Test fun tenSeparateRebufferAndOverflowEpisodesAreCappedAtFiveReportsEach() {
        val reports = mutableListOf<String>()
        RendererFixture(reports::add).use { renderer ->
            renderer.write(renderer.threshold)
            repeat(10) {
                renderer.starve()
                assertFalse(TestTrack.playing)
                renderer.write(renderer.threshold)
                assertTrue(TestTrack.playing)
            }
            assertEquals(5, reports.count { it.startsWith("Audio: rebuffer paused") })
            assertEquals(5, reports.count { it.startsWith("Audio: rebuffer recovered") })
            assertEquals(10, renderer.get("rebufferCount"))

            repeat(10) {
                renderer.overflow()
                assertEquals(true, renderer.get("droppedPacketsLogged"))
                renderer.drain()
                assertEquals(false, renderer.get("droppedPacketsLogged"))
            }
            val overflows = reports.filter { it.startsWith("Audio: queue full") }
            assertEquals(5, overflows.size)
            assertEquals("Audio: queue full audioType=media count=5", overflows.last())
        }
    }

    @Test fun failingDiagnosticsCannotStopRebufferRecoveryOrEscapePacketSubmission() {
        RendererFixture { throw IllegalStateException("Report unavailable") }.use { renderer ->
            renderer.write(renderer.threshold)
            renderer.starve()
            assertFalse(TestTrack.playing)
            renderer.write(renderer.threshold)
            assertTrue(TestTrack.playing)
            renderer.overflow()
            renderer.drain()
            assertEquals(false, renderer.get("droppedPacketsLogged"))
        }
    }

    /** Exercise the production worker methods without starting an asynchronous decoder thread. */
    private class RendererFixture(report: (String) -> Unit) : Closeable {
        private val plan = MediaAudioBuffer.plan(true, 48_000, 2, 4096, 500)
        val threshold = MediaAudioBuffer.startBytesFor(plan.startBytes, plan.trackBufferBytes, 4096)
        private val track = AudioTrack.Builder()
            .setAudioAttributes(AudioAttributes.Builder().build())
            .setAudioFormat(android.media.AudioFormat.Builder().setSampleRate(48_000)
                .setEncoding(android.media.AudioFormat.ENCODING_PCM_16BIT)
                .setChannelMask(android.media.AudioFormat.CHANNEL_OUT_STEREO).build())
            .setBufferSizeInBytes(plan.trackBufferBytes).build()
        private val type = Class.forName("com.shilapi.xcertplay.media.AudioRenderer")
        private val renderer = type.declaredConstructors.single { it.parameterCount == 13 }
            .apply { isAccessible = true }.newInstance(
                AudioFormat(AudioCodecKind.LPCM, 48_000, 2, 96, "media"),
                false, false, 0, null, AudioFocusCoordinator(null, false, false),
                0, 500, report, 0L, false, null, false)
        private val packetType = Class.forName("com.shilapi.xcertplay.media.AudioRenderer\$AudioPacket")
        // A header-only packet drains the queue without introducing extra PCM into the fixture.
        private val rtp = ByteArray(12)
        private val packet = packetType.declaredConstructors.single().apply { isAccessible = true }
            .newInstance(rtp, 0)
        @Suppress("UNCHECKED_CAST")
        private val queue = get("queue") as BlockingQueue<Any>

        init {
            set("track", track)
            set("mappedChannel", AudioChannel.MEDIA)
            set("startThresholdBytes", threshold)
            set("bytesPerSecond", 48_000 * 4)
        }

        fun get(name: String): Any? = type.getDeclaredField(name).apply { isAccessible = true }.get(renderer)
        private fun set(name: String, value: Any?) =
            type.getDeclaredField(name).apply { isAccessible = true }.set(renderer, value)

        fun write(bytes: Int) {
            type.getDeclaredMethod("writePcm", ByteArray::class.java,
                Int::class.javaPrimitiveType, Int::class.javaPrimitiveType)
                .apply { isAccessible = true }.invoke(renderer, ByteArray(bytes), 0, bytes)
        }

        fun starve(retainedBytes: Int = 0) {
            TestTrack.headFrames = (TestTrack.writtenBytes - retainedBytes) / 4
            TestTrack.underruns++
            type.getDeclaredMethod("maintainPlaybackBuffer").apply { isAccessible = true }.invoke(renderer)
        }

        fun overflow() {
            set("started", true)
            while (queue.remainingCapacity() > 0) queue.offer(packet)
            type.getDeclaredMethod("submit", ByteArray::class.java, Int::class.javaPrimitiveType)
                .apply { isAccessible = true }.invoke(renderer, rtp, 0)
        }

        fun drain() {
            queue.clear()
            type.getDeclaredMethod("handle", packetType).apply { isAccessible = true }.invoke(renderer, packet)
        }

        override fun close() {
            (renderer as Closeable).close()
            // No worker was started, so execute the release normally performed in its finally block.
            type.getDeclaredMethod("release").apply { isAccessible = true }.invoke(renderer)
        }
    }

    @Implements(AudioTrack::class)
    class TestTrack {
        @Implementation fun getPlaybackHeadPosition() = headFrames
        @Implementation fun getUnderrunCount() = underruns
        @Implementation fun pause() { playing = false }
        @Implementation fun play() { playing = true }
        @Implementation fun write(bytes: ByteArray, offset: Int, size: Int, mode: Int): Int {
            writtenBytes += size
            return size
        }
        companion object {
            var headFrames = 0
            var writtenBytes = 0
            var underruns = 0
            var playing = false
        }
    }
}
