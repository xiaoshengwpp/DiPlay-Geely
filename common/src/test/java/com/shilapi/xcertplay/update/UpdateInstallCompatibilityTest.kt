package com.shilapi.xcertplay.update

import com.shilapi.xcertplay.DiPlayActivity
import java.io.File
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [25, 28, 33])
class UpdateInstallCompatibilityTest {
    @Test fun geelyProfileNeverLaunchesInstallerOrUnknownSourcesSettings() {
        val activity = Robolectric.buildActivity(DiPlayActivity::class.java).get()
        val apk = File(activity.cacheDir, "update/test/DiPlay.apk").apply {
            parentFile!!.mkdirs()
            writeBytes(byteArrayOf(1))
        }
        DiPlayActivity::class.java.getDeclaredField("updateFile")
            .apply { isAccessible = true }.set(activity, apk)
        DiPlayActivity::class.java.getDeclaredMethod("installUpdate")
            .apply { isAccessible = true }.invoke(activity)
        assertNull(shadowOf(activity).nextStartedActivity)
    }

    @Test fun directNewInstallerEntryAlsoStopsBeforeReadingThePublicCopy() {
        val activity = Robolectric.buildActivity(DiPlayActivity::class.java).get()
        val nonexistent = File(activity.cacheDir, "never-created.apk")
        DiPlayActivity::class.java.getDeclaredMethod("startInstaller", File::class.java, android.net.Uri::class.java)
            .apply { isAccessible = true }.invoke(activity, nonexistent, android.net.Uri.parse("content://invalid/no-access"))
        assertNull(shadowOf(activity).nextStartedActivity)
        org.junit.Assert.assertFalse(nonexistent.exists())
    }
}
