package com.shilapi.xcertplay.network

/** Compatibility entry point; scan control is unsupported and performs no work. */
object WifiScanPauseMain {
    internal const val PAUSE = "pause"
    internal const val RESTORE = "restore"

    @Suppress("UNUSED_PARAMETER")
    @JvmStatic fun main(args: Array<String>) = Unit
}

/** Disabled adapter. Injected objects cannot enable reflective platform reads or writes. */
@Suppress("UNUSED_PARAMETER")
internal class WifiAutojoinSwitch(
    wifi: Any,
    listenerType: Class<*>? = null,
    queryTimeoutMillis: Long = 5_000,
) {
    val method: String? get() = null
    fun set(enabled: Boolean): WifiScanSwitchResult = WifiScanSwitchResult.UNSUPPORTED
    internal fun read(): Boolean? = null
}
