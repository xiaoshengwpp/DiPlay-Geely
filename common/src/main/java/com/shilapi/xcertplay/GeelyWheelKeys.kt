package com.shilapi.xcertplay

import android.content.Context

/**
 * Deliberately inactive in this unverified Geely product. Brand strings do not establish input
 * service compatibility or Siri consent. No context, Binder listener, retry or retained session
 * exists here, so stop/restart and late vendor callbacks cannot revive a connection.
 * The standard Android MediaSession path remains available.
 */
internal object GeelyWheelKeys {
    @Suppress("UNUSED_PARAMETER")
    fun start(context: Context) = Unit
    fun stop() = Unit
}
