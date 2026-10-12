package com.shilapi.xcertplay

import com.shilapi.xcertplay.hud.GeelyVendorPolicy

import android.content.Context
import android.provider.Settings
import com.shilapi.xcertplay.adb.LocalAdb
import java.io.Closeable
import java.util.concurrent.atomic.AtomicBoolean

/** Explicit settings action; connection startup never grants these permissions. */
internal object UsbPermissionSetup {
    // Keep wheel-service rebinding and explicit USB setup from overwriting each other's service list.
    internal val accessibilityLock = Any()
    enum class Permission {
        ACCESSIBILITY, USAGE, OVERLAY;

        fun granted(context: Context): Boolean = runCatching {
            when (this) {
                ACCESSIBILITY -> UsbAutoConfirmService.isEnabled(context) &&
                    Settings.Secure.getInt(context.contentResolver, Settings.Secure.ACCESSIBILITY_ENABLED, 0) == 1
                USAGE -> HomeScreenMonitor.hasAccess(context)
                OVERLAY -> Settings.canDrawOverlays(context)
            }
        }.getOrDefault(false)
    }

    fun snapshot(context: Context): Map<Permission, Boolean> = Permission.entries.associateWith { it.granted(context) }

    internal interface Client : Closeable {
        fun connect(): LocalAdb.Access
        fun shell(command: String): String?
        fun cancel()
    }

    private object DisabledClient : Client {
        override fun connect() = LocalAdb.Access.UNSUPPORTED
        override fun shell(command: String): String? = null
        override fun cancel() = Unit
        override fun close() = Unit
    }

    data class Result(
        val access: LocalAdb.Access,
        val verified: Map<Permission, Boolean>,
        val commandsFailed: Set<Permission> = emptySet(),
    ) {
        val complete: Boolean get() = access == LocalAdb.Access.READY && commandsFailed.isEmpty() &&
            verified.size == Permission.entries.size && verified.values.all { it }
    }

    class Operation(
        private val context: Context,
        private val factory: () -> Client = { DisabledClient },
        private val granted: (Permission) -> Boolean = { it.granted(context) },
    ) {
        private val cancelled = AtomicBoolean(false)
        val isCancelled: Boolean get() = cancelled.get()

        fun cancel() { cancelled.set(true) }

        fun run(): Result {
            if (!GeelyVendorPolicy.bydAdb) return Result(LocalAdb.Access.UNSUPPORTED, emptyMap())
            return Result(LocalAdb.Access.UNSUPPORTED, emptyMap())
        }
    }

    /** Inert compatibility strings: the Geely product provides no ADB permission workflow. */
    @Suppress("UNUSED_PARAMETER")
    internal fun command(permission: Permission, packageName: String): String = ":"

    @Suppress("UNUSED_PARAMETER")
    fun manualCommand(packageName: String): String = ":"
}
