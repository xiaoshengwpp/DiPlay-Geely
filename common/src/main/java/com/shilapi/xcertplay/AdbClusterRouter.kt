package com.shilapi.xcertplay

import android.content.Context

/** Strict projection target, discovered afresh through the authorized shell. */
internal object AdbClusterRouter {
    data class Result(val success: Boolean, val report: String)

    /** The removed vehicle-routing adapter cannot discover or launch a vendor display. */
    @Suppress("UNUSED_PARAMETER")
    fun enabled(context: Context): Boolean = false

    // Match only the base logical display, not a device's layer-stack number or override record.
    internal fun displayId(dump: String): Int? {
        val candidates = dump.lineSequence().mapNotNull { line ->
            if (!line.contains("mBaseDisplayInfo=DisplayInfo{\"${DiLink4ClusterDisplay.NAME}, displayId ") ||
                !Regex("\\breal 1920 x 720\\b").containsMatchIn(line) ||
                !line.contains("owner com.xdja.containerservice (uid 1000)")) return@mapNotNull null
            Regex("displayId (\\d+)\"").find(line)?.groupValues?.get(1)?.toIntOrNull()?.takeIf { it > 0 }
        }.distinct().toList()
        return candidates.singleOrNull()
    }

    // Direct shell launch, following Hanxu4131's legacy platform-21 adapter.
    internal fun launchCommand(pkg: String, display: Int, token: String): String {
        require(display > 0)
        require(Regex("[A-Za-z_][A-Za-z0-9_]*(?:\\.[A-Za-z_][A-Za-z0-9_]*)+").matches(pkg))
        require(runCatching { java.util.UUID.fromString(token).toString() == token }.getOrDefault(false))
        return "am start-activity --display $display -f 0x18000000 " +
            "-n $pkg/com.shilapi.xcertplay.AdbClusterActivity --es cluster_launch_token $token"
    }

    internal fun accepted(output: String): Boolean =
        (output.contains("Starting: Intent {") || Regex("(?m)^Status: ok\\s*$").containsMatchIn(output)) &&
        !Regex("(?i)error|exception|permission\\s*deni(?:al|ed)").containsMatchIn(output)

    internal fun activityDisplay(dump: String, pkg: String, task: Int): Int? {
        var display: Int? = null
        val matches = mutableListOf<Int>()
        val component = "$pkg/com.shilapi.xcertplay.AdbClusterActivity"
        for (line in dump.lineSequence()) {
            val header = Regex("^Display #(\\d+) \\(activities from top to bottom\\):\\s*$").matchEntire(line)
            if (header != null) { display = header.groupValues[1].toInt(); continue }
            if (line.isNotEmpty() && !line.first().isWhitespace()) display = null
            val current = display ?: continue
            if (Regex("^\\s{4,}\\* Hist #\\d+: ActivityRecord\\{").containsMatchIn(line) &&
                Regex("\\bu\\d+\\s+" + Regex.escape(component) + "(?=\\s|,)").containsMatchIn(line) &&
                Regex("\\bt$task(?=\\s|\\})").containsMatchIn(line)) matches.add(current)
        }
        return matches.singleOrNull()?.takeIf { it > 0 }
    }

    @Suppress("UNUSED_PARAMETER")
    fun launch(context: Context, token: String, holdStockMap: Boolean = true, prepare: (Int) -> Boolean): Result =
        Result(false, "Cluster routing disabled for Geely")

    @Suppress("UNUSED_PARAMETER")
    fun verify(context: Context, task: Int): Int? = null

    @Suppress("UNUSED_PARAMETER")
    fun report(context: Context): String = "ADB cluster routing is unavailable in the Geely build."
}
