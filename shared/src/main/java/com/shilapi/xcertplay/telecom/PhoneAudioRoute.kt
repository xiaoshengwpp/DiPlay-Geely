package com.shilapi.xcertplay.telecom

import android.content.Context
import android.util.Log
import com.shilapi.xcertplay.hud.BydOutputSettings

/** Why DiPlay wants the car in its phone audio state. */
enum class PhoneAudioReason {
    /** A call on the iPhone is ringing, dialing or connected over CarPlay. */
    CARPLAY_CALL,

    /** Another app on the head unit holds a voice-over-IP call. */
    VOIP_APP,
}

/** Starts and ends the Telecom call that puts the car's audio into its phone state. */
internal interface PhoneAudioBackend {
    /** Blocks until the call is active; false when Telecom refused it. */
    fun start(): Boolean

    fun stop()

    /** False once the system has ended the call on its own. */
    fun isActive(): Boolean
}

internal interface RouteScheduler {
    fun execute(block: () -> Unit)

    /** Runs [block] after [delayMillis]; the returned function cancels it. */
    fun schedule(delayMillis: Long, block: () -> Unit): () -> Unit
}

/**
 * Holds one Telecom call open while any enabled reason wants the car's phone audio state, and ends it
 * shortly after the last reason goes away. All backend work runs in order on the scheduler.
 *
 * A refused call is not retried at once: [carPlayCallsUseCarAudio] turns false for [failureBackoffMillis], so
 * calls in that window fall back to DiPlay's own echo canceller instead of having neither.
 */
internal class PhoneAudioRouteController(
    private val backend: PhoneAudioBackend,
    private val scheduler: RouteScheduler,
    private val enabled: (PhoneAudioReason) -> Boolean,
    private val now: () -> Long = System::currentTimeMillis,
    private val releaseDelayMillis: Long = RELEASE_DELAY_MILLIS,
    private val failureBackoffMillis: Long = FAILURE_BACKOFF_MILLIS,
) {
    private val lock = Any()
    private val wanted = mutableSetOf<PhoneAudioReason>()
    private var held = false // scheduler thread
    private var cancelRelease: (() -> Unit)? = null // scheduler thread
    @Volatile private var failedAt = NEVER

    fun setWanted(reason: PhoneAudioReason, value: Boolean) {
        synchronized(lock) { if (value) wanted += reason else wanted -= reason }
        evaluate()
    }

    /** A setting changed: start or end the call to match. */
    fun settingsChanged() = evaluate()

    /** True while the CarPlay call's echo is left to the car, so DiPlay's own canceller must stay out of it. */
    fun carPlayCallsUseCarAudio(): Boolean = enabled(PhoneAudioReason.CARPLAY_CALL) && !failedRecently()

    private fun failedRecently(): Boolean = failedAt != NEVER && now() - failedAt < failureBackoffMillis

    private fun needed(): Boolean =
        synchronized(lock) { wanted.toList() }.any(enabled) && !failedRecently()

    private fun evaluate() {
        scheduler.execute {
            if (needed()) {
                cancelRelease?.invoke()
                cancelRelease = null
                if (!held || !backend.isActive()) {
                    held = false
                    if (backend.start()) {
                        held = true
                        Log.i(TAG, "car phone audio state on")
                    } else {
                        failedAt = now()
                        Log.w(TAG, "Telecom refused the call that selects the car's phone audio state")
                    }
                }
            } else if (held && cancelRelease == null) {
                cancelRelease = scheduler.schedule(releaseDelayMillis) {
                    cancelRelease = null
                    if (!needed()) {
                        backend.stop()
                        held = false
                        Log.i(TAG, "car phone audio state off")
                    }
                }
            }
        }
    }

    companion object {
        private const val TAG = "DiPlay-PhoneAudio"
        private const val NEVER = Long.MIN_VALUE

        /** Bridges the gap between two calls, or a call and call waiting, without leaving and re-entering the state. */
        const val RELEASE_DELAY_MILLIS = 2_000L
        const val FAILURE_BACKOFF_MILLIS = 5 * 60_000L
    }
}

/** No-op product facade. The independent controller above remains available for pure tests. */
object PhoneAudioRoute {
    private val executor by lazy { Unit }
    private var controller: PhoneAudioRouteController? = null
    private var watcher: VoipCallWatcher? = null
    fun attach(context: Context) = Unit
    fun carPlayCall(active: Boolean) = Unit
    internal fun voipApp(active: Boolean) = Unit
    fun settingsChanged(context: Context) = Unit
    fun carPlayCallsUseCarAudio(): Boolean = false
    internal fun ownCarPlayCallActive(): Boolean = false
}
