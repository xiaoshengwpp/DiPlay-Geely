"""Source wiring contracts supplement behavioral JVM audio tests."""
from pathlib import Path
import unittest

ROOT = Path(__file__).resolve().parents[1]

class AudioPartialPortTest(unittest.TestCase):
    def test_audio_only_selects_short_wait_and_call_log_name(self):
        source = (ROOT / 'shared/src/main/java/com/shilapi/xcertplay/media/AndroidMediaSink.kt').read_text()
        video, audio = source.split('private class AudioRenderer(', 1)
        self.assertNotIn('timeoutNs = AUDIO_INPUT_WAIT_NS', video)
        self.assertEqual(1, audio.count('timeoutNs = AUDIO_INPUT_WAIT_NS'))
        self.assertIn('const val AUDIO_INPUT_WAIT_NS = 100_000_000L', audio)
        self.assertIn('if (mappedChannel == AudioChannel.PHONE) "CALL" else mappedChannel?.name', audio)
        self.assertIn('channel=$channelName ', audio)

    def test_engine_monitor_and_feedback_share_rtp_clock(self):
        source = (ROOT / 'shared/src/main/java/com/shilapi/xcertplay/airplay/CarPlayMediaEngine.kt').read_text()
        self.assertIn('FeedbackPositionMonitor(format.rtpClockRate)', source)
        feedback = source.split('override fun onFeedback(', 1)[1].split('override fun onTeardown(', 1)[0]
        self.assertIn('val clockRate = meta.format.rtpClockRate', feedback)
        self.assertIn('"sampleRate" to clockRate', feedback)
        self.assertIn('Math.round(elapsedSec * clockRate)', feedback)
        self.assertNotIn('meta.format.sampleRate', feedback)

if __name__ == '__main__':
    unittest.main()
