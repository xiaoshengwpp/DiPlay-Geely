package com.shilapi.xcertplay

import android.widget.LinearLayout
import com.shilapi.xcertplay.backup.SettingsBackupEndpoint
import com.shilapi.xcertplay.backup.SettingsBackupServer
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mockito.*
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [29])
class SettingsBackupNetworkLifecycleTest {
    @Test fun activeBackupLosingItsAddressStopsThenCanReopen() {
        val lifecycle = Robolectric.buildActivity(DiPlayActivity::class.java).setup()
        val activity = lifecycle.get()
        val old = mock(SettingsBackupServer::class.java)
        fun set(name: String, value: Any?) = activity.javaClass.getDeclaredField(name).apply { isAccessible = true }.set(activity, value)
        fun get(name: String): Any? = activity.javaClass.getDeclaredField(name).apply { isAccessible = true }.get(activity)
        fun renderBackup() = activity.javaClass.getDeclaredMethod("backup", LinearLayout::class.java)
            .apply { isAccessible = true }.invoke(activity, LinearLayout(activity))
        try {
            set("backupServer", old)
            set("backupEndpoint", SettingsBackupEndpoint(1234, "test-only"))
            activity.backupAddressCandidates = { emptyList() }
            repeat(2) { renderBackup() }
            verify(old, times(1)).stop()
            assertNull(get("backupServer"))
            assertNull(get("backupEndpoint"))
            activity.backupAddressCandidates = { listOf("127.0.0.1") }
            renderBackup()
            assertNotNull(get("backupServer"))
            assertNotNull(get("backupEndpoint"))
            activity.backupAddressCandidates = { emptyList() }
            renderBackup()
            assertNull(get("backupServer"))
            assertNull(get("backupEndpoint"))
        } finally {
            activity.javaClass.getDeclaredMethod("stopBackupServer").apply { isAccessible = true }.invoke(activity)
            lifecycle.pause().stop().destroy()
        }
    }
}
