package com.shilapi.xcertplay.adb

/** A transport being absent here says nothing about whether the owner's car exposes ADB. */
object AdbCapability {
    enum class State { AVAILABLE, UNAVAILABLE, UNKNOWN, NOT_IMPLEMENTED }
    data class Snapshot(
        val developerSetting: State,
        val localTransport: State = State.NOT_IMPLEMENTED,
        val ownerComputerTransport: State = State.UNKNOWN,
    ) {
        fun report(): String = "ADB developer setting=$developerSetting; in-app transport=$localTransport; " +
            "owner-computer connection=$ownerComputerTransport. A setting is not a connection or authorization. " +
            "No shell commands, keys, pairing, permission grants or system-setting writes are performed."
    }

    fun snapshot(developerSettingEnabled: Boolean?): Snapshot = Snapshot(when (developerSettingEnabled) {
        true -> State.AVAILABLE
        false -> State.UNAVAILABLE
        null -> State.UNKNOWN
    })

    /** Documentation-only command catalog. No executor, arbitrary command, or write operation. */
    val readOnlyEvidenceCommands: List<String> = listOf(
        "adb devices -l",
        "adb shell getprop ro.product.model",
        "adb shell getprop ro.product.device",
        "adb shell getprop ro.build.version.release",
        "adb shell dumpsys display",
        "adb shell dumpsys window displays",
    )
}
