package com.shilapi.xcertplay

import com.shilapi.xcertplay.hud.GeelyVendorPolicy

import android.content.Context
import android.content.pm.ApplicationInfo
import android.provider.Settings
import android.util.Log
import com.shilapi.xcertplay.adb.AdbKeys
import com.shilapi.xcertplay.adb.LocalAdb
import com.shilapi.xcertplay.hud.BydOutputSettings
import com.shilapi.xcertplay.network.CarHotspotSettings
import com.shilapi.xcertplay.orchestration.ManualHotspotValidation

/** Each grant is requested explicitly from settings; startup never calls this authorization path. */
internal object CarHotspotSetup {
    // Older BYD units report QUALCOMM/qti and have no supported navigation-output service.
    fun isBydHeadUnit(context: Context): Boolean = GeelyVendorPolicy.bydAdb && (BydOutputSettings.navigationAvailable(context) || runCatching {
        context.packageManager.getApplicationInfo("com.byd.carsettings", 0).flags and ApplicationInfo.FLAG_SYSTEM != 0
    }.getOrDefault(false))

    enum class Permission(val appOp: String) {
        HOTSPOT("WRITE_SETTINGS"), BOOT_LAUNCH("SYSTEM_ALERT_WINDOW");

        fun granted(context: Context): Boolean = when (this) {
            HOTSPOT -> Settings.System.canWrite(context)
            BOOT_LAUNCH -> Settings.canDrawOverlays(context)
        }
    }

    fun check(context: Context, adb: LocalAdb? = null): LocalAdb.Access =
        if (!GeelyVendorPolicy.bydAdb) LocalAdb.Access.UNREACHABLE else (adb ?: LocalAdb(AdbKeys.load(context))).use {
        it.connect(mayAsk = false)
    }

    fun grant(context: Context, permissions: List<Permission>, adb: LocalAdb? = null): LocalAdb.Access =
        if (!GeelyVendorPolicy.bydAdb) LocalAdb.Access.UNREACHABLE else (adb ?: LocalAdb(AdbKeys.load(context))).use {
            val access = it.connect(mayAsk = true)
            Log.i("DiPlay-ADB", "switch connection: $access")
            if (access == LocalAdb.Access.READY) {
                for (permission in permissions) {
                    if (!permission.granted(context)) {
                        Log.i("DiPlay-ADB", "request permission: ${permission.appOp}")
                        it.shell("appops set ${context.packageName} ${permission.appOp} allow")
                    }
                    val granted = permission.granted(context)
                    Log.i("DiPlay-ADB", "permission ${permission.appOp}: granted=$granted")
                    if (!granted) break
                }
            }
            access
        }

    fun shouldStartOnLaunch(context: Context, hasSession: Boolean): Boolean =
        GeelyVendorPolicy.bydAdb && !hasSession && CarHotspotSettings.shouldEnable(context,
            AirPlayPersistence.loadWirelessEnabled(context), AirPlayPersistence.loadWirelessHotspotMode(context)) &&
            ManualHotspotValidation.error(AirPlayPersistence.loadManualHotspotSsid(context),
                AirPlayPersistence.loadManualHotspotPassphrase(context)) == null
}
