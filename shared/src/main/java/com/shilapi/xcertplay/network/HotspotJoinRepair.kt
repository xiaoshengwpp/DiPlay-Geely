package com.shilapi.xcertplay.network

import android.content.Context

/** Disabled repair interface with pure command and response compatibility helpers. */
@Suppress("UNUSED_PARAMETER")
class HotspotJoinRepair {
    enum class Action { CHECK, APPLY, RESTORE }
    enum class Code { READY, ALREADY_PRESENT, APPLIED, RESTORED, UNSUPPORTED, DRIFT,
        FIRMWARE_CHANGED, HOTSPOT_ON, UNKNOWN_STATE, CANCELLED, FAILED_ROLLED_BACK,
        RECOVERY_REQUIRED, STORAGE_FAILED, BUSY, ADB_OFF, NOT_APPROVED, PAIRING_ONLY, UNKNOWN }
    data class Result(val code: Code, val token: String? = null, val rollback: Boolean = false)
    fun run(context: Context, action: Action, token: String? = null): Result = Result(Code.UNSUPPORTED)

    fun cancel() = Unit

    companion object {
        internal const val HEADER = "DIPLAY_HOTSPOT_JOIN_V1"
        internal fun validToken(value: String) = value.matches(Regex("[0-9a-f]{8}(?:-[0-9a-f]{4}){3}-[0-9a-f]{12}"))
        internal fun command(apk: String, packageName: String, action: String, request: String, token: String?): String = ""
        internal fun parse(output: String?): Result {
            val fields = output?.trim()?.split('|') ?: return Result(Code.UNKNOWN)
            if (fields.size != 4 || fields[0] != HEADER || fields[3] !in setOf("0", "1")) return Result(Code.UNKNOWN)
            val code = Code.entries.firstOrNull { it.name == fields[1] } ?: return Result(Code.UNKNOWN)
            val token = fields[2].takeIf { validToken(it) }
            if (fields[2] != "-" && token == null || code in setOf(Code.READY, Code.APPLIED) && token == null)
                return Result(Code.UNKNOWN)
            return Result(code, token, fields[3] == "1")
        }
    }
}
