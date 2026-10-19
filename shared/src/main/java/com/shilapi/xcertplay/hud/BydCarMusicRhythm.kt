package com.shilapi.xcertplay.hud

import android.content.Context
import java.util.concurrent.CompletableFuture

/** Unsupported BYD compatibility API. No service calls, transport, or background work. */
@Suppress("UNUSED_PARAMETER")
object BydCarMusicRhythm {
    internal var direct: BydCarMusicRhythmCall = BydCarMusicRhythmDirect
    internal var helper: BydCarMusicRhythmCall = BydCarMusicRhythmHelper
    fun setPlaying(context: Context, value: Boolean) = Unit
    fun sendBands(context: Context, bands: IntArray) = Unit
    fun checkSupport(context: Context): CompletableFuture<Boolean> = CompletableFuture.completedFuture(false)
}

internal interface BydCarMusicRhythmCall {
    fun supported(context: Context): Boolean
    fun start(context: Context): Boolean
    fun stop(context: Context)
    fun sendBands(context: Context, bands: IntArray): Boolean = false
}

internal object BydCarMusicRhythmDirect : BydCarMusicRhythmCall {
    override fun supported(context: Context): Boolean = false
    override fun start(context: Context): Boolean = false
    override fun stop(context: Context) = Unit
}

internal object BydCarMusicRhythmHelper : BydCarMusicRhythmCall {
    override fun supported(context: Context): Boolean = false
    override fun start(context: Context): Boolean = false
    override fun stop(context: Context) = Unit
    override fun sendBands(context: Context, bands: IntArray): Boolean = false
}

@Suppress("UNUSED_PARAMETER")
object BydCarMusicRhythmTool {
    const val MEDIA_CENTRE = "com.byd.mediacenter"
    @JvmStatic fun main(args: Array<String>) = Unit
}

/** Pure protocol values remain available for compatibility tests. */
internal object BydCarMusicRhythmBands {
    const val COUNT = 16
    private const val FIRST_FEATURE_ID = 774897672
    private const val FEATURE_STEP = 4

    fun valid(bands: IntArray): Boolean = bands.size == COUNT && bands.all { it in 1..15 }

    /** Sixteen space-separated values from 1 to 15, or null. */
    fun parse(text: String): IntArray? {
        val fields = text.trim().split(' ').filter { it.isNotEmpty() }
        if (fields.size != COUNT) return null
        val values = IntArray(COUNT) { fields[it].toIntOrNull() ?: return null }
        return values.takeIf(::valid)
    }

    fun featureIds(): IntArray = IntArray(COUNT) { FIRST_FEATURE_ID + FEATURE_STEP * it }

    @Suppress("UNUSED_PARAMETER")
    class Feed {
        fun send(bands: IntArray): Int = -1
    }
}
