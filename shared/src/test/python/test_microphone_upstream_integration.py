"""Source wiring checks; behavioral JVM/Robolectric tests remain the runtime authority."""
from pathlib import Path
import unittest

ROOT = Path(__file__).resolve().parents[4]
JAVA = ROOT / "shared/src/main/java/com/shilapi/xcertplay"


class MicrophoneUpstreamIntegrationTest(unittest.TestCase):
    def test_retry_budgets_and_dead_recorder_boundary_are_kept(self):
        source = (JAVA / "media/MicrophoneUplink.kt").read_text()
        self.assertIn("count != AudioRecord.ERROR_DEAD_OBJECT", source)
        self.assertIn("const val MAX_READ_RESTARTS = 3", source)
        self.assertIn("const val MAX_NO_AUDIO_RESTARTS = 3", source)
        self.assertIn("Thread.sleep(EMPTY_READ_PAUSE_MILLIS)", source)

    def test_restart_rechecks_ownership_after_the_pause(self):
        source = (JAVA / "media/MicrophoneUplink.kt").read_text()
        restart = source.split("private fun restartRecording(", 1)[1].split("private fun sendFrame", 1)[0]
        after_pause = restart.split("restartPause()", 1)[1]
        self.assertIn("synchronized(this)", after_pause)
        self.assertLess(after_pause.index("!running.get() || this.recorder !== recorder"),
                        after_pause.index("recorder.startRecording()"))
        close = source.split("override fun close()", 1)[1].split("private fun release()", 1)[0]
        self.assertIn("synchronized(this)", close)
        self.assertIn("closed = true", close)
        self.assertIn("if (closed) return false", source)

    def test_both_restarts_drop_partial_pcm_and_reset_the_capture_clock(self):
        source = (JAVA / "media/MicrophoneUplink.kt").read_text()
        capture = source.split("private fun capture(", 1)[1].split("private fun restartRecording", 1)[0]
        self.assertEqual(2, capture.count("clock?.reset()"))
        self.assertGreaterEqual(capture.count("filled = 0"), 4)
        clock = source.split("internal class CaptureClock", 1)[1]
        reset = clock.split("fun reset()", 1)[1].split("fun read(", 1)[0]
        self.assertIn("samplesRead = 0L", reset)

    def test_first_downlink_and_silent_timer_share_the_ownership_fence(self):
        source = (JAVA / "airplay/CarPlayMediaEngine.kt").read_text()
        audio = source.split("override fun onAudio(", 1)[1].split("override fun onBufferedAudio(", 1)[0]
        self.assertEqual(2, audio.count("startMicrophoneIfCurrent("))
        start = source.split("private fun startMicrophoneIfCurrent(", 1)[1].split("private fun stopMicrophone(", 1)[0]
        self.assertIn("pendingMicrophone[key] !== config", start)
        self.assertIn("microphoneOwners[AudioStreamId(key.type, key.audioType)] !== config", start)
        self.assertIn("key.session.isClosed", start)

    def test_session_close_is_scoped_and_cannot_stop_a_replacement_owner(self):
        source = (JAVA / "airplay/CarPlayMediaEngine.kt").read_text()
        close = source.split("override fun onSessionClosed(", 1)[1].split("private fun registerMicrophone(", 1)[0]
        self.assertIn("pendingMicrophone.keys.filter { it.session === session }.forEach(::stopMicrophone)", close)
        self.assertNotIn("pendingMicrophone.clear()", close)
        self.assertNotIn("audioMeta.clear()", close)
        stop = source.split("private fun stopMicrophone(", 1)[1].split("private fun detachBufferedLocked(", 1)[0]
        self.assertLess(stop.index("microphoneOwners[id] === config"), stop.index("sink.onMicrophoneStopped(id)"))

    def test_loudspeaker_remains_opt_in_and_restores_the_previous_route(self):
        sink = (JAVA / "media/AndroidMediaSink.kt").read_text()
        speaker = (JAVA / "media/CallSpeaker.kt").read_text()
        self.assertIn("callSpeakerphone: () -> Boolean = { false }", sink)
        self.assertIn("if (callSpeakerRequested) callSpeaker = CallSpeaker.on(manager)", sink)
        self.assertEqual(sink.count("if (callSpeakerRequested) callSpeaker = CallSpeaker.on(manager)"), 2)
        self.assertIn("callSpeakerRequested = runCatching(callSpeakerphone).getOrDefault(false)", sink)
        self.assertIn("communicationModeGeneration != generation", sink)
        self.assertIn("audioRenderers[id] !== renderer", sink)
        self.assertIn("!renderer.isActiveEarpieceRoute()", sink)
        self.assertIn("val before = manager.communicationDevice", speaker)
        self.assertIn("manager.setCommunicationDevice(before)", speaker)
        self.assertIn("current.id == speaker.id && current.type == speaker.type", speaker)

    def test_telephony_still_waits_for_downlink(self):
        source = (JAVA / "airplay/MicrophoneStart.kt").read_text()
        self.assertIn('startsWithoutDownlink(audioType: String): Boolean = audioType != "telephony"', source)
        self.assertIn("claimed.compareAndSet(false, true)", source)


if __name__ == "__main__":
    unittest.main()
