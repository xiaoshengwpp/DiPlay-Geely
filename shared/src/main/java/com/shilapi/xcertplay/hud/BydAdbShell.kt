@file:Suppress("UNUSED_PARAMETER", "unused")

package com.shilapi.xcertplay.hud

import android.content.Context
import com.shilapi.xcertplay.adb.LocalAdb

/** Compatibility API only. This product contains no live vendor backend. */
internal class BydAdbShell(private val tag: String) {
    @Volatile var lastAccess: LocalAdb.Access? = null
        private set
    @Synchronized fun run(context: Context, command: String): String? = null
    @Synchronized fun run(context: Context, command: String, readTimeoutMillis: Int?): String? = null
    @Synchronized fun close() = Unit
}

/** Reads `service call` replies such as `Result: Parcel(00000000 00000002 '........')`. */
internal object BydParcel {
    /** The value after a zero exception code, as its 32 raw bits. */
    fun value(output: String?): Int? {
        val words = words(output)
        if (words.size < 2 || words[0] != 0L) return null
        return words[1].toInt()
    }

    fun words(output: String?): List<Long> {
        val body = output?.substringAfter("Parcel(", "")?.substringBefore('\'') ?: return emptyList()
        return body.trim().split(Regex("\\s+")).mapNotNull { word ->
            word.takeIf { it.length == 8 }?.toLongOrNull(16)
        }
    }
}
