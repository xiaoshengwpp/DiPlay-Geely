package com.shilapi.xcertplay.hud

import android.content.Context
import android.content.ContextWrapper
import android.content.SharedPreferences
import android.content.pm.PackageManager
import com.shilapi.xcertplay.iap2.wire.Iap2Frame
import java.io.ByteArrayOutputStream
import java.io.PrintStream
import java.lang.reflect.InvocationTargetException
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ExecutionException
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [29], manifest = Config.NONE)
class GeelyAmbientAndCallBoundaryTest {
    private val rejectingContext = object : ContextWrapper(null) {
        override fun getApplicationContext(): Context = error("vendor context accessed")
        override fun getPackageManager(): PackageManager = error("vendor packages probed")
        override fun getPackageName(): String = error("vendor package checked")
        override fun getSharedPreferences(name: String, mode: Int): SharedPreferences = error("vendor preferences or keys read")
        override fun getSystemService(name: String): Any = error("vendor service accessed")
    }

    @Test fun ambientConstructorAndEveryLifecycleMethodStayInert() {
        assertFalse(GeelyVendorPolicy.bydAmbientLamp)
        val client = BydAmbientLightClient(rejectingContext)
        repeat(2) {
            assertDisabled(client.read())
            assertDisabled(client.begin())
            // The build boundary wins even over invalid or forged session input.
            assertDisabled(client.begin(BydAmbientLightClient.State(-1, -1, -1, -1, -1)))
            assertDisabled(client.apply(-1, -1, -1))
            assertDisabled(client.stop())
            assertDisabled(client.stopAtMinimum())
            assertDisabled(client.cancelGeneration())
            assertEquals(0L, client.status().generation)
            client.close()
        }
        assertEquals(BydAmbientLightClient.Phase.CLOSED, client.status().phase)
        val ensure = client.javaClass.getDeclaredMethod("ensure", Long::class.javaPrimitiveType)
            .apply { isAccessible = true }
        assertBoundaryRejected("byd-ambient-lamp-disabled") { ensure.invoke(client, 0L) }
        assertNull(client.javaClass.getDeclaredMethod("exchange", String::class.java, Int::class.javaPrimitiveType)
            .apply { isAccessible = true }.invoke(client, "read", 0))
        assertLazyUntouched(client, "app", "executor")
        for (name in listOf("adb", "stream", "token", "keepAlive", "pending")) {
            assertNull(name, client.javaClass.getDeclaredField(name).apply { isAccessible = true }.get(client))
        }
    }

    @Test fun directAmbientSdkConstructionAndWorkerEntrypointsAreDisabled() {
        val output = captureOutput {
            BydAmbientLightTool.main(arrayOf("--stdin", "0".repeat(32)))
            BydAmbientLightTool.main(emptyArray())
        }
        assertEquals(2, output.lineSequence().count { it == "error byd-ambient-lamp-disabled" })
        // This direct invocation must not create the watchdog, acquire ownership or inspect stdin.
        BydAmbientLightTool::class.java.getDeclaredMethod("runOwned", String::class.java)
            .apply { isAccessible = true }.invoke(BydAmbientLightTool, "0".repeat(32))
        val constructor = Class.forName("com.shilapi.xcertplay.hud.AmbientDevice")
            .getDeclaredConstructor().apply { isAccessible = true }
        assertBoundaryRejected("byd-ambient-lamp-disabled") { constructor.newInstance() }
    }

    @Test fun bluetoothAndBootRepairNeverTouchContextOrOwnerCallback() {
        val owner = Any()
        repeat(3) {
            BydBluetoothSuspend.suspend(rejectingContext, owner, 0) { error("owner callback invoked") }
            BydBluetoothSuspend.resume(rejectingContext, owner)
            BydBluetoothSuspend.onAppOpened(rejectingContext)
            assertTrue(BydBluetoothSuspend.resumeAndWait(rejectingContext, null, 0))
            assertFalse(BydBluetoothSuspend.isSuspendedByUs(rejectingContext))
            val repaired = BydBootStartRepair.apply(rejectingContext, "untrusted.package; exit")
            assertFalse(repaired.applied)
            assertEquals(listOf("BYD ADB disabled for Geely"), repaired.lines)
        }
        assertLazyUntouched(BydBluetoothSuspend, "shell", "worker")
        for (name in listOf("lease", "pending")) {
            assertNull(name, BydBluetoothSuspend::class.java.getDeclaredField(name)
                .apply { isAccessible = true }.get(BydBluetoothSuspend))
        }
        assertNull(BydBluetoothSuspend::class.java.getDeclaredMethod("radioEnabled", Context::class.java)
            .apply { isAccessible = true }.invoke(BydBluetoothSuspend, rejectingContext))
        assertEquals(false, BydBluetoothSuspend::class.java
            .getDeclaredMethod("awaitRadio", Context::class.java, Boolean::class.javaPrimitiveType)
            .apply { isAccessible = true }.invoke(BydBluetoothSuspend, rejectingContext, true))
    }

