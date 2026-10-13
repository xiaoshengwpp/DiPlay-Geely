package com.shilapi.xcertplay.hud

import android.content.Context
import android.content.ContextWrapper
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.media.MediaRecorder
import android.os.Build
import android.telecom.Connection
import com.shilapi.xcertplay.iap2.wire.Iap2Frame
import com.shilapi.xcertplay.media.microphoneAudioSource
import com.shilapi.xcertplay.network.GeelyHeadUnit
import com.shilapi.xcertplay.network.WifiScanPauseMain
import com.shilapi.xcertplay.telecom.PhoneAudioCall
import com.shilapi.xcertplay.telecom.PhoneAudioConnectionService
import com.shilapi.xcertplay.telecom.PhoneAudioRoute
import com.shilapi.xcertplay.telecom.TelecomPhoneAudioBackend
import com.shilapi.xcertplay.telecom.VoipCallWatcher
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.util.ReflectionHelpers

/** Geely adapters are available; unrelated OEM facades still fail before any platform side effect. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [29], manifest = Config.NONE)
class GeelyHardwareBoundary0218Test {
    private val rejectingContext = object : ContextWrapper(null) {
        override fun getApplicationContext(): Context = error("vendor context accessed")
        override fun getPackageManager(): PackageManager = error("vendor packages probed")
        override fun getPackageName(): String = error("vendor package checked")
        override fun getSharedPreferences(name: String, mode: Int): SharedPreferences = error("vendor preferences accessed")
        override fun getSystemService(name: String): Any = error("vendor system service accessed")
    }

    @Test fun availableGeelyAdaptersDoNotEnableUnverifiedOemPhoneOrClusterBackends() {
        assertTrue(GeelyVendorPolicy.geelyInput)
        assertTrue(GeelyVendorPolicy.geelyMicrophoneOverride)
        assertTrue(GeelyVendorPolicy.foregroundMediaKeys)
        assertFalse(GeelyVendorPolicy.phoneAudioRoute)
        assertFalse(GeelyVendorPolicy.fceCluster)
    }

    @Test fun importedPreferencesAndSetterCallsCannotEnablePhoneOrFceOutput() {
        val app = RuntimeEnvironment.getApplication()
        val prefs = app.getSharedPreferences("diplay_byd_outputs", Context.MODE_PRIVATE)
        val keys = listOf("phone_audio_route_experimental", "phone_audio_route_apps_experimental", "fce_cluster_guidance_experimental")
        prefs.edit().also { edit -> keys.forEach { edit.putBoolean(it, true) } }.commit()
        try {
            for (context in listOf(rejectingContext, app)) {
                BydOutputSettings.setPhoneAudioRoute(context, true)
                BydOutputSettings.setPhoneAudioRouteApps(context, true)
                BydOutputSettings.setFceCluster(context, true)
                assertFalse(BydOutputSettings.phoneAudioRoute(context))
                assertFalse(BydOutputSettings.phoneAudioRouteApps(context))
                assertFalse(BydOutputSettings.fceCluster(context))
                assertFalse(BydOutputSettings.fceClusterAvailable())
            }
            // No-op setters do not mutate a forged preference either.
            BydOutputSettings.setPhoneAudioRoute(app, false)
            BydOutputSettings.setPhoneAudioRouteApps(app, false)
            BydOutputSettings.setFceCluster(app, false)
            keys.forEach { assertTrue(prefs.getBoolean(it, false)) }
        } finally { prefs.edit().clear().commit() }
    }

    @Test fun phoneFacadeAndWatcherStayInertAcrossRepeatedLifecycleCalls() {
        val watcher = VoipCallWatcher(rejectingContext) { error("VOIP callback invoked") }
        repeat(3) {
            PhoneAudioRoute.attach(rejectingContext)
            PhoneAudioRoute.settingsChanged(rejectingContext)
            PhoneAudioRoute.carPlayCall(true)
            PhoneAudioRoute.voipApp(true)
            assertFalse(PhoneAudioRoute.carPlayCallsUseCarAudio())
            assertFalse(PhoneAudioRoute.ownCarPlayCallActive())
            PhoneAudioRoute.carPlayCall(false)
            PhoneAudioRoute.voipApp(false)
            watcher.start()
            watcher.stop()
        }
        assertLazyNotInitialized(watcher, "audio")
        assertLazyNotInitialized(watcher, "handler")
        assertEquals(PhoneAudioRoute.Capability.NO_VERIFIED_GEELY_BACKEND, PhoneAudioRoute.capability())
        val facadeFields = PhoneAudioRoute::class.java.declaredFields.map { it.name }
        for (name in listOf("executor", "controller", "watcher")) {
            assertFalse("disabled facade retains $name", facadeFields.any { it == name || it == name + "\$delegate" })
        }
    }

    @Test fun telecomBackendAndDirectServiceCallbacksCannotCreateAnActiveCall() {
        val backend = TelecomPhoneAudioBackend(rejectingContext)
        val service = PhoneAudioConnectionService()
        repeat(3) {
            assertFalse(backend.start())
            assertFalse(invokeNoArgs(backend, "place") as Boolean)
            backend.stop()
            assertFalse(backend.isActive())
            val outgoing = service.onCreateOutgoingConnection(null, null)
            val incoming = service.onCreateIncomingConnection(null, null)
            assertEquals(Connection.STATE_DISCONNECTED, outgoing.state)
            assertEquals(Connection.STATE_DISCONNECTED, incoming.state)
            service.onCreateOutgoingConnectionFailed(null, null)
            service.onCreateIncomingConnectionFailed(null, null)
            assertEquals(0L, PhoneAudioCall.expect().count)
            assertEquals(Connection.STATE_DISCONNECTED, PhoneAudioCall.create().state)
            PhoneAudioCall.end()
            assertFalse(PhoneAudioCall.isActive())
        }
        assertNull(field(PhoneAudioCall, "connection"))
        assertNull(field(PhoneAudioCall, "created"))
    }

    @Test fun fceBridgeAndDirectPropertyWriterNeverProbeOrWrite() {
        val writer = FceCarPropertyWriter()
        FceClusterBridge.resetForTest({ _, _ -> error("vendor property written") }, rejectingContext)
        try {
            repeat(3) {
                FceClusterBridge.initialize(rejectingContext)
                FceClusterBridge.onFrame(Iap2Frame(BydHudRouteState.ROUTE_GUIDANCE_UPDATE, byteArrayOf()))
                FceClusterBridge.clear()
                invokeNoArgs(FceClusterBridge, "tick")
                invokeNoArgs(FceClusterBridge, "refreshLocked")
                assertFalse(FceClusterBridge.available())
                assertFalse(FceCarPropertyWriter.isAvailable())
                assertFalse(writer.setInt(FceClusterProperties.NAVI_STATUS, 2))
                assertNull(invokeNoArgs(writer, "lookup"))
            }
            assertFalse(field(FceClusterBridge, "tickStarted") as Boolean)
            assertNull(field(writer, "binder"))
        } finally { FceClusterBridge.resetForTest({ _, _ -> error("vendor write") }, null) }
    }

    @Test fun newBydFacadesAndShellMainsNeverReadContextOrScheduleWorkers() {
        val owner = Any()
        repeat(3) {
            BydCallPopupGuard.update(rejectingContext, owner, true) { error("connection callback invoked") }
            BydCallPopupGuard.update(rejectingContext, owner, false) { error("connection callback invoked") }
            val stop = BydClusterSpeedReader.observe(rejectingContext) { error("speed callback invoked") }
            stop(); stop()
            BydCallPopupTool.main(arrayOf("a".repeat(32)))
            WifiScanPauseMain.main(arrayOf("pause"))
            WifiScanPauseMain.main(arrayOf("restore"))
        }
        assertEquals("OFF", BydCallPopupGuard.status)
        assertLazyNotInitialized(BydCallPopupGuard, "worker")
        assertNull(field(BydCallPopupGuard, "owner"))
        assertNull(field(BydCallPopupGuard, "app"))
        assertNull(field(BydCallPopupGuard, "adb"))
        assertLazyNotInitialized(BydNavigationOutputs, "fceCluster")
    }

    @Test fun brandLikeIdentifiersCannotEnableAutomaticSiriCompatibilityOrAlterCalls() {
        val names = listOf("PRODUCT", "DEVICE", "MODEL", "MANUFACTURER", "BRAND")
        val before = names.associateWith { Build::class.java.getField(it).get(null) }
        try {
            names.forEach { ReflectionHelpers.setStaticField(Build::class.java, it, "geely_fx11") }
            assertFalse(GeelyHeadUnit.detected)
            assertEquals(MediaRecorder.AudioSource.VOICE_RECOGNITION, microphoneAudioSource("speechrecognition", false))
            assertEquals(MediaRecorder.AudioSource.VOICE_RECOGNITION, microphoneAudioSource("speechrecognition", true))
            assertEquals(MediaRecorder.AudioSource.VOICE_COMMUNICATION, microphoneAudioSource("telephony", false))
            assertEquals(MediaRecorder.AudioSource.MIC, microphoneAudioSource("telephony", true))
            assertEquals(MediaRecorder.AudioSource.MIC, microphoneAudioSource("default", false))
        } finally { before.forEach { (name, value) -> ReflectionHelpers.setStaticField(Build::class.java, name, value) } }
    }

    private fun field(target: Any, name: String): Any? = target.javaClass.getDeclaredField(name)
        .apply { isAccessible = true }.get(target)

    private fun assertLazyNotInitialized(target: Any, name: String) {
        assertFalse("${target.javaClass.simpleName} $name initialized", (field(target, name + "\$delegate") as Lazy<*>).isInitialized())
    }

    private fun invokeNoArgs(target: Any, name: String): Any? = target.javaClass.getDeclaredMethod(name)
        .apply { isAccessible = true }.invoke(target)
}
