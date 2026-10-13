package com.shilapi.xcertplay

import android.os.Looper
import android.provider.Settings
import com.shilapi.xcertplay.adb.LocalAdb
import java.io.File
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.CopyOnWriteArrayList
import org.junit.Assert.*
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowAlertDialog
import org.robolectric.shadows.ShadowSettings

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [29], manifest = Config.NONE, qualifiers = "en")
class UsbPermissionSetupTest {
    private val context get() = RuntimeEnvironment.getApplication()
    private val permissions get() = UsbPermissionSetup.Permission.entries
    private var factories = 0
    private var grantChecks = 0

    @After fun noBackendWork() {
        assertEquals("Geely must not create an ADB client", 0, factories)
        assertEquals("Geely must not inspect permissions during an automatic operation", 0, grantChecks)
    }

    @Before fun reset() {
        Settings.Secure.putString(context.contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES, null)
        Settings.Secure.putInt(context.contentResolver, Settings.Secure.ACCESSIBILITY_ENABLED, 0)
        ShadowSettings.setCanDrawOverlays(false)
        AppLocale.save(context, "en")
    }

    @Test fun listedServiceWithOrWithoutGlobalAccessibilityIsNeverVerified() {
        Settings.Secure.putString(context.contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES,
            "${context.packageName}/com.shilapi.xcertplay.UsbAutoConfirmService")
        assertFalse(UsbPermissionSetup.Permission.ACCESSIBILITY.granted(context))
        Settings.Secure.putInt(context.contentResolver, Settings.Secure.ACCESSIBILITY_ENABLED, 1)
        assertFalse(UsbPermissionSetup.Permission.ACCESSIBILITY.granted(context))
    }

    @Test fun unapprovedConnectionDoesNotRunAnyGrant() {
        val client = FakeClient().apply { access = LocalAdb.Access.NOT_APPROVED }
        val result = operation(client).run()
        assertEquals(LocalAdb.Access.UNSUPPORTED, result.access)
        assertTrue(client.commands.isEmpty())
        assertFalse(result.complete)
        assertFalse(client.closed)
    }

    @Test fun transportFailureAndEmptyOutputNeverClaimSuccess() {
        for (reply in listOf(null, "", "DIPLAY_PERMISSION_EXIT:1")) {
            val client = FakeClient().apply { output = reply }
            val result = operation(client).run()
            assertTrue(result.commandsFailed.isEmpty())
            assertTrue(result.verified.isEmpty())
            assertTrue(client.commands.isEmpty())
            assertEquals(0, client.connections)
            assertFalse(result.complete)
        }
    }

    @Test fun successfulShellWithoutRuntimePermissionDoesNotClaimSuccess() {
        val result = operation(FakeClient()).run()
        assertTrue(result.commandsFailed.isEmpty())
        assertTrue(result.verified.values.none { it })
        assertFalse(result.complete)
    }

    @Test fun preexistingUsagePermissionCannotStartAutomaticSetup() {
        val client = FakeClient().apply { output = null }
        val result = operation(client) { it == UsbPermissionSetup.Permission.USAGE }.run()
        assertTrue(result.verified.isEmpty())
        assertTrue(result.commandsFailed.isEmpty())
        assertTrue(client.commands.isEmpty())
        assertEquals(0, client.connections)
        assertFalse(result.complete)
    }

    @Test fun evenAllRuntimeGrantsAndSuccessfulShellCannotEnableAutomaticSetup() {
        val allowed = permissions.toMutableSet()
        val client = FakeClient().apply {
            onCommand = { allowed += permissions[commands.size - 1] }
        }
        val result = operation(client) { it in allowed }.run()
        assertEquals(LocalAdb.Access.UNSUPPORTED, result.access)
        assertFalse(result.complete)
        assertTrue(result.verified.isEmpty())
        assertTrue(client.commands.isEmpty())
        assertEquals(0, client.connections)
        assertFalse(client.closed)
    }

    @Test fun cancelBeforeClientCreationDoesNoWork() {
        var created = false
        val operation = UsbPermissionSetup.Operation(context, { created = true; FakeClient() }) { false }
        operation.cancel()
        assertFalse(operation.run().complete)
        assertFalse(created)
    }

    @Test fun handshakeAndItsCancellationCallbackAreNeverReached() {
        val client = FakeClient()
        val operation = operation(client)
        client.onConnect = operation::cancel
        assertFalse(operation.run().complete)
        assertEquals(0, client.connections)
        assertFalse(operation.isCancelled)
        assertFalse(client.cancelled)
        assertTrue(client.commands.isEmpty())
    }

    @Test fun firstGrantAndItsCancellationCallbackAreNeverReached() {
        val client = FakeClient()
        val operation = operation(client)
        client.onCommand = { operation.cancel() }
        val result = operation.run()
        assertTrue(client.commands.isEmpty())
        assertFalse(client.cancelled)
        assertFalse(operation.isCancelled)
        assertTrue(result.verified.isEmpty())
        assertFalse(result.complete)
    }

    @Test fun accessibilityCommandPreservesExistingServicesAndAvoidsDuplicates() {
        val target = "${context.packageName}/com.shilapi.xcertplay.UsbAutoConfirmService"
        for (initial in listOf("other.package/.Service", "other.package/.Service:$target", "null", "")) {
            val actual = runAccessibilityCommand(initial)
            val expected = "UNCHANGED"
            assertEquals(expected, actual.trim())
        }
    }

    @Test fun failedOrMalformedServiceReadNeverReplacesExistingList() {
        assertEquals("UNCHANGED", runAccessibilityCommand("other.package/.Service", readFails = true).trim())
        assertEquals("UNCHANGED", runAccessibilityCommand("Permission denied").trim())
    }

