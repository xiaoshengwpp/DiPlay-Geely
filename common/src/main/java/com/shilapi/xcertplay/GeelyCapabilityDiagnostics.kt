package com.shilapi.xcertplay

import android.content.Context
import android.provider.Settings
import com.shilapi.xcertplay.adb.AdbCapability
import com.shilapi.xcertplay.media.SiriMicrophoneSettings
import com.shilapi.xcertplay.network.GeelyHeadUnit
import com.shilapi.xcertplay.telecom.PhoneAudioRoute

/** No connection attempts, permissions, private identifiers, recordings, or vehicle commands. */
internal object GeelyCapabilityDiagnostics {
    fun report(context: Context): String = buildString {
        appendLine("Known upstream input/microphone profile=${GeelyHeadUnit.detected}; " +
            "manual input opt-in=${GeelyInputSettings.manualEnabled(context)}")
        appendLine("Known profile is not confirmation for this vehicle; binding success is reported in session logs.")
        appendLine("Siri microphone preference=${SiriMicrophoneSettings.load(context)}; " +
            "actual source and capture errors are recorded in audio diagnostics, without recording contents.")
        val adbEnabled = runCatching {
            when (Settings.Global.getInt(context.contentResolver, Settings.Global.ADB_ENABLED, -1)) {
                0 -> false
                1 -> true
                else -> null
            }
        }.getOrNull()
        appendLine(AdbCapability.snapshot(adbEnabled).report())
        appendLine(PhoneAudioRoute.capabilityReport())
        append("BYD vehicle control, proprietary navigation/cluster protocols and automatic grants remain unavailable in this product.")
    }
}
