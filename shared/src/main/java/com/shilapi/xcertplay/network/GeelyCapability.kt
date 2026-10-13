package com.shilapi.xcertplay.network

/** Pure capability rules. A brand name alone is never a compatibility claim. */
object GeelyCapability {
    fun knownHardware(product: String?, model: String?, manufacturer: String?, sdkInt: Int): Boolean =
        product.equals("geely_fx11", ignoreCase = true) &&
            model.equals("CS11_A4", ignoreCase = true) &&
            manufacturer.equals("DesaySV", ignoreCase = true) && sdkInt == 30

    fun inputAllowed(knownHardware: Boolean, manualEnabled: Boolean): Boolean =
        knownHardware || manualEnabled

    fun inputServiceUsable(
        resolved: Boolean,
        enabled: Boolean,
        applicationEnabled: Boolean,
        exported: Boolean,
        permissionGranted: Boolean,
    ): Boolean = resolved && enabled && applicationEnabled && exported && permissionGranted
}
