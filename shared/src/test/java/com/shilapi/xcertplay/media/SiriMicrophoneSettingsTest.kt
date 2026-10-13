package com.shilapi.xcertplay.media

import android.content.Context
import android.media.MediaRecorder
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28, 33], manifest = Config.NONE)
class SiriMicrophoneSettingsTest {
    private val context get() = RuntimeEnvironment.getApplication()

    @Before fun clear() {
        context.getSharedPreferences("diplay_siri_microphone", Context.MODE_PRIVATE).edit().clear().commit()
    }

    @Test fun absentOrInvalidSavedChoiceUsesAuto() {
        assertEquals(SiriMicrophoneSource.AUTO, SiriMicrophoneSettings.load(context))
        val prefs = context.getSharedPreferences("diplay_siri_microphone", Context.MODE_PRIVATE)
        prefs.edit().putString("source", "future-or-corrupt-value").commit()
        assertEquals(SiriMicrophoneSource.AUTO, SiriMicrophoneSettings.load(context))
        prefs.edit().putInt("source", 42).commit()
        assertEquals(SiriMicrophoneSource.AUTO, SiriMicrophoneSettings.load(context))
    }

    @Test fun eachChoicePersistsIndependently() {
        for (source in SiriMicrophoneSource.values()) {
            SiriMicrophoneSettings.save(context, source)
            assertEquals(source, SiriMicrophoneSettings.load(context))
        }
    }

    @Test fun autoUsesCompatibilityOnlyForKnownHardware() {
        assertEquals(MediaRecorder.AudioSource.VOICE_RECOGNITION, SiriMicrophoneSource.AUTO.audioSource(false))
        assertEquals(MediaRecorder.AudioSource.VOICE_COMMUNICATION, SiriMicrophoneSource.AUTO.audioSource(true))
    }

    @Test fun explicitChoiceOverridesEitherHardwareDefault() {
        for (known in listOf(false, true)) {
            assertEquals(MediaRecorder.AudioSource.VOICE_RECOGNITION, SiriMicrophoneSource.STANDARD.audioSource(known))
            assertEquals(MediaRecorder.AudioSource.VOICE_COMMUNICATION, SiriMicrophoneSource.COMPATIBLE.audioSource(known))
        }
    }

    @Test fun siriChoiceNeverChangesOtherStreamSources() {
        for (source in SiriMicrophoneSource.values()) for (known in listOf(false, true)) {
            assertEquals(MediaRecorder.AudioSource.VOICE_COMMUNICATION,
                microphoneAudioSource("telephony", false, source, known))
            assertEquals(MediaRecorder.AudioSource.MIC, microphoneAudioSource("telephony", true, source, known))
            assertEquals(MediaRecorder.AudioSource.MIC, microphoneAudioSource("default", false, source, known))
            assertEquals(source.audioSource(known), microphoneAudioSource("speechrecognition", false, source, known))
        }
    }
}
