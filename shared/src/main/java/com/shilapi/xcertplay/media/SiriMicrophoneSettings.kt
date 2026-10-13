package com.shilapi.xcertplay.media

import android.content.Context
import android.media.MediaRecorder
import com.shilapi.xcertplay.network.GeelyHeadUnit

/** Siri capture only. Phone calls and other microphone streams keep their own source policy. */
enum class SiriMicrophoneSource {
    AUTO, STANDARD, COMPATIBLE;

    fun audioSource(knownHardware: Boolean = GeelyHeadUnit.detected): Int = when (this) {
        AUTO -> if (knownHardware) MediaRecorder.AudioSource.VOICE_COMMUNICATION
            else MediaRecorder.AudioSource.VOICE_RECOGNITION
        STANDARD -> MediaRecorder.AudioSource.VOICE_RECOGNITION
        COMPATIBLE -> MediaRecorder.AudioSource.VOICE_COMMUNICATION
    }
}

/** Read for each new microphone stream; changing this never interrupts a running Siri request. */
object SiriMicrophoneSettings {
    private const val PREFS = "diplay_siri_microphone"
    private const val SOURCE = "source"

    fun load(context: Context): SiriMicrophoneSource = runCatching {
        val value = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString(SOURCE, SiriMicrophoneSource.AUTO.name)
        SiriMicrophoneSource.valueOf(value ?: SiriMicrophoneSource.AUTO.name)
    }.getOrDefault(SiriMicrophoneSource.AUTO)

    fun save(context: Context, source: SiriMicrophoneSource) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putString(SOURCE, source.name).apply()
    }
}
