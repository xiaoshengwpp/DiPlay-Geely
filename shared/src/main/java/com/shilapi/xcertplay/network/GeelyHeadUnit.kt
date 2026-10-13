package com.shilapi.xcertplay.network

import android.os.Build

/**
 * Exact known firmware family for upstream Geely input and Siri microphone compatibility.
 * Detection does not enable an integration: each consumer still checks its own capability/setting.
 * Unknown Geely/Lynk models are not covered by matching the brand, a substring, or one identifier.
 */
object GeelyHeadUnit {
    val detected: Boolean get() = runCatching {
        GeelyCapability.knownHardware(Build.PRODUCT, Build.MODEL, Build.MANUFACTURER, Build.VERSION.SDK_INT)
    }.getOrDefault(false)
}
