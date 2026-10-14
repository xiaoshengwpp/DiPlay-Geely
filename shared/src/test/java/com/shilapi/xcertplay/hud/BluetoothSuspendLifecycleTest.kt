package com.shilapi.xcertplay.hud

import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.content.ServiceConnection
import android.content.SharedPreferences
import com.shilapi.xcertplay.airplay.*
import com.shilapi.xcertplay.orchestration.*
import com.shilapi.xcertplay.transport.Iap2IdentificationConfig
import org.junit.After
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], manifest = Config.NONE)
class BluetoothSuspendLifecycleTest {
    private val app get() = RuntimeEnvironment.getApplication()
    private val leaseField = BydBluetoothSuspend::class.java.getDeclaredField("lease").apply { isAccessible = true }
    private val original = leaseField.get(null)
    private val controllers = mutableListOf<CarPlayController>()
    private val rejectingContext = object : ContextWrapper(null) {
        override fun getApplicationContext(): Context = error("BYD context accessed")
        override fun getSharedPreferences(name: String, mode: Int): SharedPreferences = error("BYD journal accessed")
        override fun getSystemService(name: String): Any = error("Bluetooth service accessed")
    }

    @After fun tearDown() {
        controllers.forEach { it.close(); assertTrue(it.awaitClosed(2_000)) }
        leaseField.set(null, original)
    }

    private fun assertNoWorker() {
        for (name in listOf("shell", "worker")) {
            val field = BydBluetoothSuspend::class.java.getDeclaredField(name + "\$delegate").apply { isAccessible = true }
            assertFalse("$name initialized", (field.get(null) as Lazy<*>).isInitialized())
        }
        assertNull(BydBluetoothSuspend::class.java.getDeclaredField("pending").apply { isAccessible = true }.get(null))
    }

    private fun installRejectingLease(): BluetoothSuspendLease {
        val lease = BluetoothSuspendLease(
            object : BluetoothRestoreJournal {
                override fun pending(): Boolean = error("restore journal read")
                override fun write(pending: Boolean): Boolean = error("restore journal written")
            },
            { error("Bluetooth state read") },
            { error("Bluetooth state changed") },
            { error("Bluetooth recovery awaited") },
        )
        leaseField.set(null, lease)
        return lease
    }

    @Test fun lifecycleIgnoresEvenAnInjectedLeaseBeforeInvokingCallbacks() {
        val lease = installRejectingLease()
        val owner = Any()
        repeat(3) {
            BydBluetoothSuspend.suspend(rejectingContext, owner, 0,
                log = { error("pause log callback invoked") },
                stillActive = { error("owner callback invoked") })
            BydBluetoothSuspend.resume(rejectingContext, owner) { error("resume log callback invoked") }
            BydBluetoothSuspend.onAppOpened(rejectingContext)
            assertFalse(BydBluetoothSuspend.isSuspendedByUs(rejectingContext))
        }
        assertSame(lease, leaseField.get(null))
        assertNoWorker()
    }

    @Test fun disabledVendorRecoveryDoesNotBlockTheNormalWirelessHandshake() {
        installRejectingLease()
        // The controller separately checks adapter.isEnabled; no vendor work is needed here.
        assertTrue(BydBluetoothSuspend.resumeAndWait(rejectingContext, null, 0))
        assertNoWorker()
    }

    @Test fun realControllerCloseDoesNotAttemptVendorCrashRecovery() {
        val lease = installRejectingLease()
        val controller = controller()
        controller.close()
        assertTrue(controller.awaitClosed(2_000))
        assertSame(lease, leaseField.get(null))
        assertNoWorker()
    }

    @Test fun forgedPersistedRestoreFlagCannotReadOrChangeTheRadio() {
        val prefs = app.getSharedPreferences("diplay_bt_suspend", Context.MODE_PRIVATE)
        prefs.edit().putBoolean("restore_initially_enabled", true).commit()
        try {
            BydBluetoothSuspend.onAppOpened(app)
            BydBluetoothSuspend.resume(app, Any())
            assertTrue(BydBluetoothSuspend.resumeAndWait(app, null))
            assertFalse(BydBluetoothSuspend.isSuspendedByUs(app))
            assertTrue("disabled code must leave legacy recovery data untouched", prefs.getBoolean("restore_initially_enabled", false))
            assertNoWorker()
        } finally {
            prefs.edit().clear().commit()
        }
    }

    private fun controller(): CarPlayController {
        val context = object : ContextWrapper(app) {
            override fun getApplicationContext(): Context = this
            override fun bindService(service: Intent, conn: ServiceConnection, flags: Int) = false
        }
        return CarPlayController(context, CarPlayRuntimeConfig(
            mfiTarget = MfiTarget.LOCAL, transport = CarPlayTransport.WIRED,
            identification = Iap2IdentificationConfig(name = "test", modelIdentifier = "test", manufacturer = "test",
                serialNumber = "test", firmwareVersion = "1", hardwareVersion = "1", carPlayUsbInterfaceNumber = 3),
        ), AirPlayConfig("test", "test", "", "1", AirPlayDisplayConfig(800, 480)),
            AirPlayIdentity(ByteArray(32), ByteArray(32), "test"), PairingStore(),
            object : AirPlaySessionListener {}, object : AirPlayMediaHandler {}, {}).also(controllers::add)
    }
}
