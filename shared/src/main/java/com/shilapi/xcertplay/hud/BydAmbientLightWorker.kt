package com.shilapi.xcertplay.hud

import java.io.InputStream

/** Token-bound app_process bridge. It accepts only the explicit interior-lamp operations below. */
internal object BydAmbientLightWorkerProtocol {
    private const val MAX_LINE_BYTES = 512
    private val tokenPattern = Regex("[0-9a-f]{32}")
    data class Request(val op: String, val args: List<Int> = emptyList())
    fun validToken(token: String) = tokenPattern.matches(token)

    /**
     * The worker's one-line reply in [output]. On BYD firmware with its hotfix framework (DiLink 5 on a 2024 Tang),
     * app_process prints "HotFixRoot: … -> patchMetaFile:…" on stdout as the worker sets up BYD's SDK, before
     * the first reply; only the last line is the worker's.
     */
    fun reply(output: String?): String? = output?.lines()?.lastOrNull { it.isNotBlank() }?.trim()

    fun parse(line: String, token: String): Request? {
        if (!validToken(token) || line.toByteArray(Charsets.UTF_8).size > MAX_LINE_BYTES) return null
        val f = line.split(' ')
        if (f.firstOrNull() != token) return null
        if (f.size == 2 && f[1] in setOf("ping", "read", "begin", "stop", "minimum-stop", "close")) return Request(f[1])
        if (f.size == 7 && f[1] == "begin") {
            val a = f.drop(2).map { it.toIntOrNull() ?: return null }
            val seed = BydAmbientLightPolicy.Snapshot(a[0], a[1], a[2], a[3], a[4])
            return if (BydAmbientLightPolicy.validSnapshot(seed)) Request("begin", a) else null
        }
        if (f.size == 5 && f[1] == "apply") {
            val a = f.drop(2).map { it.toIntOrNull() ?: return null }
            return if (BydAmbientLightPolicy.validApply(a[0], a[1], a[2])) Request("apply", a) else null
        }
        return null
    }

    fun snapshotForBegin(request: Request, readCurrent: () -> BydAmbientLightPolicy.Snapshot): BydAmbientLightPolicy.Snapshot {
        require(request.op == "begin")
        val a = request.args
        val snapshot = if (a.isEmpty()) readCurrent() else {
            require(a.size == 5)
            BydAmbientLightPolicy.Snapshot(a[0], a[1], a[2], a[3], a[4])
        }
        require(BydAmbientLightPolicy.validSnapshot(snapshot))
        return snapshot
    }

    fun readLine(input: InputStream): String? {
        val b = java.io.ByteArrayOutputStream()
        repeat(MAX_LINE_BYTES + 1) {
            val c = input.read()
            if (c < 0) return null
            if (c == 10) return b.toString("UTF-8")
            b.write(c)
        }
        return null
    }
}

/** No-op compatibility interface. This product has no live vehicle access implementation. */
object BydAmbientLightTool {
    @JvmStatic fun main(args: Array<String>) { println("error byd-ambient-lamp-disabled") }
    private fun runOwned(token: String) = Unit
}

/** Retained fail-closed construction contract; no SDK lookup or mutation code is shipped. */
private class AmbientDevice {
    init { error("byd-ambient-lamp-disabled") }
}
