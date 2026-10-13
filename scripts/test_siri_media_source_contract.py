"""Source-only Siri/input contracts. These do not replace the Android/Robolectric suites."""
from pathlib import Path
import unittest

ROOT = Path(__file__).resolve().parents[1]
SHARED = ROOT / 'shared/src/main/java/com/shilapi/xcertplay'
COMMON = ROOT / 'common/src/main/java/com/shilapi/xcertplay'

class SiriMediaSourceContractTest(unittest.TestCase):
    def test_siri_choice_is_persisted_separately_and_bad_values_fall_back(self):
        text = (SHARED / 'media/SiriMicrophoneSettings.kt').read_text()
        self.assertIn('AUTO, STANDARD, COMPATIBLE', text)
        self.assertIn('"diplay_siri_microphone"', text)
        self.assertIn('.putString(SOURCE, source.name).apply()', text)
        self.assertIn('getOrDefault(SiriMicrophoneSource.AUTO)', text)
        self.assertNotIn('GeelyInput', text)
        self.assertNotIn('GeelyVendorPolicy', text)

    def test_auto_uses_only_the_narrow_hardware_detector(self):
        text = (SHARED / 'media/SiriMicrophoneSettings.kt').read_text()
        self.assertIn('knownHardware: Boolean = GeelyHeadUnit.detected', text)
        self.assertIn('AUTO -> if (knownHardware) MediaRecorder.AudioSource.VOICE_COMMUNICATION', text)
        self.assertIn('else MediaRecorder.AudioSource.VOICE_RECOGNITION', text)
        self.assertIn('STANDARD -> MediaRecorder.AudioSource.VOICE_RECOGNITION', text)
        self.assertIn('COMPATIBLE -> MediaRecorder.AudioSource.VOICE_COMMUNICATION', text)

    def test_non_siri_sources_keep_upstream_policy(self):
        text = (SHARED / 'media/MicrophoneUplink.kt').read_text()
        policy = text.split('internal fun microphoneAudioSource(', 1)[1]
        self.assertIn('audioType == "telephony" && speakerphoneCall -> MediaRecorder.AudioSource.MIC', policy)
        self.assertIn('audioType == "telephony" -> MediaRecorder.AudioSource.VOICE_COMMUNICATION', policy)
        self.assertIn('audioType == "speechrecognition" -> siriSource.audioSource(knownHardware)', policy)
        self.assertIn('else -> MediaRecorder.AudioSource.MIC', policy)
        self.assertNotIn('GeelyVendorPolicy', policy)

    def test_selected_source_diagnostics_and_creation_fallback_are_kept(self):
        text = (SHARED / 'media/MicrophoneUplink.kt').read_text()
        self.assertLess(text.index('stats.useVoiceCommunicationSource()'), text.index('AudioRecord.getMinBufferSize('))
        self.assertIn('?: if (source == MediaRecorder.AudioSource.VOICE_RECOGNITION)', text)
        self.assertIn('createRecorder(MediaRecorder.AudioSource.VOICE_COMMUNICATION, channelMask, bufferSize)', text)

    def test_capture_reads_the_saved_siri_choice_at_stream_creation(self):
        text = (SHARED / 'media/AndroidMediaSink.kt').read_text()
        self.assertIn('siriMicrophoneSource = appContext?.let(SiriMicrophoneSettings::load)', text)

    def test_all_media_routes_share_call_session_and_avrcp_guards(self):
        text = (COMMON / 'CarPlayMediaKeys.kt').read_text()
        send = text.split('    private fun send(', 1)[1].split('    internal fun onForegroundMediaKey', 1)[0]
        for guard in ('active !== expectedController', '!stillCurrent()', 'active.activeAirPlaySessionToken() ?: return false',
                      'inCall(context) || AirPlayPersistence.loadCarBluetoothAudio(context)', 'mediaDispatch.shouldSend('):
            self.assertLess(send.index(guard), send.index('active.sendMediaButton(index, token)'))
        for route in ('FOREGROUND', 'MEDIA_SESSION', 'MEDIA_CONTROLLER', 'GEELY_PRIVATE'):
            self.assertIn('CarPlayMediaInputRoute.' + route, text)
        self.assertIn('if (sent) mediaDispatch.record(', text)
        self.assertIn('const val ECHO_WINDOW_MILLIS = 150L', text)

    def test_standard_foreground_keys_are_not_gated_by_vendor_policy(self):
        text = (COMMON / 'CarPlayHostActivity.kt').read_text()
        dispatch = text.split('override fun dispatchKeyEvent(event: KeyEvent)', 1)[1].split('private fun requestLegacySiri', 1)[0]
        self.assertNotIn('GeelyVendorPolicy.foregroundMediaKeys', dispatch)
        self.assertIn('foregroundMediaKeys.consumeContinuation(event)', dispatch)
        self.assertIn('sessionActive = activeAirPlaySession != null', dispatch)
        self.assertIn('inCall = inCall(this)', dispatch)
        self.assertIn('bluetoothAudio = AirPlayPersistence.loadCarBluetoothAudio(this)', dispatch)
        self.assertIn('CarPlayMediaKeys.onForegroundMediaKey(mediaPress, controller)', dispatch)

    def test_legacy_siri_cannot_escape_session_or_call_guards(self):
        text = (COMMON / 'CarPlayHostActivity.kt').read_text()
        self.assertIn('val siriSession = activeAirPlaySession ?: return super.dispatchKeyEvent(event)', text)
        self.assertIn('if (inCall(this)) return super.dispatchKeyEvent(event)', text)
        self.assertIn('legacySiriPresses[physicalKey] = siriController to siriSession', text)
        body = text.split('private fun requestLegacySiri(', 1)[1].split('override fun onWindowFocusChanged', 1)[0]
        self.assertIn('controller !== expectedController || activeAirPlaySession !== expectedSession || inCall(this)', body)

    def test_private_siri_rejects_retired_session_and_calls(self):
        text = (COMMON / 'CarPlayMediaKeys.kt').read_text()
        body = text.split('internal fun onExternalSiriKey(', 1)[1].split('    private val callback', 1)[0]
        for guard in ('active !== expectedController', 'active.activeAirPlaySessionToken() ?: return false', '!stillCurrent()', 'inCall(context)'):
            self.assertLess(body.index(guard), body.index('active.requestSiri(token)'))
        self.assertNotIn('loadCarBluetoothAudio', body)
        self.assertIn('controller === active && stillCurrent() && !inCall(context)', body)

    def test_controller_revalidates_session_token_and_owner_inside_command_queue(self):
        text = (SHARED / 'orchestration/CarPlayController.kt').read_text()
        body = text.split('private fun enqueueWheelCommand(', 1)[1].split('    override fun close()', 1)[0]
        self.assertIn('expectedToken != null && token !== expectedToken', body)
        queued = body.split('touchExecutor.execute {', 1)[1]
        for guard in ('executionAllowed', '!closed', '!session.isClosed', 'activeSession === session',
                      'session.mainScreenSessionToken() === token'):
            self.assertLess(queued.index(guard), queued.index('command(session)'))

if __name__ == '__main__':
    unittest.main()
