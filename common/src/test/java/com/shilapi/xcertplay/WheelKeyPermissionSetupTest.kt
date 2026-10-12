package com.shilapi.xcertplay

import android.content.ComponentName
import android.content.Context
import android.provider.Settings
import com.shilapi.xcertplay.adb.LocalAdb
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Robolectric
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [29], manifest = Config.NONE)
class WheelKeyPermissionSetupTest {
    private val context get() = RuntimeEnvironment.getApplication()
    private val ours get() = ComponentName(context, WheelKeyService::class.java).flattenToString()
    private val other = "car.package/.Accessibility"
    private val commands = mutableListOf<String>()
    private var reading = other
    private var reply: String? = "\nDIPLAY_WHEEL_EXIT:0"

    @Before fun setup() {
        context.getSharedPreferences("diplay_navigation_wheel", Context.MODE_PRIVATE).edit().clear().commit()
        context.getSharedPreferences("diplay_wheel_map_zoom", Context.MODE_PRIVATE).edit().clear().commit()
        Settings.Secure.putString(context.contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES, other)
        Settings.Secure.putInt(context.contentResolver, Settings.Secure.ACCESSIBILITY_ENABLED, 0)
    }

    @Test fun nullEmptyOrFailedReadIsNeverAttempted() {
        for (output in listOf(null, "", "$other\nDIPLAY_WHEEL_EXIT:1")) {
            assertRefusedWithoutSideEffects({ commands += it; output })
        }
    }

    @Test fun malformedOrShellActiveValuesAreNeverRead() {
        for (value in listOf("Permission denied", "$other:'; touch /tmp/unsafe; '", "$other::bad", "$other\nbad")) {
            reading = value
            assertRefusedWithoutSideEffects(::shell)
        }
    }

    @Test fun aStoppedListedServiceIsNeverReboundAndOtherServicesKeepTheirOrder() {
        reading = "$other:$ours:another.package/.Service"
        Settings.Secure.putString(context.contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES, reading)
        assertRefusedWithoutSideEffects(::shell)
        assertFalse(WheelKeyService.enabledInSettings(context))
    }

    @Test fun aServiceConnectionCannotPermitAnAccessibilityWrite() {
        val service = Robolectric.buildService(WheelKeyService::class.java).create().get()
        service.javaClass.getDeclaredMethod("onServiceConnected").apply { isAccessible = true }.invoke(service)
        try {
            reading = "$other:$ours"
            assertFalse(WheelKeyService.connected())
            assertRefusedWithoutSideEffects(::shell)
        } finally { service.onDestroy() }
    }

    @Test fun serviceBindingCannotBeTriggeredThroughTheContinuationCallback() {
        val service = Robolectric.buildService(WheelKeyService::class.java).create().get()
        var continuationChecks = 0
        try {
            assertRefusedWithoutSideEffects(::shell) {
                continuationChecks++
                service.javaClass.getDeclaredMethod("onServiceConnected").apply { isAccessible = true }.invoke(service)
                true
            }
            assertEquals(0, continuationChecks)
            assertFalse(WheelKeyService.connected())
        } finally { service.onDestroy() }
    }

    @Test fun failingShellIsNeverInvokedAndCannotClaimSuccess() {
        reply = "DIPLAY_WHEEL_EXIT:1"
        assertRefusedWithoutSideEffects(::shell)
    }

    @Test fun successfulShellWithoutActualPermissionIsNeverInvoked() {
        assertRefusedWithoutSideEffects({ command ->
            commands += command
            if (command.contains("settings get")) "$other\nDIPLAY_WHEEL_EXIT:0" else "DIPLAY_WHEEL_EXIT:0"
        })
    }

    @Test fun enablingAndDisablingCannotScheduleRestorationOrMutatePreferences() {
        val prefs = context.getSharedPreferences("diplay_wheel_map_zoom", Context.MODE_PRIVATE)
        val before = prefs.all.toMap()
        for (enabled in listOf(true, false)) {
            WheelZoomSettings.setEnabled(context, enabled)
            WheelZoomSettings.setJoystick(context, enabled)
            WheelZoomSettings.setSiriKey(context, enabled)
            assertFalse(WheelKeyService.needsRestore(context))
            WheelKeyService.restoreIfNeeded(context)
            assertEquals(before, prefs.all)
            assertRefusedWithoutSideEffects(::shell) { WheelKeyService.needsRestore(context) }
        }
    }

    @Test fun aShellReadCannotMutateTheContinuationState() {
        var allow = true
        assertRefusedWithoutSideEffects({ command -> shell(command).also { allow = false } }) { allow }
        assertTrue(allow)
    }

    @Test fun forgedNavigationAuthorizationAndEnabledFlagsCannotRestoreTheService() {
        val prefs = context.getSharedPreferences("diplay_navigation_wheel", Context.MODE_PRIVATE)
        assertFalse(NavigationWheelSettings.enabled(context))
        prefs.edit().putBoolean("enabled", true).commit()
        val before = prefs.all.toMap()
        NavigationWheelSettings.recordAuthorization(context)
        assertEquals(before, prefs.all)
        prefs.edit().putBoolean("authorized", true).commit()
        val forged = prefs.all.toMap()
        assertFalse(NavigationWheelSettings.enabled(context))
        assertFalse(NavigationWheelSettings.restoreAllowed(context))
        assertFalse(WheelKeyService.needsRestore(context))
        for (mayAsk in listOf(true, false)) {
            assertEquals(LocalAdb.Access.UNREACHABLE, WheelKeyService.enableOverAdb(context, mayAsk))
        }
        WheelKeyService.restoreIfNeeded(context)
        assertRefusedWithoutSideEffects(::shell) { WheelKeyService.needsRestore(context) }
        assertEquals(forged, prefs.all)
    }

    private fun assertRefusedWithoutSideEffects(
        shell: (String) -> String?, shouldContinue: () -> Boolean = { true },
    ) {
        val services = Settings.Secure.getString(context.contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES)
        val enabled = Settings.Secure.getInt(context.contentResolver, Settings.Secure.ACCESSIBILITY_ENABLED, 0)
        val prefs = context.getSharedPreferences("diplay_navigation_wheel", Context.MODE_PRIVATE)
        val before = prefs.all.toMap()
        assertFalse(WheelKeyService.applyServiceSettings(context, shell, shouldContinue))
        assertTrue("No ADB read or write may be attempted", commands.isEmpty())
        assertEquals(services, Settings.Secure.getString(context.contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES))
        assertEquals(enabled, Settings.Secure.getInt(context.contentResolver, Settings.Secure.ACCESSIBILITY_ENABLED, 0))
        assertEquals(before, prefs.all)
    }

    private fun shell(command: String): String? {
        commands += command
        if (command.contains("settings get")) return "$reading\nDIPLAY_WHEEL_EXIT:0"
        if (reply?.trim() != "DIPLAY_WHEEL_EXIT:0") return reply
        if (command.contains("settings put secure enabled_accessibility_services")) {
            val value = command.substringAfter("enabled_accessibility_services '").substringBefore("'")
            Settings.Secure.putString(context.contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES, value)
        } else if (command.contains("settings put secure accessibility_enabled 1")) {
            Settings.Secure.putInt(context.contentResolver, Settings.Secure.ACCESSIBILITY_ENABLED, 1)
        }
        return reply
    }
}
