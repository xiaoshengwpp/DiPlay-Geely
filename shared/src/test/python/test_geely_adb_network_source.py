"""Source-level regressions for the removed live ADB/network adapters.

These complement the negative JVM tests and injected protocol/policy tests. A
feature flag must not be able to bring back keys, sockets, or platform discovery.
"""
from pathlib import Path
import re
import unittest

MAIN = Path(__file__).resolve().parents[2] / "main/java/com/shilapi/xcertplay"
FILES = (
    "adb/LocalAdb.kt",
    "network/CarHotspotTethering.kt",
    "network/HotspotJoinRepair.kt",
    "network/HotspotJoinRepairMain.kt",
    "network/WifiScanPause.kt",
    "network/WifiScanPauseMain.kt",
    "network/HotspotJoinCapability.kt",
    "network/HotspotJoinFirmware.kt",
    "network/CarHotspotAdbFallback.kt",
)


class GeelyAdbNetworkSourceTest(unittest.TestCase):
    def source(self, filename):
        return (MAIN / filename).read_text()

    def test_live_access_implementation_is_removed_not_hidden_behind_a_flag(self):
        forbidden = (
            r"\bGeelyVendorPolicy\b",
            r"\bAdbKeys\s*\.",
            r"\bSocket\s*\(",
            r"\bInetSocketAddress\b",
            r"\bgetSystemService\s*\(",
            r"\bClass\s*\.\s*forName\s*\(",
            r"\bgetDeclaredField\s*\(",
            r"\bgetSharedPreferences\s*\(",
            r"\bHotspotJoinPlatform\s*\.\s*connect\s*\(",
            r"\bOs\s*\.",
            r"\bexitProcess\s*\(",
        )
        for filename in FILES:
            source = self.source(filename)
            with self.subTest(filename=filename):
                for pattern in forbidden:
                    self.assertNotRegex(source, pattern)

    def test_local_adb_entry_points_have_unconditional_disabled_results(self):
        source = self.source("adb/LocalAdb.kt")
        for signature in (
            "fun connect(mayAsk: Boolean): Access = Access.UNSUPPORTED",
            "fun shell(command: String): String? = null",
            "fun shell(command: String, readTimeoutMillis: Int): String? = null",
            "fun openShell(command: String): InteractiveShell? = null",
            "override fun close() = Unit",
            "fun cancelPendingOperations() = Unit",
        ):
            self.assertIn(signature, source)
        for state in ("key", "host", "port", "socket", "input", "output", "nextStreamId"):
            self.assertNotRegex(source, rf"\b(?:val|var)\s+{state}\b")
        for removed in ("handshake", "awaitApproval", "closeQuietly"):
            self.assertNotRegex(source, rf"\bfun\s+{removed}\b")
        self.assertIn("class InteractiveShell internal constructor(", source)
        self.assertIn("internal class InteractiveResponseBuffer", source)

    def test_car_hotspot_adapter_cannot_inspect_or_change_system_state(self):
        source = self.source("network/CarHotspotTethering.kt")
        self.assertIn("fun permitted(context: Context): Boolean = false", source)
        self.assertRegex(source, re.compile(
            r"fun enable\(\s*context: Context,.*?\): Result = Result.PERMISSION_REQUIRED",
            re.DOTALL,
        ))
        self.assertNotIn("ConnectivityManager", source)
        self.assertNotIn("Settings.System", source)
        self.assertNotIn("CarHotspotStatus", source)
        self.assertNotIn("CarHotspotAdbFallback", source)

    def test_hotspot_repair_retains_only_inert_api_and_pure_codecs(self):
        source = self.source("network/HotspotJoinRepair.kt")
        self.assertIn(
            "fun run(context: Context, action: Action, token: String? = null): Result = Result(Code.UNSUPPORTED)",
            source,
        )
        self.assertIn("fun cancel() = Unit", source)
        self.assertNotRegex(source, r"\b(?:Thread|LocalAdb)\s*\(")
        self.assertIn("internal fun parse(output: String?): Result", source)

    def test_privileged_mains_are_empty_and_cannot_exit_the_process(self):
        for filename in ("HotspotJoinRepairMain.kt", "WifiScanPauseMain.kt"):
            with self.subTest(filename=filename):
                source = self.source("network/" + filename)
                self.assertIn("@JvmStatic fun main(args: Array<String>) = Unit", source)
                self.assertNotRegex(source, r"\bfun\s+(?:execute|connect)\s*\(")
                self.assertNotRegex(source, r"\bThread\s*\(")
                self.assertNotIn("android.os.ServiceManager", source)

    def test_scan_control_has_no_worker_journal_or_live_service_defaults(self):
        source = self.source("network/WifiScanPause.kt")
        for signature in (
            "fun pause() = Unit",
            "fun close() = Unit",
            "fun restoreIfNeeded(context: Context) = Unit",
        ):
            self.assertIn(signature, source)
        self.assertRegex(source, r"fun eligible\([^\n]+\): Boolean =\s*false")
        self.assertNotRegex(source, r"\b(?:BydAdbShell|WifiScanPauseSession|WifiScanPauseJournal)\s*\(")
        self.assertNotIn("Executors", source)
        adapter = self.source("network/WifiScanPauseMain.kt")
        self.assertIn("listenerType: Class<*>? = null", adapter)
        self.assertNotIn("android.net.wifi.IBooleanListener", adapter)


    def test_platform_has_only_offline_model_reflection_and_inert_service_access(self):
        source = self.source("network/HotspotJoinPlatform.kt")
        for forbidden in ("ServiceManager", "IWifiManager", "setSoftApConfiguration", "getSoftApConfiguration",
                          "getWifiApEnabledState", "invoke(wifi", "HotspotJoinFirmware.current",
                          "HotspotJoinCapability.read"):
            self.assertNotIn(forbidden, source)
        self.assertIn('override fun read(): Any = throw UnsupportedOperationException(', source)
        self.assertIn('fun connect(): HotspotJoinPlatform = throw UnsupportedOperationException(', source)
        self.assertIn('override fun write(config: Any): Boolean = false', source)
        self.assertIn('override fun hotspotOff(): Boolean? = null', source)
        self.assertIn('private val capability: () -> HotspotJoinCapability.Snapshot? = { null }', source)
        self.assertIn('val codec = object : HotspotJoinJournal.Codec<Any>', source)
        self.assertIn('override fun repaired(config: Any): Any?', source)

    def test_disabled_adapters_cannot_register_callbacks_or_discover_firmware(self):
        capability = self.source("network/HotspotJoinCapability.kt")
        self.assertIn('fun read(wifi: Any): Snapshot? = null', capability)
        self.assertIn('fun read(registration: Registration, timeoutMillis: Long = 2_000): Snapshot? = null', capability)
        for forbidden in ('register.invoke', 'registration.register', 'Proxy', 'Parcel', 'CountDownLatch'):
            self.assertNotIn(forbidden, capability)
        firmware = self.source("network/HotspotJoinFirmware.kt")
        self.assertIn('fun current(): String? = null', firmware)
        for forbidden in ('SystemProperties', 'java.io.File', 'inputStream(', 'Build.VERSION', '/apex/'):
            self.assertNotIn(forbidden, firmware)
        scan = self.source("network/WifiScanPauseMain.kt")
        self.assertIn('fun set(enabled: Boolean): WifiScanSwitchResult = WifiScanSwitchResult.UNSUPPORTED', scan)
        self.assertIn('internal fun read(): Boolean? = null', scan)
        self.assertNotIn('java.lang.reflect', scan)
        self.assertNotIn('android.os.', scan)

    def test_disabled_command_templates_never_emit_a_shell_invocation(self):
        for filename in ('HotspotJoinRepair.kt', 'WifiScanPause.kt'):
            source = self.source('network/' + filename)
            with self.subTest(filename=filename):
                self.assertRegex(source, r'fun command\([^\n]+\): String = ""')
                self.assertNotIn('app_process', source)
                self.assertNotIn('CLASSPATH=', source)

    def test_adb_fallback_cannot_generate_or_execute_hotspot_commands(self):
        source = self.source("network/CarHotspotAdbFallback.kt")
        self.assertIn('shell: (String, Int) -> String?, log: (String) -> Unit): Boolean = false', source)
        self.assertIn('fun advertisedCommand(service: String, help: String?): String? = null', source)
        for forbidden in ('shell(', 'start-tethering', 'cmd $service', 'dumpsys wifi'):
            self.assertNotIn(forbidden, source)
        self.assertIn('internal fun apState(dump: String?): Boolean?', source)
        self.assertIn('internal fun accepted(output: String?): Boolean', source)

if __name__ == "__main__":
    unittest.main()