    @Test fun manualCommandIsHarmlessAndNeverContainsPermissionWrites() {
        assertEquals(":", UsbPermissionSetup.manualCommand(context.packageName))
        for (permission in permissions) assertEquals(":", UsbPermissionSetup.command(permission, context.packageName))
        val script = shellFixture("other.package/.Service") + "\n" + UsbPermissionSetup.manualCommand(context.packageName) + "; printf '%s' \"\$saved\""
        assertEquals("UNCHANGED", runShell(script).trim())
    }

    @Test fun cancelWithoutAnAutomaticDialogCannotCreateAWorker() = refusedUiAttempt { activity ->
        DiPlayActivity::class.java.getDeclaredMethod("cancelUsbPermissionSetup").apply { isAccessible = true }.invoke(activity)
    }

    @Test fun activityStopAfterRejectedSetupCannotCreateAWorker() = refusedUiAttempt { activity ->
        DiPlayActivity::class.java.getDeclaredMethod("onStop").apply { isAccessible = true }.invoke(activity)
    }

    @Test fun activityDestroyAfterRejectedSetupCannotCreateAWorker() = refusedUiAttempt { activity ->
        DiPlayActivity::class.java.getDeclaredMethod("onDestroy").apply { isAccessible = true }.invoke(activity)
    }

    private fun refusedUiAttempt(finish: (DiPlayActivity) -> Unit) {
        val activity = Robolectric.buildActivity(DiPlayActivity::class.java).get()
        activity.setTheme(android.R.style.Theme_Material_NoActionBar)
        val client = FakeClient()
        var operationsCreated = 0
        activity.usbPermissionOperationFactory = { operationsCreated++; operation(client) }
        val previousDialog = ShadowAlertDialog.getLatestAlertDialog()
        val preferences = context.getSharedPreferences("diplay_navigation_wheel", android.content.Context.MODE_PRIVATE)
        val before = preferences.all.toMap()
        repeat(2) {
            DiPlayActivity::class.java.getDeclaredMethod("autoApplyPermissions").apply { isAccessible = true }.invoke(activity)
        }
        finish(activity)
        shadowOf(Looper.getMainLooper()).idle()
        assertEquals(0, operationsCreated)
        assertEquals(0, client.connections)
        assertTrue(client.commands.isEmpty())
        assertFalse(client.cancelled)
        assertFalse(client.closed)
        assertSame(previousDialog, ShadowAlertDialog.getLatestAlertDialog())
        assertEquals(before, preferences.all)
    }

    private fun operation(client: FakeClient, granted: (UsbPermissionSetup.Permission) -> Boolean = { false }) =
        UsbPermissionSetup.Operation(context, { factories++; client }) { grantChecks++; granted(it) }

    private fun shellFixture(initial: String, readFails: Boolean = false): String =
        """current='$initial'; saved=UNCHANGED;
            settings() {
                if [ "${'$'}1" = get ]; then ${if (readFails) "return 1" else "printf '%s' \"${'$'}current\""};
                elif [ "${'$'}3" = enabled_accessibility_services ]; then saved="${'$'}4"; fi;
            };
            appops() { return 0; };""".trimIndent()

    private fun runAccessibilityCommand(initial: String, readFails: Boolean = false): String = runShell(
        shellFixture(initial, readFails) + "\n( " + UsbPermissionSetup.command(
            UsbPermissionSetup.Permission.ACCESSIBILITY, context.packageName) +
            "; printf '%s' \"\$saved\" ); result=\$?; if [ \"\$result\" != 0 ]; then printf UNCHANGED; fi;"
    )

    @Test fun shellHarnessPreservesQuotedArgumentsAndLiteralMetacharacters() {
        assertEquals("two words\n\"quoted\"\n\$literal", runShell(
            "printf '%s\\n' 'two words' '\"quoted\"'; printf '%s' '\$literal'",
        ))
    }

    private fun runShell(script: String): String {
        val gitShell = File(System.getenv("ProgramFiles") ?: "C:/Program Files", "Git/bin/sh.exe")
        val shell = System.getenv("DIPLAY_TEST_SHELL")
            ?: if (File.separatorChar == '\\' && gitShell.isFile) gitShell.absolutePath else "sh"
        // Windows reconstructs the -c argument and can strip its embedded quotes.
        // Stdin delivers the exact Android shell command, without a host command-line parser.
        val process = ProcessBuilder(shell).redirectErrorStream(true).start()
        try {
            process.outputStream.bufferedWriter(Charsets.UTF_8).use { it.write(script + "\n") }
            assertTrue(process.waitFor(3, TimeUnit.SECONDS))
            return process.inputStream.bufferedReader(Charsets.UTF_8).readText()
        } finally {
            process.destroy()
        }
    }

    private class FakeClient : UsbPermissionSetup.Client {
        var connections = 0
        var access = LocalAdb.Access.READY
        var output: String? = "DIPLAY_PERMISSION_EXIT:0"
        var onConnect: () -> Unit = {}
        var onCommand: (String) -> Unit = {}
        var block = false
        @Volatile var cancelled = false
        @Volatile var closed = false
        val commands = CopyOnWriteArrayList<String>()
        val entered = CountDownLatch(1)
        val release = CountDownLatch(1)
        val finished = CountDownLatch(1)
        override fun connect(): LocalAdb.Access { connections++; onConnect(); return access }
        override fun shell(command: String): String? {
            commands += command
            entered.countDown()
            onCommand(command)
            if (block) assertTrue(release.await(3, TimeUnit.SECONDS))
            return if (cancelled) null else output
        }
        override fun cancel() { cancelled = true; release.countDown() }
        override fun close() { closed = true; finished.countDown() }
    }
}
