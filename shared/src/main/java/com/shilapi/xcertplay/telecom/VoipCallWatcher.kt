@file:Suppress("UNUSED_PARAMETER", "unused")

package com.shilapi.xcertplay.telecom

import android.content.Context
import android.media.AudioAttributes
import android.media.MediaRecorder

/** No recording/playback callbacks are registered by this product. */
internal class VoipCallWatcher(context: Context, private val onChange: (Boolean) -> Unit) {
    private val audio by lazy { Unit }
    private val handler by lazy { Unit }
    fun start() = Unit
    fun stop() = Unit
    companion object {
        private const val TAG = "DiPlay-PhoneAudio"

        fun voipActive(recordingSources: List<Int>, playbackUsages: List<Int>): Boolean =
            recordingSources.any { it == MediaRecorder.AudioSource.VOICE_COMMUNICATION } &&
                playbackUsages.any { it == AudioAttributes.USAGE_VOICE_COMMUNICATION }
    }
}
