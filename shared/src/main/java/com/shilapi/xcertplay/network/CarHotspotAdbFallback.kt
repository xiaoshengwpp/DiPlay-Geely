package com.shilapi.xcertplay.network

import java.util.concurrent.FutureTask
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException

/** Disabled command adapter with offline response/state parsing and injected deadline tests. */
@Suppress("UNUSED_PARAMETER")
internal object CarHotspotAdbFallback {
    private const val EXIT = "DIPLAY_HOTSPOT_EXIT:"

    fun start(deadline: Long, cancelled: () -> Boolean, state: () -> Boolean?,
        shell: (String, Int) -> String?, log: (String) -> Unit): Boolean = false

    internal fun advertisedCommand(service: String, help: String?): String? = null

    internal fun accepted(output: String?): Boolean = output != null && !rejected(output) &&
        Regex("(?m)^$EXIT(\\d+)\\s*$").findAll(output).lastOrNull()?.groupValues?.get(1) == "0"

    private fun rejected(output: String): Boolean = Regex(
        "unknown command|permission denied|securityexception|(?:^|\\n)\\s*error:|failed|can't find service|not found|not supported|unsupported",
        RegexOption.IGNORE_CASE).containsMatchIn(output)

    /** Read current AP fields only, never a past log, station Wi-Fi state or an interface address. */
    internal fun apState(dump: String?): Boolean? {
        if (dump == null) return null
        val managers = dump.split(Regex("(?m)^\\s*(?:--)?Dump of SoftApManager(?:--| id=\\S+)?\\s*$")).drop(1)
        if (managers.isEmpty()) {
            val numeric = Regex("(?m)^\\s*(?:mWifiApState|mSoftApState|mTetheredSoftApState)\\s*[:=]\\s*(\\d+)\\s*$")
                .findAll(dump).map { CarHotspotStatus.stateEnabled(it.groupValues[1].toIntOrNull()) }.toList()
            return if (numeric.any { it == null }) null else numeric.distinct().singleOrNull()
        }
        val tethered = managers.filter { block ->
            val mode = Regex("(?m)^\\s*(?:mMode|mOriginalModeConfiguration\\.targetMode):\\s*(\\d+)\\s*$").find(block)?.groupValues?.get(1)
            mode == "1"
        }
        val states = tethered.map { block ->
            val current = Regex("(?m)^\\s*current StateMachine mode:\\s*(\\w+)\\s*$").find(block)?.groupValues?.get(1)
            val up = Regex("(?m)^\\s*mIfaceIsUp:\\s*(true|false)\\s*$").find(block)?.groupValues?.get(1)
            when {
                current == "StartedState" && up == "true" -> true
                current == "IdleState" && up == "false" -> false
                else -> null
            }
        }
        return if (states.any { it == null }) null else states.distinct().singleOrNull()
    }

    /** Deadline includes connection, capability reads, writes and AP-state polling. Cancellation aborts I/O. */
    internal fun bounded(deadline: Long, cancelled: () -> Boolean, abort: () -> Unit, action: () -> Boolean): Boolean {
        if (cancelled() || Thread.currentThread().isInterrupted || System.nanoTime() >= deadline) return false
        val task = FutureTask(action)
        Thread(task, "diplay-hotspot-adb").apply { isDaemon = true; start() }
        try {
            while (!cancelled() && !Thread.currentThread().isInterrupted) {
                val remaining = deadline - System.nanoTime()
                if (remaining <= 0) return false
                try {
                    return task.get(minOf(remaining, TimeUnit.MILLISECONDS.toNanos(100)), TimeUnit.NANOSECONDS)
                } catch (_: TimeoutException) { }
            }
        } catch (_: InterruptedException) {
            Thread.currentThread().interrupt()
        } catch (_: java.util.concurrent.ExecutionException) { }
        finally {
            if (!task.isDone) { abort(); task.cancel(true) }
        }
        return false
    }
}
