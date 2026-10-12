package com.shilapi.xcertplay.hud

import android.content.Context
import android.os.Build

/** The small read-only BYD data surface DiPlay can forward to CarPlay. */
enum class BydVehicleField(
    internal val symbol: String,
    internal val symbolAliases: List<String>,
    internal val deviceName: String,
    internal val defaultDevice: Int,
    internal val defaultFid: Int,
    internal val transaction: Int,
) {
    SPEED("Speed.SPEED_AUTO_SPEED", emptyList(), "SPEED", 1013, -1807745016, 7),
    GEAR("Gearbox.GEARBOX_AUTO_MODE_TYPE", emptyList(), "GEARBOX", 1011, 555745336, 5),
    SOC("Statistic.STATISTIC_ELEC_PERCENTAGE", emptyList(), "STATISTIC", 1014, 1246777400, 7),
    RANGE("Statistic.STATISTIC_ELEC_DRIVING_RANGE", emptyList(), "STATISTIC", 1014, 1246765118, 5),
    REMAINING_KWH(
        "Power.POWER_BATTERY_REMAIN_ELECTRICITY",
        emptyList(),
        "POWER",
        1005,
        882901008,
        7,
    ),
    BMS_STATE(
        "Charging.CHARGING_BATTERRY_DEVICE_STATE",
        listOf("Charging.CHARGING_BATTERY_DEVICE_STATE"),
        "CHARGING",
        1009,
        876609560,
        5,
    ),
}

enum class BydFieldSource { FIRMWARE, KNOWN_13, DEFAULT }

data class BydReadAddress(
    val device: Int,
    val fid: Int,
    val transaction: Int,
    val source: BydFieldSource,
) {
    fun command(): String = "service call autoservice $transaction i32 $device i32 $fid"
}

data class BydFieldProbeResult(
    val field: BydVehicleField,
    val supported: Boolean,
    val address: BydReadAddress?,
    /** Decoded numeric value at probe time; gear and BMS state retain their integer code. */
    val value: Double? = null,
    val failure: String? = null,
)

/** A completed, read-only probe of the current head unit. */
data class BydVehicleCapabilities(
    val fields: Map<BydVehicleField, BydFieldProbeResult>,
    val catalogAvailable: Boolean,
    val firmwareKey: String,
    val detectedAtMillis: Long = System.currentTimeMillis(),
) {
    fun result(field: BydVehicleField): BydFieldProbeResult =
        fields[field] ?: BydFieldProbeResult(field, false, null, failure = "not probed")

    fun supports(field: BydVehicleField): Boolean = result(field).supported

    val motionSupported: Boolean get() = supports(BydVehicleField.SPEED) && supports(BydVehicleField.GEAR)
    val gearSupported: Boolean get() = supports(BydVehicleField.GEAR)
    val batterySupported: Boolean get() = supports(BydVehicleField.SOC) && supports(BydVehicleField.RANGE)
    val batteryEnergySupported: Boolean get() = supports(BydVehicleField.REMAINING_KWH)
    val chargingSupported: Boolean get() = supports(BydVehicleField.BMS_STATE)
}

data class BydVehicleAutomaticReplaceResult(
    val saved: Boolean,
    val lostFields: Set<BydVehicleField> = emptySet(),
    val snapshotChanged: Boolean = false,
)

/** No-op compatibility interface. This product has no live vehicle access implementation. */
object BydVehicleFieldStore {
    @Volatile private var memory: BydVehicleCapabilities? = null
    fun firmwareKey(): String = "${Build.FINGERPRINT}|${Build.DISPLAY}"
    fun load(context: Context): BydVehicleCapabilities? = null
    fun save(context: Context, capabilities: BydVehicleCapabilities) = Unit
    fun replaceAutomatically(context: Context, expected: BydVehicleCapabilities?, candidate: BydVehicleCapabilities): BydVehicleAutomaticReplaceResult =
        BydVehicleAutomaticReplaceResult(saved = false)
    fun replaceAnyway(context: Context, expected: BydVehicleCapabilities?, candidate: BydVehicleCapabilities): Boolean = false
    fun address(context: Context, field: BydVehicleField): BydReadAddress = defaultAddress(field)
    fun defaultAddress(field: BydVehicleField) = BydReadAddress(field.defaultDevice, field.defaultFid, field.transaction, BydFieldSource.DEFAULT)
    fun clearMemoryForTests() { memory = null }
}
