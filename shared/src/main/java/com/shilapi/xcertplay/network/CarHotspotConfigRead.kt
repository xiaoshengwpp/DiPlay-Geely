package com.shilapi.xcertplay.network

import android.content.Context
import android.os.Build
import com.shilapi.xcertplay.adb.LocalAdb
import com.shilapi.xcertplay.orchestration.ManualHotspotSecurity

data class CarHotspotSavedCredentials(
    val ssid: String,
    val passphrase: String,
    val security: ManualHotspotSecurity,
)

/** Compatibility API only. Reading vehicle hotspot credentials is unsupported in the Geely build. */
@Suppress("UNUSED_PARAMETER")
object CarHotspotConfigRead {
    enum class Reason { UNSUPPORTED_SDK, ADB_UNAVAILABLE, UNAVAILABLE }

    data class Outcome(
        val credentials: CarHotspotSavedCredentials? = null,
        val reason: Reason? = null,
        val access: LocalAdb.Access? = null,
    )

    fun read(context: Context, sdk: Int = Build.VERSION.SDK_INT, log: (String) -> Unit): Outcome =
        Outcome(reason = Reason.UNSUPPORTED_SDK)
}
