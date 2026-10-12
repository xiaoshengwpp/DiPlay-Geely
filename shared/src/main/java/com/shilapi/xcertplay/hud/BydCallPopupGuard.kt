@file:Suppress("UNUSED_PARAMETER", "unused")

package com.shilapi.xcertplay.hud

import android.content.Context
import com.shilapi.xcertplay.adb.LocalAdb
import java.io.Closeable

/** Compatibility API only. This product contains no live vendor backend. */
object BydCallPopupGuard {
    private val worker by lazy { Unit }
    private var owner: Any? = null
    private var app: Context? = null
    private var generation = 0L
    private var running = false
    private var leased = false
    private var adb: LocalAdb? = null
    @Volatile var status = "OFF"
        private set
    fun update(context: Context, candidate: Any, foregroundConnected: Boolean, connected: () -> Boolean) = Unit
    internal fun runPrepared(value: Long, channel: BydCallPopupChannel) = Unit
    internal fun runSession(channel: BydCallPopupChannel, current: () -> Boolean,
        waitHeartbeat: () -> Unit, report: (String) -> Unit) = Unit
}
internal class BydCallPopupChannel private constructor() : Closeable {
    fun exchange(command: String): String = "CANCELLED"
    override fun close() = Unit
    companion object {
        val TERMINAL = setOf("RESTORED", "ALREADY_HIDDEN", "EXTERNAL_CHANGE", "CANCELLED")
        fun prepare(apk: String, shell: (String) -> String?): BydCallPopupChannel = BydCallPopupChannel()
    }
}
