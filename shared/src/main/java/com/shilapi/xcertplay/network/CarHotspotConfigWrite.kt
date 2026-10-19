package com.shilapi.xcertplay.network

import android.content.Context
import android.os.Build
import com.shilapi.xcertplay.orchestration.ManualHotspotBand
import com.shilapi.xcertplay.orchestration.ManualHotspotSecurity

data class HotspotCredentials(
    val ssid: String,
    val passphrase: String,
    val security: ManualHotspotSecurity,
    val band: ManualHotspotBand = ManualHotspotBand.AUTO,
)

/** Compatibility API only. Vehicle hotspot configuration must not be mutated by this Geely build. */
@Suppress("UNUSED_PARAMETER")
object CarHotspotConfigWrite {
    enum class Result(val diagnostic: String) {
        WRITTEN("car hotspot configuration written"),
        SKIPPED("car hotspot configuration write skipped"),
        UNSUPPORTED_SDK("this Android version does not allow writing the car hotspot configuration"),
        ADB_UNAVAILABLE("ADB access is missing for the car hotspot configuration write"),
        FAILED("the car hotspot configuration write failed"),
        OPEN_REJECTED("an open hotspot does not overwrite the car's password"),
    }

    /** The result of a write plus the SSID that is actually saved, which the car may have kept. */
    data class WriteOutcome(val result: Result, val ssid: String? = null)

    fun supported(sdk: Int = Build.VERSION.SDK_INT): Boolean = false

    fun write(
        context: Context,
        credentials: HotspotCredentials,
        deadline: Long = Long.MAX_VALUE,
        isCancelled: () -> Boolean = { false },
        sdk: Int = Build.VERSION.SDK_INT,
        log: (String) -> Unit,
    ): WriteOutcome = WriteOutcome(Result.UNSUPPORTED_SDK)
}
