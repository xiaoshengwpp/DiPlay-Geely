package com.shilapi.xcertplay.media

import android.Manifest
import android.media.AudioAttributes
import android.media.AudioDeviceInfo
import android.media.AudioFormat as AndroidAudioFormat
import android.media.AudioManager
import android.media.AudioRouting
import android.media.AudioTrack
import com.shilapi.xcertplay.airplay.AudioCodecKind
import com.shilapi.xcertplay.airplay.AudioFormat
import com.shilapi.xcertplay.airplay.AudioStreamId
import com.shilapi.xcertplay.airplay.MicrophoneConfig
import java.net.InetAddress
import java.util.concurrent.CopyOnWriteArrayList
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.shadows.AudioDeviceInfoBuilder
import org.robolectric.shadows.ShadowAudioRecord
import org.robolectric.shadows.ShadowAudioTrack

/** #797, narrowed for Geely: the existing explicit preference and current call/track own any route change. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [30], manifest = Config.NONE)
class CallEarpieceFallbackTest {
    private val context get() = RuntimeEnvironment.getApplication()
    private val telephony = AudioStreamId(100, "telephony")
    private val diagnostics = CopyOnWriteArrayList<String>()
    private val tracks = mutableListOf<AudioTrack>()
    private lateinit var manager: AudioManager
    private lateinit var sink: AndroidMediaSink
    private var speakerRequested = false
    private var failDiagnostics = false
    private val earpiece = device(AudioDeviceInfo.TYPE_BUILTIN_EARPIECE)
    private val speaker = device(AudioDeviceInfo.TYPE_BUILTIN_SPEAKER)
    private val headset = device(AudioDeviceInfo.TYPE_WIRED_HEADSET)

    @Before fun setUp() {
        shadowOf(context).grantPermissions(Manifest.permission.RECORD_AUDIO, Manifest.permission.MODIFY_AUDIO_SETTINGS)
        manager = context.getSystemService(AudioManager::class.java)
        ShadowAudioRecord.setSourceProvider { _ ->
            object : ShadowAudioRecord.AudioRecordSource {
                override fun readInByteArray(buffer: ByteArray, offset: Int, size: Int, blocking: Boolean): Int {
                    Thread.sleep(5)
                    return 0
                }
            }
        }
        sink = AndroidMediaSink(context = context, onAudioDiagnostic = {
            if (failDiagnostics) throw IllegalStateException("test diagnostic failure")
            diagnostics.add(it)
        }, callSpeakerphone = { speakerRequested })
    }

    @After fun tearDown() {
        sink.close()
        tracks.forEach(AudioTrack::release)
        ShadowAudioTrack.setRoutedDevice(null)
        ShadowAudioRecord.clearSource()
    }

    @Test fun defaultOffOnlyReportsTheEarpieceOnceAndNeverSelectsASpeaker() {
        startCall()
        val renderer = callRenderer()
        route(renderer, earpiece)
        route(renderer, earpiece)
        @Suppress("DEPRECATION")
        assertFalse(manager.isSpeakerphoneOn)
        assertEquals(listOf("Audio: call routed to the earpiece; loudspeaker=false requested=false stream=$telephony"),
            routeDiagnostics())
    }

    @Test fun normalSpeakerAndExternalRoutesAreLeftAlone() {
        startCall()
        val renderer = callRenderer()
        route(renderer, speaker)
        route(renderer, headset)
        assertTrue(routeDiagnostics().isEmpty())
    }

    @Test fun nothingMovesOutsideACallOrAfterItsMicrophoneStops() {
        val renderer = callRenderer()
        route(renderer, earpiece)
        startCall()
        val callback = callback(renderer)
        sink.onMicrophoneStopped(telephony)
        callback()
        route(renderer, earpiece)
        assertTrue(routeDiagnostics().isEmpty())
    }

    @Test fun enablingTheSettingDuringACallWaitsForTheNextCall() {
        startCall()
        val renderer = callRenderer()
        speakerRequested = true
        route(renderer, earpiece)
        assertTrue(routeDiagnostics().single().contains("requested=false"))
        @Suppress("DEPRECATION")
        assertFalse(manager.isSpeakerphoneOn)
        sink.onMicrophoneStopped(telephony)
        startCall()
        @Suppress("DEPRECATION")
        assertTrue(manager.isSpeakerphoneOn)
    }

    @Test @Config(sdk = [33], manifest = Config.NONE)
    fun anExplicitChoiceCanRetryWhenTheSpeakerBecomesAvailableAndRestoreThePriorRoute() {
        val renderer = prepareOptedInCall()
        enableSpeaker()
        route(renderer, earpiece)
        assertEquals(AudioDeviceInfo.TYPE_BUILTIN_SPEAKER, manager.communicationDevice?.type)
        route(renderer, earpiece)
        assertEquals(1, routeDiagnostics().size)
        sink.onMicrophoneStopped(telephony)
        assertEquals(AudioDeviceInfo.TYPE_BUILTIN_EARPIECE, manager.communicationDevice?.type)
    }

    @Test @Config(sdk = [33], manifest = Config.NONE)
    fun downlinkBeforeTheMicrophoneBindsToTheCallThatActuallyStarts() {
        speakerRequested = true
        shadowOf(manager).setAvailableCommunicationDevices(listOf(earpiece))
        assertTrue(manager.setCommunicationDevice(earpiece))
        val renderer = callRenderer()
        route(renderer, earpiece)
        assertTrue(routeDiagnostics().isEmpty())
        startCall()
        enableSpeaker()
        route(renderer, earpiece)
        assertEquals(AudioDeviceInfo.TYPE_BUILTIN_SPEAKER, manager.communicationDevice?.type)
    }

    @Test @Config(sdk = [33], manifest = Config.NONE)
    fun disablingTheChoiceDuringACallAlsoWaitsForTheNextCall() {
        val renderer = prepareOptedInCall()
        speakerRequested = false
        enableSpeaker()
        route(renderer, earpiece)
        assertTrue(routeDiagnostics().single().contains("requested=true"))
        sink.onMicrophoneStopped(telephony)
        startCall()
        route(renderer, earpiece)
        assertTrue(routeDiagnostics().last().contains("requested=false"))
    }

    @Test @Config(sdk = [33], manifest = Config.NONE)
    fun aFailedRetryIsNotRepeatedWithinTheSameCall() {
        val renderer = prepareOptedInCall()
        route(renderer, earpiece) // No speaker exists yet: fail safely and try only once.
        enableSpeaker()
        route(renderer, earpiece)
        assertEquals(AudioDeviceInfo.TYPE_BUILTIN_EARPIECE, manager.communicationDevice?.type)
        assertEquals(1, routeDiagnostics().size)
        assertTrue(routeDiagnostics().single().contains("loudspeaker=false requested=true"))
    }

    @Test @Config(sdk = [33], manifest = Config.NONE)
    fun aNewerCommunicationDeviceIsNotOverriddenByAnOldTrackEvent() {
        val renderer = prepareOptedInCall()
        shadowOf(manager).setAvailableCommunicationDevices(listOf(earpiece, speaker, headset))
        assertTrue(manager.setCommunicationDevice(headset))
        route(renderer, earpiece)
        assertEquals(AudioDeviceInfo.TYPE_WIRED_HEADSET, manager.communicationDevice?.type)
        assertTrue(routeDiagnostics().isEmpty())
    }

    @Test @Config(sdk = [33], manifest = Config.NONE)
    fun endingTheCallPreservesARouteTheDriverChoseAfterFallback() {
        val renderer = prepareOptedInCall()
        shadowOf(manager).setAvailableCommunicationDevices(listOf(earpiece, speaker, headset))
        route(renderer, earpiece)
        assertEquals(AudioDeviceInfo.TYPE_BUILTIN_SPEAKER, manager.communicationDevice?.type)
        assertTrue(manager.setCommunicationDevice(headset))
        sink.onMicrophoneStopped(telephony)
        assertEquals(AudioDeviceInfo.TYPE_WIRED_HEADSET, manager.communicationDevice?.type)
    }

    @Test @Config(sdk = [33], manifest = Config.NONE)
    fun aCallbackRetainedFromAnOlderCallCannotActOnAReusedStreamId() {
        val renderer = prepareOptedInCall()
        val stale = callback(renderer)
        sink.onMicrophoneStopped(telephony)
        startCall() // Reuse even the renderer: the call generation, not its stream ID, must fence this.
        enableSpeaker()
        ShadowAudioTrack.setRoutedDevice(earpiece)
        stale()
        assertEquals(AudioDeviceInfo.TYPE_BUILTIN_EARPIECE, manager.communicationDevice?.type)
        assertTrue(routeDiagnostics().isEmpty())
        route(renderer, earpiece)
        assertEquals(AudioDeviceInfo.TYPE_BUILTIN_SPEAKER, manager.communicationDevice?.type)
    }

    @Test fun closedAndReplacedRenderersCannotReportOrChangeTheCurrentCall() {
        startCall()
        val old = callRenderer()
        val stale = callback(old)
        val current = callRenderer(sampleRate = 24_000)
        ShadowAudioTrack.setRoutedDevice(earpiece)
        stale()
        route(old, earpiece)
        assertTrue(routeDiagnostics().isEmpty())
        route(current, earpiece)
        assertEquals(1, routeDiagnostics().size)
    }

    @Test fun aRemovedRendererAndAClosedSinkRejectQueuedCallbacks() {
        startCall()
        val old = callRenderer()
        val removed = callback(old)
        sink.onAudioStopped(telephony)
        ShadowAudioTrack.setRoutedDevice(earpiece)
        removed()
        assertTrue(routeDiagnostics().isEmpty())
        val current = callRenderer()
        val closed = callback(current)
        sink.close()
        closed()
        assertTrue(routeDiagnostics().isEmpty())
        assertEquals(AudioManager.MODE_NORMAL, manager.mode)
    }

    @Test fun anOldTrackNotificationDoesNotDescribeTheCurrentTrack() {
        startCall()
        val renderer = callRenderer()
        val oldTrack = track(renderer)
        setTrack(renderer, newTrack())
        ShadowAudioTrack.setRoutedDevice(earpiece)
        notifyRoute(renderer, oldTrack)
        assertTrue(routeDiagnostics().isEmpty())
        notifyRoute(renderer, track(renderer))
        assertEquals(1, routeDiagnostics().size)
    }

    @Test fun aCurrentNonEarpieceRouteAndAnExternallyChangedModeRejectTheCallback() {
        startCall()
        val renderer = callRenderer()
        val queued = callback(renderer)
        ShadowAudioTrack.setRoutedDevice(headset)
        queued()
        ShadowAudioTrack.setRoutedDevice(earpiece)
        manager.mode = AudioManager.MODE_NORMAL
        queued()
        assertTrue(routeDiagnostics().isEmpty())
    }

    @Test fun aFailedDiagnosticCannotEscapeTheRoutingCallback() {
        startCall()
        val renderer = callRenderer()
        failDiagnostics = true
        route(renderer, earpiece)
        @Suppress("DEPRECATION")
        assertFalse(manager.isSpeakerphoneOn)
    }

    private fun prepareOptedInCall(): Any {
        speakerRequested = true
        shadowOf(manager).setAvailableCommunicationDevices(listOf(earpiece))
        assertTrue(manager.setCommunicationDevice(earpiece))
        startCall()
        return callRenderer()
    }

    private fun enableSpeaker() = shadowOf(manager).setAvailableCommunicationDevices(listOf(earpiece, speaker))
    private fun routeDiagnostics() = diagnostics.filter { it.startsWith("Audio: call routed") }

    private fun startCall() = sink.onMicrophoneStarted(telephony, MicrophoneConfig(
        audioType = "telephony", sampleRate = 16_000, channels = 1, payloadType = 100, frameMillis = 20,
        host = InetAddress.getLoopbackAddress(), port = 9, key = ByteArray(32),
    ))

    /** Use the sink's renderer and a real shadowed track, without starting an unrelated PCM worker. */
    private fun callRenderer(sampleRate: Int = 16_000): Any = AndroidMediaSink::class.java
        .getDeclaredMethod("audioRenderer", AudioStreamId::class.java, AudioFormat::class.java)
        .apply { isAccessible = true }
        .invoke(sink, telephony, AudioFormat(AudioCodecKind.LPCM, sampleRate, 1, 100, "telephony"))!!
        .also { setTrack(it, newTrack()) }

    private fun newTrack(): AudioTrack = AudioTrack.Builder()
        .setAudioAttributes(AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_VOICE_COMMUNICATION)
            .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH).build())
        .setAudioFormat(AndroidAudioFormat.Builder().setSampleRate(16_000)
            .setChannelMask(AndroidAudioFormat.CHANNEL_OUT_MONO).setEncoding(AndroidAudioFormat.ENCODING_PCM_16BIT).build())
        .setBufferSizeInBytes(4096).setTransferMode(AudioTrack.MODE_STREAM).build().also(tracks::add)

    private fun setTrack(renderer: Any, track: AudioTrack) {
        renderer.javaClass.getDeclaredField("track").apply { isAccessible = true }.set(renderer, track)
    }

    private fun track(renderer: Any): AudioTrack = renderer.javaClass.getDeclaredField("track")
        .apply { isAccessible = true }.get(renderer) as AudioTrack

    @Suppress("UNCHECKED_CAST")
    private fun callback(renderer: Any): () -> Unit = renderer.javaClass.getDeclaredField("onRoutedToEarpiece")
        .apply { isAccessible = true }.get(renderer) as () -> Unit

    private fun route(renderer: Any, device: AudioDeviceInfo) {
        ShadowAudioTrack.setRoutedDevice(device)
        notifyRoute(renderer, track(renderer))
    }

    private fun notifyRoute(renderer: Any, track: AudioTrack) {
        renderer.javaClass.getDeclaredMethod("checkCallRoute", AudioRouting::class.java)
            .apply { isAccessible = true }.invoke(renderer, track)
    }

    private fun device(type: Int): AudioDeviceInfo = AudioDeviceInfoBuilder.newBuilder().setType(type).build()
}
