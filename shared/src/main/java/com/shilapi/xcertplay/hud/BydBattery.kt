package com.shilapi.xcertplay.hud

import android.content.Context
import com.shilapi.xcertplay.transport.VehicleStatusProvider
import com.shilapi.xcertplay.transport.VehicleStatusSnapshot
import kotlin.math.roundToInt
import kotlin.math.roundToLong

internal enum class BydBatteryProtocol(val percentId: Int, val rangeId: Int, val chargingStateId: Int) {
    CAN(1033543720, 1033203771, 876611608),
    CANFD(1246777400, 1246765118, 876609560),
}

/** The traction battery as BYD's statistic, power and charging devices report it. */
internal data class BydBatteryReading(
    val percent: Double,
    val rangeKm: Int,
    val remainingKwh: Double?,
    val charging: Boolean,
    val protocol: BydBatteryProtocol? = null,
)

/** No-op compatibility interface. This product has no live vehicle access implementation. */
internal object BydBattery {
    fun read(shell: (String) -> String?): BydBatteryReading? = null
    fun read(context: Context, shell: (String) -> String?): BydBatteryReading? = null

    /**
     * What the iPhone gets. Full charge and full range are scaled up from the current values; below
     * 20 % the rounding of the percentage would make that scale jumpy, so [fullKwh] from an earlier,
     * higher reading is used when there is one.
     */
    fun snapshot(reading: BydBatteryReading, lowPercent: Int, fullKwh: Double?): VehicleStatusSnapshot {
        val fraction = reading.percent / 100
        val full = fullKwh ?: reading.remainingKwh?.let { if (fraction > 0) it / fraction else it }
        return VehicleStatusSnapshot(
            rangeKm = reading.rangeKm,
            rangeWarning = reading.percent <= lowPercent,
            batteryPercent = reading.percent,
            currentChargeWh = reading.remainingKwh?.let { (it * 1000).roundToLong() },
            maxChargeWh = full?.let { (it * 1000).roundToLong() },
            maxRangeKm = if (fraction > 0) (reading.rangeKm / fraction).roundToInt() else reading.rangeKm,
            charging = reading.charging,
        )
    }

    /** A full-charge estimate from [reading], when the percentage is high enough to trust it. */
    fun fullKwh(reading: BydBatteryReading): Double? =
        if (reading.percent >= 20) reading.remainingKwh?.div(reading.percent / 100) else null

    private fun float(bits: Int): Double = java.lang.Float.intBitsToFloat(bits).toDouble()
}

/** No-op compatibility interface. This product has no live vehicle access implementation. */
internal object BydBatteryStatus : VehicleStatusProvider {
    private val shell by lazy { Unit }
    private val executor by lazy { Unit }
    fun start(appContext: Context) = Unit
    override fun snapshot(): VehicleStatusSnapshot? = null
    private fun poll() = Unit
    fun read(appContext: Context, reader: () -> BydBatteryReading?): BydBatteryReading? = null
    fun publishProbeReading(appContext: Context, reading: BydBatteryReading) = Unit
}

internal class BydBatteryCache(private val now: () -> Long) {
    private var latest: BydBatteryReading? = null
    private var latestMillis = 0L
    private var fullKwh: Double? = null

    @Synchronized
    fun accept(reading: BydBatteryReading?): Boolean {
        if (reading == null) {
            latest = null
            fullKwh = null
            return false
        }
        if (latest?.protocol != reading.protocol) fullKwh = null
        val changed = latest?.let {
            it.protocol != reading.protocol || it.percent.roundToInt() != reading.percent.roundToInt() || it.charging != reading.charging
        } != false
        BydBattery.fullKwh(reading)?.let { fullKwh = it }
        latest = reading
        latestMillis = now()
        return changed
    }

    @Synchronized
    fun snapshot(lowPercent: Int): VehicleStatusSnapshot? {
        val reading = latest ?: return null
        if (now() - latestMillis > 3 * 60_000L) return null
        return BydBattery.snapshot(reading, lowPercent, fullKwh)
    }
}
