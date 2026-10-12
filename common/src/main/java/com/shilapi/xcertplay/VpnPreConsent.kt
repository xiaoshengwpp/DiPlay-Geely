package com.shilapi.xcertplay

import android.content.Context
import com.shilapi.xcertplay.adb.LocalAdb

/** Normal Android VPN consent is required; a previously approved ADB key is not VPN consent. */
internal object VpnPreConsent {
    @Suppress("UNUSED_PARAMETER")
    fun grant(context: Context, adb: LocalAdb? = null): LocalAdb.Access = LocalAdb.Access.UNSUPPORTED
}
