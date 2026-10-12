@file:Suppress("UNUSED_PARAMETER", "unused")

package com.shilapi.xcertplay.hud

import android.content.Context
import java.util.concurrent.CompletableFuture

/** Compatibility API only. This product contains no live vendor backend. */
internal class BydAmbientLightClient(context: Context) : AutoCloseable {
    data class State(val frontColor: Int, val backColor: Int, val frontBrightness: Int, val backBrightness: Int, val area: Int)
    enum class Phase { IDLE, STARTING, ACTIVE, RESTORING, FAILED, CLOSED }
    data class Status(val phase: Phase, val generation: Long, val lastError: String? = null)
    private val app by lazy { Unit }
    private val executor by lazy { Unit }
    private var adb: Any? = null
    private var stream: Any? = null
    private var token: String? = null
    private var keepAlive: Any? = null
    private var pending: Any? = null
    @Volatile private var status = Status(Phase.IDLE, 0)
    fun status(): Status = status
    fun read(): CompletableFuture<State> = disabledFuture()
    fun begin(restoreSeed: State? = null): CompletableFuture<State> = disabledFuture()
    fun apply(area: Int, color: Int, rawBrightness: Int): CompletableFuture<State> = disabledFuture()
    fun stop(): CompletableFuture<State?> = disabledFuture()
    fun stopAtMinimum(): CompletableFuture<State?> = disabledFuture()
    fun cancelGeneration(): CompletableFuture<Unit> = disabledFuture()
    private fun <T> disabledFuture(): CompletableFuture<T> = CompletableFuture<T>().apply {
        completeExceptionally(IllegalStateException("byd-ambient-lamp-disabled"))
    }
    private fun ensure(ticket: Long): Unit = error("byd-ambient-lamp-disabled")
    private fun exchange(command: String, timeout: Int = 5000): String? = null
    override fun close() { status = Status(Phase.CLOSED, 0) }
}
