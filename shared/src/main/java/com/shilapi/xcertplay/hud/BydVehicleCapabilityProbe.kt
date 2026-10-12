@file:Suppress("UNUSED_PARAMETER", "unused")

package com.shilapi.xcertplay.hud

import android.content.Context

/** Compatibility API only. This product contains no live vendor backend. */
data class BydVehicleProbeOutcome(
    val access: BydAdbAccess.State,
    val capabilities: BydVehicleCapabilities? = null,
    val error: String? = null,
)
object BydVehicleCapabilityProbe {
    @Synchronized fun probe(context: Context, persist: Boolean = true): BydVehicleProbeOutcome = disabled()
    internal fun finishProbe(context: Context, access: BydAdbAccess.State, batch: ProbeBatch, persist: Boolean): BydVehicleProbeOutcome = disabled()
    internal data class ProbeBatch(val capabilities: BydVehicleCapabilities, val transportReplies: Int, val repliedFields: Int, val failedReads: Int) {
        val complete: Boolean get() = failedReads == 0 && repliedFields == BydVehicleField.entries.size
    }
    internal fun probeFields(catalogOutput: String?, sdkInt: Int, shell: (String) -> String?,
        firmwareKey: String = BydVehicleFieldStore.firmwareKey()): ProbeBatch = ProbeBatch(
        BydVehicleCapabilities(emptyMap(), catalogAvailable = false, firmwareKey = firmwareKey),
        transportReplies = 0, repliedFields = 0, failedReads = BydVehicleField.entries.size,
    )
    internal fun publishBatteryReading(context: Context, capabilities: BydVehicleCapabilities) = Unit
    private fun disabled() = BydVehicleProbeOutcome(BydAdbAccess.State.DISABLED_FOR_VEHICLE)
}
