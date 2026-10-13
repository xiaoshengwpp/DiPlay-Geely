package com.shilapi.xcertplay.airplay

import java.io.Closeable
import java.net.Socket
import java.net.InetAddress
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.math.BigInteger
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CarPlayMediaEngineTest {
    @Test
    fun streamConnectionIdUsesUnsignedDecimalForHkdfSalt() {
        assertEquals("18446744073709551615", unsignedPlistDecimal(-1L))
        assertEquals(
            BigInteger("18446744073709551615"),
            unsignedPlistInteger(-1L),
        )
    }

    @Test
    fun screenStreamTeardownReportsInactive() {
        val events = mutableListOf<Pair<Int, Boolean>>()
        val sink = object : MediaSink {
            override fun onScreenStreamActive(type: Int, active: Boolean) {
                events += type to active
            }
        }
        val session = testSession()

        try {
            val engine = CarPlayMediaEngine(sink)
            engine.onTeardown(session, 110)
            engine.onTeardown(session, 100)
        } finally {
            session.close()
        }

        assertEquals(listOf(110 to false), events)
    }

    @Test
    fun sessionCloseReportsAllScreenStreamsInactive() {
        val events = mutableListOf<Pair<Int, Boolean>>()
        val sink = object : MediaSink {
            override fun onScreenStreamActive(type: Int, active: Boolean) {
                events += type to active
            }
        }
        val session = testSession()
        val engine = CarPlayMediaEngine(sink)
        val streamsField = CarPlayMediaEngine::class.java.getDeclaredField("streams").apply {
            isAccessible = true
        }
        @Suppress("UNCHECKED_CAST")
        val streams = streamsField.get(engine) as
            MutableMap<CarPlayMediaEngine.StreamKey, Closeable>
        streams[CarPlayMediaEngine.StreamKey(session, 110)] = Closeable {}
        streams[CarPlayMediaEngine.StreamKey(session, 111)] = Closeable {}
        streams[CarPlayMediaEngine.StreamKey(session, 100)] = Closeable {}

        engine.onSessionClosed(session)
        session.close()

        assertEquals(setOf(110 to false, 111 to false), events.toSet())
        assertTrue(streams.isEmpty())
    }

    @Test
    fun videoRemoteControlSessionsAreAcceptedOnlyWithVideoInCar() {
        val stream = mapOf("type" to 130L, "clientTypeUUID" to "A6B27562-B43A-4F2D-B75F-82391E250194", "controlType" to 1L)
        val engine = CarPlayMediaEngine(object : MediaSink {})
        val plain = testSession()
        val video = testSession(videoInCar = true)
        try {
            assertNull(engine.onDataStream(plain, stream))
            val first = engine.onDataStream(video, stream)
            assertEquals(130, first?.get("type"))
            assertEquals(3L, first?.get("streamID"))
            assertEquals(4L, engine.onDataStream(video, stream)?.get("streamID"))
        } finally {
            plain.close()
            video.close()
        }
    }

    @Test
    fun feedbackUsesRtpClockAcrossUnsignedWrapAndPlayoutLatency() {
        for (bits in listOf(0x10000000L, 0x20000000L, 0x40000000L, 0x800000L, 0x400000L, 0x10L)) {
            val format = AudioStreamCodec.fromFormatBits(bits, 100, "telephony")
            val engine = CarPlayMediaEngine(object : MediaSink {})
            val session = testSession()
            try {
                val origin = System.nanoTime() - 20_000_000_000L
                val metaClass = Class.forName("com.shilapi.xcertplay.airplay.CarPlayMediaEngine\$AudioMeta")
                val constructor = metaClass.declaredConstructors.single { it.parameterCount == 6 }
                    .apply { isAccessible = true }
                val meta = constructor.newInstance(100, format, 7L, 100, -100, origin)
                val field = CarPlayMediaEngine::class.java.getDeclaredField("audioMeta")
                    .apply { isAccessible = true }
                @Suppress("UNCHECKED_CAST")
                val metadata = field.get(engine) as MutableMap<CarPlayMediaEngine.StreamKey, Any>
                metadata[CarPlayMediaEngine.StreamKey(session, 100, "telephony")] = meta
                @Suppress("UNCHECKED_CAST")
                val entry = (engine.onFeedback(session)!!["streams"] as List<Map<String, Any?>>).single()
                val now = entry["timestampRawNs"] as Long
                val expected = (0xffff_ff9cL + Math.round(((now - origin) / 1e9 - 0.1) * format.rtpClockRate)) and 0xffff_ffffL
                assertEquals(format.rtpClockRate, entry["sampleRate"])
                assertEquals(expected, entry["sampleTime"])
                assertEquals(7L, entry["streamConnectionID"])

                // Exercise the actual monitor owned by the engine, not a separately configured one.
                val monitor = metaClass.getDeclaredField("position").apply { isAccessible = true }
                    .get(meta) as FeedbackPositionMonitor
                monitor.observeRtp(0)
                assertTrue(monitor.onFeedback(100, 7L, format.rtpClockRate / 2L, now)!!
                    .endsWith("aheadMs=500 overrun=true"))

                // Before playout begins, a negative starting Int remains unsigned and stationary.
                metaClass.getDeclaredField("originNs").apply { isAccessible = true }
                    .set(meta, System.nanoTime() + 60_000_000_000L)
                @Suppress("UNCHECKED_CAST")
                val waiting = (engine.onFeedback(session)!!["streams"] as List<Map<String, Any?>>).single()
                assertEquals(0xffff_ff9cL, waiting["sampleTime"])
            } finally {
                engine.onSessionClosed(session)
                session.close()
            }
        }
    }

    @Test fun aLateOldSessionCloseCannotStopItsReplacementMicrophone() {
        val stops = mutableListOf<AudioStreamId>()
        val engine = CarPlayMediaEngine(object : MediaSink {
            override fun onMicrophoneStopped(id: AudioStreamId) { stops += id }
        })
        val old = testSession()
        val replacement = testSession()
        val oldKey = CarPlayMediaEngine.StreamKey(old, 100, "speechrecognition")
        val newKey = CarPlayMediaEngine.StreamKey(replacement, 100, "speechrecognition")
        val oldConfig = microphoneConfig()
        val newConfig = microphoneConfig()
        try {
            registerMicrophone(engine, oldKey, oldConfig)
            assertTrue(startMicrophone(engine, oldKey, oldConfig) { true })
            registerMicrophone(engine, newKey, newConfig)
            assertEquals(listOf(AudioStreamId(100, "speechrecognition")), stops)
            stops.clear()
            engine.onSessionClosed(old)
            assertTrue(stops.isEmpty())
            assertFalse(startMicrophone(engine, oldKey, oldConfig) { error("Retired microphone started") })
            assertTrue(startMicrophone(engine, newKey, newConfig) { true })
            engine.onSessionClosed(replacement)
            assertEquals(listOf(AudioStreamId(100, "speechrecognition")), stops)
        } finally { old.close(); replacement.close() }
    }

    @Test fun closingOneSessionPreservesAnotherSessionsPendingMicrophone() {
        val stops = mutableListOf<AudioStreamId>()
        val engine = CarPlayMediaEngine(object : MediaSink {
            override fun onMicrophoneStopped(id: AudioStreamId) { stops += id }
        })
        val first = testSession()
        val second = testSession()
        val firstKey = CarPlayMediaEngine.StreamKey(first, 100, "speechrecognition")
        val secondKey = CarPlayMediaEngine.StreamKey(second, 100, "default")
        val firstConfig = microphoneConfig()
        val secondConfig = microphoneConfig().copy(audioType = "default")
        try {
            registerMicrophone(engine, firstKey, firstConfig)
            registerMicrophone(engine, secondKey, secondConfig)
            engine.onSessionClosed(first)
            assertEquals(listOf(AudioStreamId(100, "speechrecognition")), stops)
            assertTrue(startMicrophone(engine, secondKey, secondConfig) { true })
        } finally {
            engine.onSessionClosed(second)
            first.close(); second.close()
        }
    }

    @Test fun teardownWaitsForAnInFlightStartAndRejectsLateStarts() {
        val events = CopyOnWriteArrayList<String>()
        val engine = CarPlayMediaEngine(object : MediaSink {
            override fun onMicrophoneStopped(id: AudioStreamId) { events += "stop" }
        })
        val session = testSession()
        val key = CarPlayMediaEngine.StreamKey(session, 100, "speechrecognition")
        val config = microphoneConfig()
        val entered = CountDownLatch(1)
        val resume = CountDownLatch(1)
        val starts = Thread {
            startMicrophone(engine, key, config) {
                entered.countDown()
                assertTrue(resume.await(5, TimeUnit.SECONDS))
                events += "start"
                true
            }
        }
        val closes = Thread { engine.onSessionClosed(session) }
        try {
            registerMicrophone(engine, key, config)
            starts.start()
            assertTrue(entered.await(5, TimeUnit.SECONDS))
            closes.start()
            resume.countDown()
            starts.join(5000)
            closes.join(5000)
            assertFalse(starts.isAlive)
            assertFalse(closes.isAlive)
            assertEquals(listOf("start", "stop"), events)
            assertFalse(startMicrophone(engine, key, config) { error("Late microphone started") })
        } finally {
            resume.countDown()
            starts.join(5000)
            closes.join(5000)
            engine.onSessionClosed(session)
            session.close()
        }
    }

    private fun registerMicrophone(engine: CarPlayMediaEngine, key: CarPlayMediaEngine.StreamKey, config: MicrophoneConfig) {
        CarPlayMediaEngine::class.java.getDeclaredMethod("registerMicrophone", CarPlayMediaEngine.StreamKey::class.java,
            MicrophoneConfig::class.java).apply { isAccessible = true }.invoke(engine, key, config)
    }

    private fun startMicrophone(engine: CarPlayMediaEngine, key: CarPlayMediaEngine.StreamKey,
        config: MicrophoneConfig, start: () -> Boolean): Boolean =
        CarPlayMediaEngine::class.java.getDeclaredMethod("startMicrophoneIfCurrent", CarPlayMediaEngine.StreamKey::class.java,
            MicrophoneConfig::class.java, kotlin.jvm.functions.Function0::class.java).apply { isAccessible = true }
            .invoke(engine, key, config, start) as Boolean

    private fun microphoneConfig() = MicrophoneConfig("speechrecognition", 16_000, 1, 100, 20,
        InetAddress.getLoopbackAddress(), 9, ByteArray(32))

    private fun testSession(videoInCar: Boolean = false): AirPlaySession = AirPlaySession(
        socket = Socket(),
        config = AirPlayConfig(
            deviceName = "test",
            deviceId = "02:00:00:00:00:02",
            btMac = "02:00:00:00:00:01",
            sourceVersion = "1.0",
            main = AirPlayDisplayConfig(widthPixels = 800, heightPixels = 480),
            videoInCar = videoInCar,
        ),
        identity = AirPlayIdentity.generate(),
        pairings = PairingStore(),
        mfi = null,
        listener = object : AirPlaySessionListener {},
        media = object : AirPlayMediaHandler {},
    )
}
