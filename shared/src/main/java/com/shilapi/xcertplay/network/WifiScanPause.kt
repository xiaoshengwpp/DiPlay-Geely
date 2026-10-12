package com.shilapi.xcertplay.network

import android.content.Context
import android.os.Build
import com.shilapi.xcertplay.adb.LocalAdb

/** Inert compatibility interface. The Geely build never changes Wi-Fi scan or autojoin settings. */
@Suppress("UNUSED_PARAMETER")
internal class WifiScanPause(
    context: Context,
    log: (String) -> Unit,
    control: WifiScanPauseControl = ProcessWifiScanPause,
) {
    @Synchronized
    fun pause() = Unit

    @Synchronized
    fun close() = Unit

    internal companion object {
        internal const val TAG = "DiPlayWifiScan"
        internal const val HEADER = "DIPLAY_WIFI_SCAN_V1"

        /** Retained timeout constant for source compatibility; no helper is launched. */
        internal const val HELPER_TIMEOUT_MS = 15_000

        fun eligible(backend: WirelessHotspotBackend, sdk: Int = Build.VERSION.SDK_INT): Boolean =
            false

        fun restoreIfNeeded(context: Context) = Unit

        fun command(apk: String, enabled: Boolean): String = ""

        /** Reads the helper's protocol line; other output, such as runtime warnings, is ignored. */
        fun parse(output: String?): WifiScanSwitchResult {
            val prefix = "$HEADER|"
            val line = output?.lineSequence()?.map(String::trim)?.lastOrNull { it.startsWith(prefix) }
                ?: return WifiScanSwitchResult.UNKNOWN
            return WifiScanSwitchResult.entries.firstOrNull { it.name == line.removePrefix(prefix) }
                ?: WifiScanSwitchResult.UNKNOWN
        }

        /** Why the helper did not answer, from what the adb connection reported. */
        fun unanswered(access: LocalAdb.Access?): WifiScanSwitchResult = when (access) {
            LocalAdb.Access.NOT_APPROVED -> WifiScanSwitchResult.ADB_NOT_APPROVED
            LocalAdb.Access.UNREACHABLE -> WifiScanSwitchResult.ADB_OFF
            LocalAdb.Access.UNSUPPORTED -> WifiScanSwitchResult.ADB_PAIRING_ONLY
            // Connected, so the helper may have run before the link failed.
            LocalAdb.Access.READY, null -> WifiScanSwitchResult.UNKNOWN
        }
    }
}

internal interface WifiScanPauseControl {
    fun acquire(app: Context, owner: Any, current: () -> Boolean, log: (String) -> Unit)
    fun release(app: Context, owner: Any, log: (String) -> Unit)
}

/** Default control deliberately has no service, worker, journal, or ADB dependency. */
private object ProcessWifiScanPause : WifiScanPauseControl {
    override fun acquire(app: Context, owner: Any, current: () -> Boolean, log: (String) -> Unit) = Unit

    override fun release(app: Context, owner: Any, log: (String) -> Unit) = Unit
}
