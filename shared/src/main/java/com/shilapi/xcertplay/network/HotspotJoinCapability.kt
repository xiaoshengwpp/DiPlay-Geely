package com.shilapi.xcertplay.network

import android.os.IInterface

/** Offline capability model. Live service discovery and callback registration are unsupported. */
@Suppress("UNUSED_PARAMETER")
internal object HotspotJoinCapability {
    class Snapshot(private val fiveGhz: Boolean, private val channels: Set<Int>) {
        fun allows(channel: Int): Boolean = fiveGhz && channels.isNotEmpty() &&
            channels.all { it > 0 } && (channel == 0 || channel in channels)
    }
    internal interface Registration {
        fun register(callback: IInterface)
        fun unregister(callback: IInterface)
    }

    fun read(wifi: Any): Snapshot? = null

    internal fun read(registration: Registration, timeoutMillis: Long = 2_000): Snapshot? = null
}
