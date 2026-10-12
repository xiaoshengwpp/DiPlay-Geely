@file:Suppress("UNUSED_PARAMETER", "unused")

package com.shilapi.xcertplay.hud

import android.content.Context

/** Compatibility API only. This product contains no live vendor backend. */
data class BydTyreReading(val pressureKpa: List<Int?>, val temperatureC: List<Int?>)
internal object BydTyres {
    fun noService(reply: String?): Boolean = reply != null && "Parcel(" !in reply
    fun read(shell: (String) -> String?): BydTyreReading? = null
    fun plausiblePressure(kpa: Int): Int? = kpa.takeIf { it in 50..600 }
    fun plausibleTemperature(celsius: Int): Int? = celsius.takeIf { it in -50..150 }
}
object BydTyreStatus {
    private val shell by lazy { Unit }
    private val executor by lazy { Unit }
    @Volatile private var latest: BydTyreReading? = null
    fun start(context: Context) = Unit
    fun stop() = Unit
    fun latest(): BydTyreReading? = null
    private fun poll(context: Context) = Unit
}
