package com.shilapi.xcertplay.hud

import android.content.Context
import com.shilapi.xcertplay.transport.VehicleGear
import com.shilapi.xcertplay.transport.VehicleSpeedReading
import com.shilapi.xcertplay.transport.VehicleSpeedSample
import com.shilapi.xcertplay.transport.VehicleSpeedSource
import java.util.concurrent.ScheduledExecutorService

/**
 * Speed and gear through the adb shell (autoservice binder), where apps would need a BYD signature.
 * The ids are BYD SDK 1.0.5's speed (km/h) and gearbox devices; on DiLink 5.0 they read 0.0 and 1
 * while parked.
 */
internal object BydWheelSpeed {
    const val SPEED = "service call autoservice 7 i32 1013 i32 -1807745016" // float km/h
    const val GEAR = "service call autoservice 5 i32 1011 i32 555745336" // 1 P, 2 R, 3 N, 4 D

    fun speedCommand(context: Context): String = if (BydOutputSettings.legacyVehicleProbe(context)) {
        BydVehicleFieldStore.load(context)?.result(BydVehicleField.SPEED)?.address?.command() ?: SPEED
    } else SPEED

    fun gearCommand(context: Context): String = if (BydOutputSettings.legacyVehicleProbe(context)) {
        BydVehicleFieldStore.load(context)?.result(BydVehicleField.GEAR)?.address?.command() ?: GEAR
    } else GEAR

    fun metersPerSecond(output: String?): Double? {
        val kmh = BydParcel.value(output)?.let { java.lang.Float.intBitsToFloat(it).toDouble() } ?: return null
        return if (kmh in 0.0..300.0) kmh / 3.6 else null
    }

    fun gear(output: String?): VehicleGear? = when (BydParcel.value(output)) {
        1 -> VehicleGear.PARK
        2 -> VehicleGear.REVERSE
        3 -> VehicleGear.NEUTRAL
        4, 5, 6 -> VehicleGear.DRIVE // M and S are forward selector positions on older SDKs.
        else -> null
    }
}

/** No-op compatibility interface. This product has no live vehicle access implementation. */
internal object BydWheelSpeedSource : VehicleSpeedSource {
    private val shell by lazy { Unit }
    private var context: Context? = null
    private var executor: ScheduledExecutorService? = null
    private val samples = ArrayList<VehicleSpeedSample>()
    private var gear: VehicleGear? = null
    fun attach(appContext: Context) = this
    override fun start() = Unit
    override fun stop() = Unit
    override fun drain(): VehicleSpeedReading? = null
    private fun poll() = Unit
}
