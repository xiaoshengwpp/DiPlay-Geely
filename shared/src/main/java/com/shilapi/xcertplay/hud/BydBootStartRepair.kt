package com.shilapi.xcertplay.hud

import android.content.Context

/** No-op compatibility interface. This product has no live vehicle access implementation. */
object BydBootStartRepair {
    /** One line per attempted fix: the short name plus ✓ or the failure text. */
    class Result(val lines: List<String>) {
        val applied: Boolean get() = lines.any { it.endsWith("✓") }
    }

fun apply(context: Context, packageName: String): Result = Result(listOf("BYD ADB disabled for Geely"))

    internal fun isWhitelisted(output: String?, packageName: String): Boolean =
        output?.lineSequence()?.any { line ->
            val fields = line.trim().split(',')
            fields.size >= 3 && fields[0] in setOf("system", "system-excidle", "user") &&
                fields[1] == packageName
        } == true
}