    @Test fun callFacadeAndDirectShellBoundariesStayDisabled() {
        repeat(3) {
            BydCarPlayCall.attach(rejectingContext)
            BydCarPlayCall.sessionStarted()
            BydCarPlayCall.settingChanged(true)
            BydCarPlayCall.onFrame(Iap2Frame(CarPlayCallState.CALL_STATE_UPDATE, byteArrayOf()))
            BydCarPlayCall.settingChanged(false)
            BydCarPlayCall.end()
            assertNull(BydCarPlayCall.current())
        }
        for ((method, parameters, args) in listOf(
            Triple("ensureWatcher", arrayOf(Context::class.java, String::class.java), arrayOf(rejectingContext, "token")),
            Triple("probe", arrayOf(Context::class.java, String::class.java, String::class.java), arrayOf(rejectingContext, "token", "1")),
            Triple("run", arrayOf(Context::class.java, String::class.java), arrayOf(rejectingContext, "active name token")),
        )) {
            assertEquals(false, BydCarPlayCall::class.java.getDeclaredMethod(method, *parameters)
                .apply { isAccessible = true }.invoke(BydCarPlayCall, *args))
        }
        assertLazyUntouched(BydCarPlayCall, "shell", "writer")
        assertNull(BydCarPlayCall::class.java.getDeclaredField("context").apply { isAccessible = true }.get(BydCarPlayCall))
        val output = captureOutput {
            for (command in listOf("prepare", "cancel", "probe", "watch", "end", "ringing", "dialing", "active")) {
                BydCarPlayCallTool.main(arrayOf(command, "-", "token", "1", "1"))
            }
        }
        assertEquals(8, output.lineSequence().count { it == "write=ERR byd-cluster-disabled" })
        assertLazyUntouched(BydCarPlayCallTool, "ownership")
        val device = BydCarPlayCallTool::class.java.getDeclaredMethod("device").apply { isAccessible = true }
        assertBoundaryRejected("byd-cluster-disabled") { device.invoke(BydCarPlayCallTool) }
    }

    @Test fun forgedEnabledPreferencesCannotInitializeWorkersOrRestoreHardware() {
        val context = RuntimeEnvironment.getApplication()
        val outputPrefs = context.getSharedPreferences("diplay_byd_outputs", Context.MODE_PRIVATE)
        val restorePrefs = context.getSharedPreferences("diplay_bt_suspend", Context.MODE_PRIVATE)
        outputPrefs.edit().putBoolean("carplay_calls", true).commit()
        restorePrefs.edit().putBoolean("restore_initially_enabled", true).commit()
        try {
            BydCarPlayCall.attach(context)
            BydCarPlayCall.sessionStarted()
            BydCarPlayCall.settingChanged(true)
            BydCarPlayCall.end()
            BydBluetoothSuspend.onAppOpened(context)
            BydBluetoothSuspend.suspend(context, Any(), 0) { error("forged preference allowed callback") }
            assertTrue(BydBluetoothSuspend.resumeAndWait(context, null))
            assertFalse(BydBluetoothSuspend.isSuspendedByUs(context))
            assertTrue(restorePrefs.getBoolean("restore_initially_enabled", false))
            assertLazyUntouched(BydCarPlayCall, "shell", "writer")
            assertLazyUntouched(BydBluetoothSuspend, "shell", "worker")
        } finally {
            outputPrefs.edit().clear().commit()
            restorePrefs.edit().clear().commit()
        }
    }

    @Test fun callWritesCannotInvokeHardwareOrCompensationCallbacks() {
        for (writes in listOf(emptyList(), listOf(CarPlayCallWrite("state", 1, 2)))) {
            assertFalse(CarPlayCallWrites.apply(writes,
                write = { _, _, _ -> error("vehicle write or compensation invoked") },
                name = { error("caller name write invoked") },
            ))
        }
    }

    @Test fun pureModelsParsersAndPoliciesRemainUsable() {
        val token = "0".repeat(32)
        val request = requireNotNull(BydAmbientLightWorkerProtocol.parse("$token begin 1 2 3 4 1", token))
        assertEquals(BydAmbientLightPolicy.Snapshot(1, 2, 3, 4, 1),
            BydAmbientLightWorkerProtocol.snapshotForBegin(request) { error("seeded snapshot needs no device") })
        assertArrayEquals("Alice".toByteArray(Charsets.UTF_16LE), CarPlayCallState.nameBytes("Alice"))
        assertNull(CarPlayCallState().current())
        assertTrue(BydBootStartRepair.isWhitelisted("user,example.app,12345", "example.app"))
        assertFalse(BydBootStartRepair.isWhitelisted("user,other.app,12345", "example.app"))
    }

    private fun assertDisabled(future: CompletableFuture<*>) {
        assertTrue("disabled calls must finish immediately", future.isDone)
        assertTrue(future.isCompletedExceptionally)
        try {
            future.get()
            fail("disabled call unexpectedly succeeded")
        } catch (error: ExecutionException) {
            assertTrue(error.cause is IllegalStateException)
            assertEquals("byd-ambient-lamp-disabled", error.cause?.message)
        }
    }

    private fun assertBoundaryRejected(reason: String, action: () -> Unit) {
        try {
            action()
            fail("direct SDK boundary unexpectedly initialized")
        } catch (error: InvocationTargetException) {
            assertTrue(error.cause is IllegalStateException)
            assertEquals(reason, error.cause?.message)
        }
    }

    private fun assertLazyUntouched(target: Any, vararg names: String) {
        for (name in names) {
            val field = target.javaClass.getDeclaredField(name + "\$delegate").apply { isAccessible = true }
            assertFalse("${target.javaClass.simpleName} $name initialized", (field.get(target) as Lazy<*>).isInitialized())
        }
    }

    private fun captureOutput(action: () -> Unit): String {
        val previous = System.out
        val captured = ByteArrayOutputStream()
        val output = PrintStream(captured)
        try {
            System.setOut(output)
            action()
            output.flush()
            return captured.toString("UTF-8")
        } finally {
            System.setOut(previous)
            output.close()
        }
    }
}
