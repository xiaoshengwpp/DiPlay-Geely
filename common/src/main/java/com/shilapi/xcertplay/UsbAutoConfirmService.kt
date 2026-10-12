package com.shilapi.xcertplay

import android.accessibilityservice.AccessibilityService
import android.content.Context
import android.view.accessibility.AccessibilityEvent
import com.shilapi.xcertplay.hud.GeelyVendorPolicy

/** Inert compatibility component. USB permission is never clicked or enabled automatically. */
class UsbAutoConfirmService : AccessibilityService() {
    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (!GeelyVendorPolicy.bydAdb) return
        // No window traversal or permission-changing implementation is shipped.
    }
    override fun onInterrupt() = Unit

    companion object {
        private val SYSTEM_PACKAGES = setOf("com.android.systemui", "android")
        private val USB_ACTIVITIES = setOf(
            "com.android.systemui.usb.UsbPermissionActivity",
            "com.android.systemui.usb.UsbConfirmActivity",
        )
        internal fun isSystemUsbWindow(pkg: String?, className: String?): Boolean =
            pkg in SYSTEM_PACKAGES && className in USB_ACTIVITIES

        /**
         * The window is already known to be the system USB dialog, so the prompt only has to name
         * this app. Android 10+ words it "Allow DiPlay to access iPhone?" with no "USB", and Chinese
         * puts the name between letters ("要允许DiPlay访问iPhone吗？"), so only ASCII word characters
         * count as part of a longer name such as "FakeDiPlay".
         */
        internal fun isTargetPrompt(text: String, appLabel: String): Boolean =
            appLabel.isNotBlank() &&
                Regex("(?<![A-Za-z0-9_])${Regex.escape(appLabel)}(?![A-Za-z0-9_])", RegexOption.IGNORE_CASE)
                    .containsMatchIn(text)

        @Suppress("UNUSED_PARAMETER")
        fun isEnabled(context: Context): Boolean = false

        @Suppress("UNUSED_PARAMETER")
        fun openSettings(context: Context): Boolean = false
    }
}
