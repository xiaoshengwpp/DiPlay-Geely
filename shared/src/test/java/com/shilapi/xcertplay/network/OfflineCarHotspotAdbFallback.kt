package com.shilapi.xcertplay.network

/** Test-only command-policy fixture; shell callbacks operate on synthetic state only. */
internal object OfflineCarHotspotAdbFallback {
    private const val EXIT = "DIPLAY_HOTSPOT_EXIT:"

    fun start(deadline: Long, cancelled: () -> Boolean, state: () -> Boolean?,
        shell: (String, Int) -> String?, log: (String) -> Unit): Boolean {
        fun remaining() = ((deadline - System.nanoTime()) / 1_000_000L).coerceAtMost(30_000).toInt()
        fun stopped() = cancelled() || Thread.currentThread().isInterrupted || remaining() <= 0
        fun read(command: String): String? = if (stopped()) null else shell(command, remaining().coerceAtLeast(1))
        fun observed(): Boolean? = state() ?: apState(read("dumpsys wifi"))
        if (stopped()) return false
        val initial = observed() ?: return false // No blind mutation when no AP state can be observed.
        if (stopped()) return false
        if (initial) return true
        var command: String? = null
        for (service in listOf("connectivity", "tethering")) {
            command = advertisedCommand(service, read("cmd $service help"))
            if (command != null) break
        }
        if (command == null || stopped()) return false
        val output = read("$command 2>&1; printf '\\n$EXIT%s\\n' \"\$?\"")
        if (!accepted(output) || stopped()) return false
        log("car hotspot: firmware ADB start request accepted; waiting for AP enabled state")
        while (!stopped()) {
            if (observed() == true && !stopped()) {
                log("car hotspot: AP enabled state confirmed after local ADB request")
                return true
            }
            try {
                Thread.sleep(minOf(250, remaining().coerceAtLeast(1)).toLong())
            } catch (_: InterruptedException) {
                Thread.currentThread().interrupt()
                return false
            }
        }
        return false
    }

    /** The PR's command forms are vendor extensions, not generic Android commands. */
    internal fun advertisedCommand(service: String, help: String?): String? {
        if (service !in listOf("connectivity", "tethering") || help == null || rejected(help)) return null
        for (line in help.lineSequence()) {
            val form = Regex("^\\s*(start-tethering|start)\\s+(wifi|[<\\[(][^>\\])]*\\bwifi\\b[^>\\])]*[>\\])])(?:\\s*\\[[^\\n]*])?\\s*$")
                .matchEntire(line) ?: continue
            val verb = form.groupValues[1]
            if (service == "connectivity" && verb != "start-tethering") continue
            return "cmd $service $verb wifi"
        }
        return null
    }

    private fun accepted(output: String?) = CarHotspotAdbFallback.accepted(output)
    private fun apState(dump: String?) = CarHotspotAdbFallback.apState(dump)

    private fun rejected(output: String): Boolean = Regex(
        "unknown command|permission denied|securityexception|(?:^|\\n)\\s*error:|failed|can't find service|not found|not supported|unsupported",
        RegexOption.IGNORE_CASE).containsMatchIn(output)

}
