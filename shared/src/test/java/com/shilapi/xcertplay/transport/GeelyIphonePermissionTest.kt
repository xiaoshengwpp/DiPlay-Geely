package com.shilapi.xcertplay.transport

import android.app.PendingIntent
import android.content.ComponentName
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbManager
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.Implementation
import org.robolectric.annotation.Implements
import org.robolectric.shadows.ShadowUsbManager
import org.robolectric.util.ReflectionHelpers

/** A missing AOSP dialog never enables an ADB grant path on Geely. */
@RunWith(RobolectricTestRunner::class)
@Config(
    sdk = [25, 26, 27, 28, 29],
    manifest = Config.NONE,
    shadows = [GeelyPermissionRequestUsbManagerShadow::class],
)
class GeelyIphonePermissionTest {
    private val context get() = RuntimeEnvironment.getApplication()

    @Before fun reset() {
        val permissionActivity = ComponentName(
            "com.android.systemui", "com.android.systemui.usb.UsbPermissionActivity",
        )
        shadowOf(context.packageManager).removeActivity(permissionActivity)
        GeelyPermissionRequestUsbManagerShadow.requests = 0
    }

    @Test fun bothEnumerationsUseTheNormalAndroidPermissionRequestEvenWithoutTheLegacyDialog() {
        val usb = context.getSystemService(UsbManager::class.java)
        val diagnostics = mutableListOf<String>()
        val host = IphoneUsbHost(context, usb, IphoneUsbMatcher.appleVendor(), onDiagnostic = diagnostics::add)
        for (name in listOf("initial", "reenumerated")) {
            assertTrue(host.requestPermission(device(name)) is IphoneUsbHost.PermissionRequest.Requested)
        }
        assertEquals(2, GeelyPermissionRequestUsbManagerShadow.requests)
        assertTrue("permission flow must not probe OEM UI or attempt ADB", diagnostics.isEmpty())
    }

    @Test fun anExistingPermissionNeedsNoNewRequest() {
        val usb = context.getSystemService(UsbManager::class.java)
        val phone = device("granted")
        shadowOf(usb).addOrUpdateUsbDevice(phone, true)
        val host = IphoneUsbHost(context, usb, IphoneUsbMatcher.appleVendor())
        assertTrue(host.requestPermission(phone) is IphoneUsbHost.PermissionRequest.AlreadyGranted)
        assertEquals(0, GeelyPermissionRequestUsbManagerShadow.requests)
    }

    @Test fun anUnconfiguredDeviceCannotRequestPermission() {
        val usb = context.getSystemService(UsbManager::class.java)
        val otherDevice = device("not-apple").apply { ReflectionHelpers.setField(this, "mVendorId", 0x1234) }
        val host = IphoneUsbHost(context, usb, IphoneUsbMatcher.appleVendor())
        try {
            host.requestPermission(otherDevice)
            fail("unconfigured USB identity must be rejected")
        } catch (_: IphoneUsbException.DeviceUnavailable) {
            assertEquals(0, GeelyPermissionRequestUsbManagerShadow.requests)
        }
    }

    private fun device(name: String): UsbDevice = ReflectionHelpers.newInstance(UsbDevice::class.java).apply {
        ReflectionHelpers.setField(this, "mName", name)
        ReflectionHelpers.setField(this, "mVendorId", IphoneUsbMatcher.APPLE_VENDOR_ID)
        ReflectionHelpers.setField(this, "mProductId", 0x12a8)
    }
}

@Implements(UsbManager::class)
class GeelyPermissionRequestUsbManagerShadow : ShadowUsbManager() {
    companion object { var requests = 0 }

    @Implementation
    protected fun requestPermission(device: UsbDevice, result: PendingIntent) { requests++ }
}
