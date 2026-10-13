package com.shilapi.xcertplay

import android.content.ComponentName
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.content.ServiceConnection
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.content.pm.ResolveInfo
import android.content.pm.ServiceInfo
import android.os.Binder
import android.os.Build
import android.os.Looper
import android.os.RemoteException
import com.geely.lib.oneosapi.input.IInputListener
import com.geely.lib.oneosapi.input.IInputManager
import com.shilapi.xcertplay.airplay.CarPlayMediaButton
import com.shilapi.xcertplay.media.CarPlayNowPlaying
import com.shilapi.xcertplay.orchestration.CarPlayController
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mockito.*
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.LooperMode
import org.robolectric.util.ReflectionHelpers
import java.time.Duration

/** Exercise real start/stop, Binder registration, retries and late callbacks, not just source text. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28, 30, 33], manifest = Config.NONE)
@LooperMode(LooperMode.Mode.PAUSED)
class GeelyWheelKeysLifecycleTest {
    private lateinit var context: BindingContext
    private val owners = mutableListOf<CarPlayController>()
    private val component = ComponentName(GeelyWheelKeys.INPUT_PACKAGE, GeelyWheelKeys.INPUT_CLASS)

    @Before fun setup() {
        GeelyWheelKeys.stop()
        idle()
        context = BindingContext(RuntimeEnvironment.getApplication())
        GeelyInputSettings.setManualEnabled(context, true)
        AirPlayPersistence.saveCarBluetoothAudio(context, false)
    }

    @After fun cleanup() {
        owners.forEach(CarPlayMediaKeys::detach)
        GeelyWheelKeys.stop()
        idle()
        GeelyInputSettings.setManualEnabled(context, false)
        AirPlayPersistence.saveCarBluetoothAudio(context, false)
    }

    @Test fun manualConsentIsPersistedAndDefaultsToOff() {
        context.getSharedPreferences("diplay_geely_input", Context.MODE_PRIVATE).edit().clear().commit()
        assertFalse(GeelyInputSettings.manualEnabled(context))
        assertFalse(GeelyInputSettings.enabled(context))
        GeelyInputSettings.setManualEnabled(context, true)
        assertTrue(GeelyInputSettings.manualEnabled(ContextWrapper(context)))
        GeelyInputSettings.setManualEnabled(context, false)
        assertFalse(GeelyInputSettings.manualEnabled(context))
    }

    @Test @Config(sdk = [30]) fun exactKnownHardwareUsesAutomaticModeWithoutManualConsent() {
        val names = listOf("PRODUCT", "MODEL", "MANUFACTURER")
        val before = names.associateWith { Build::class.java.getField(it).get(null) }
        try {
            ReflectionHelpers.setStaticField(Build::class.java, "PRODUCT", "geely_fx11")
            ReflectionHelpers.setStaticField(Build::class.java, "MODEL", "CS11_A4")
            ReflectionHelpers.setStaticField(Build::class.java, "MANUFACTURER", "DesaySV")
            GeelyInputSettings.setManualEnabled(context, false)
            GeelyWheelKeys.start(context, owner())
            idle()
            assertEquals(1, context.connections.size)
        } finally {
            before.forEach { (name, value) -> ReflectionHelpers.setStaticField(Build::class.java, name, value) }
        }
    }

    @Test fun absentDisabledPrivateOrPermissionDeniedServicesNeverBindEvenWithConsent() {
        val original = context.service
        val variants = listOf<(ServiceInfo) -> Unit>(
            { it.enabled = false }, { it.applicationInfo.enabled = false }, { it.exported = false },
            { it.permission = "vendor.permission.INPUT" }, { it.packageName = "another.vendor" },
            { it.name = "another.vendor.InputService" },
        )
        variants.forEach { change ->
            context.service = usableService().also(change)
            GeelyWheelKeys.start(context, owner())
            idle()
            GeelyWheelKeys.stop()
            idle()
        }
        context.service = null
        GeelyWheelKeys.start(context, owner())
        idle()
        assertTrue(context.connections.isEmpty())
        context.service = original
    }

    @Test @Config(sdk = [25, 28, 30, 33]) fun expectedInterfaceRegistersOnceAndStopReleasesListenerAndBinding() {
        val owner = owner()
        GeelyWheelKeys.start(context, owner)
        GeelyWheelKeys.start(context, owner)
        idle()
        assertEquals(1, context.connections.size)
        val connection = context.connections.single()
        val vendor = VendorInput()
        connection.onServiceConnected(component, vendor)
        connection.onServiceConnected(component, vendor)
        idle()
        assertEquals(1, vendor.registrations.size)
        assertArrayEquals(GeelyWheelKeyCodes.ALL, vendor.codes.single())
        GeelyWheelKeys.stop()
        GeelyWheelKeys.stop()
        idle()
        assertEquals(vendor.registrations, vendor.unregistered)
        assertEquals(listOf(connection), context.unbound)
    }

    @Test fun incompatibleAndNullBindersAreReleasedWithoutProtocolCallsOrRetry() {
        GeelyWheelKeys.start(context, owner())
        idle()
        context.connections.single().onServiceConnected(component, Binder().apply {
            attachInterface(null, "different.input.Protocol")
        })
        idle()
        advanceRetry()
        assertEquals(1, context.connections.size)
        assertEquals(1, context.unbound.size)
        GeelyWheelKeys.stop()
        GeelyWheelKeys.start(context, owner())
        idle()
        context.connections.last().onNullBinding(component)
        idle()
        advanceRetry()
        assertEquals(2, context.connections.size)
        assertEquals(2, context.unbound.size)
    }

    @Test fun stopBeforeConnectionRejectsLateCallbackAndDisconnectCannotReviveIt() {
        GeelyWheelKeys.start(context, owner())
        idle()
        val connection = context.connections.single()
        GeelyWheelKeys.stop()
        val vendor = VendorInput()
        connection.onServiceConnected(component, vendor)
        connection.onServiceDisconnected(component)
        idle()
        advanceRetry()
        assertTrue(vendor.registrations.isEmpty())
        assertEquals(1, context.connections.size)
        assertEquals(listOf(connection), context.unbound)
    }

    @Test fun disconnectRetriesOnlyForCurrentGenerationAndStopCancelsPendingRetry() {
        GeelyWheelKeys.start(context, owner())
        idle()
        val first = context.connections.single()
        first.onServiceDisconnected(component)
        idle()
        advanceRetry()
        assertEquals(2, context.connections.size)
        val second = context.connections.last()
        second.onServiceDisconnected(component)
        idle()
        GeelyWheelKeys.stop()
        advanceRetry()
        first.onBindingDied(component)
        advanceRetry()
        assertEquals(2, context.connections.size)
        assertEquals(listOf(first, second), context.unbound)
    }

    @Test fun failedBindRetriesButStoppingBeforeFirstPostedBindNeverBinds() {
        GeelyWheelKeys.start(context, owner())
        GeelyWheelKeys.stop()
        idle()
        assertTrue(context.connections.isEmpty())
        context.bindResult = false
        GeelyWheelKeys.start(context, owner())
        idle()
        advanceRetry()
        assertEquals(2, context.connections.size)
        GeelyWheelKeys.stop()
        advanceRetry()
        assertEquals(2, context.connections.size)
    }

    @Test fun bindAndRegisterSecurityDenialsAreNotRetried() {
        context.bindFailure = SecurityException("vendor bind denied")
        GeelyWheelKeys.start(context, owner())
        idle()
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMinutes(1))
        assertEquals(1, context.connections.size)
        context.bindFailure = null
        GeelyWheelKeys.stop()
        GeelyWheelKeys.start(context, owner())
        idle()
        val vendor = VendorInput().apply { registrationDenied = true }
        context.connections.last().onServiceConnected(component, vendor)
        idle()
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMinutes(1))
        assertEquals(2, context.connections.size)
        assertEquals(vendor.registrations, vendor.unregistered)
    }

    @Test fun transientBindFailureHasBoundedExponentialBackoff() {
        context.bindResult = false
        GeelyWheelKeys.start(context, owner())
        idle()
        assertEquals(1, context.connections.size)
        advanceRetry()
        assertEquals(2, context.connections.size)
        advanceRetry()
        assertEquals(2, context.connections.size)
        advanceRetry()
        assertEquals(3, context.connections.size)
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(12_000))
        assertEquals(4, context.connections.size)
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMinutes(1))
        assertEquals(4, context.connections.size)
    }

    @Test fun nullConnectedBinderNeverRegistersOrRetries() {
        GeelyWheelKeys.start(context, owner())
        idle()
        context.connections.single().onServiceConnected(component, null)
        idle()
        advanceRetry()
        assertEquals(1, context.connections.size)
        assertEquals(1, context.unbound.size)
    }

    @Test fun registrationFailureIsCleanedUpAndRetried() {
        GeelyWheelKeys.start(context, owner())
        idle()
        val vendor = VendorInput().apply { registrationFailure = true }
        context.connections.single().onServiceConnected(component, vendor)
        idle()
        assertEquals(vendor.registrations, vendor.unregistered)
        assertEquals(1, context.unbound.size)
        advanceRetry()
        assertEquals(2, context.connections.size)
    }

    @Test fun stopDuringRegistrationStillUnregistersAndNeverDispatches() {
        val controller = attach()
        val vendor = VendorInput().apply { onRegister = { GeelyWheelKeys.stop() } }
        context.connections.single().onServiceConnected(component, vendor)
        idle()
        val listener = vendor.registrations.single()
        listener.onShortClick(GeelyWheelKeyCodes.KEY_NEXT, 0)
        listener.onLongPressTriggered(GeelyWheelKeyCodes.KEY_VOICE_ASSIST, 0)
        idle()
        assertEquals(vendor.registrations, vendor.unregistered)
        verify(controller, never()).sendMediaButton(anyInt())
        verify(controller, never()).requestSiri()
    }

    @Test fun oldBindingCallbacksAndListenersCannotAffectAReplacementController() {
        val firstOwner = attach()
        val firstConnection = context.connections.single()
        val firstVendor = VendorInput()
        firstConnection.onServiceConnected(component, firstVendor)
        idle()
        val oldListener = firstVendor.registrations.single()
        val nextOwner = attach()
        val nextConnection = context.connections.last()
        val nextVendor = VendorInput()
        nextConnection.onServiceConnected(component, nextVendor)
        firstConnection.onServiceConnected(component, VendorInput())
        firstConnection.onServiceDisconnected(component)
        oldListener.onShortClick(GeelyWheelKeyCodes.KEY_NEXT, 0)
        oldListener.onLongPressTriggered(GeelyWheelKeyCodes.KEY_VOICE_ASSIST, 0)
        idle()
        advanceRetry()
        verify(firstOwner, never()).sendMediaButton(anyInt())
        verify(firstOwner, never()).requestSiri()
        verify(nextOwner, never()).sendMediaButton(anyInt())
        verify(nextOwner, never()).requestSiri()
        assertEquals(2, context.connections.size)
        nextVendor.registrations.single().onShortClick(GeelyWheelKeyCodes.KEY_NEXT, 0)
        idle()
        verify(nextOwner).sendMediaButton(CarPlayMediaButton.NEXT)
    }

    @Test fun stoppedListenerCannotDispatchAfterSameControllerRestarts() {
        val controller = attach()
        val vendor = VendorInput()
        context.connections.single().onServiceConnected(component, vendor)
        idle()
        val oldListener = vendor.registrations.single()
        // Both already queued and subsequently arriving events must lose their old generation.
        oldListener.onShortClick(GeelyWheelKeyCodes.KEY_PLAY_PAUSE, 0)
        oldListener.onLongPressTriggered(GeelyWheelKeyCodes.KEY_VOICE_ASSIST, 0)
        GeelyWheelKeys.stop()
        GeelyWheelKeys.start(context, controller)
        idle()
        oldListener.onShortClick(GeelyWheelKeyCodes.KEY_PLAY_PAUSE, 0)
        oldListener.onLongPressTriggered(GeelyWheelKeyCodes.KEY_VOICE_ASSIST, 0)
        idle()
        verify(controller, never()).sendMediaButton(anyInt())
        verify(controller, never()).requestSiri()
    }

    @Test @Config(sdk = [25, 28, 30, 33]) fun queuedCallbacksCannotTransferToANewSessionOnTheSameController() {
        val controller = attach()
        val vendor = VendorInput()
        context.connections.single().onServiceConnected(component, vendor)
        idle()
        val listener = vendor.registrations.single()
        listener.onShortClick(GeelyWheelKeyCodes.KEY_NEXT, 0)
        listener.onLongPressTriggered(GeelyWheelKeyCodes.KEY_VOICE_ASSIST, 0)
        // The binding and controller remain current, but their phone session has changed.
        `when`(controller.activeAirPlaySessionToken()).thenReturn(Any())
        idle()
        verify(controller, never()).sendMediaButton(anyInt())
        verify(controller, never()).requestSiri()
        // New callbacks from the unchanged binding must still work for the replacement session.
        listener.onShortClick(GeelyWheelKeyCodes.KEY_NEXT, 0)
        listener.onLongPressTriggered(GeelyWheelKeyCodes.KEY_VOICE_ASSIST, 0)
        idle()
        verify(controller).sendMediaButton(CarPlayMediaButton.NEXT)
        verify(controller).requestSiri()
        assertEquals(1, context.connections.size)
    }

    @Test @Config(sdk = [25, 28, 30, 33]) fun callbacksWithoutASessionCannotBecomeValidWhileWaitingForTheHandler() {
        val controller = attach()
        val vendor = VendorInput()
        context.connections.single().onServiceConnected(component, vendor)
        idle()
        val listener = vendor.registrations.single()
        `when`(controller.activeAirPlaySessionToken()).thenReturn(null)
        listener.onShortClick(GeelyWheelKeyCodes.KEY_PLAY_PAUSE, 0)
        listener.onLongPressTriggered(GeelyWheelKeyCodes.KEY_VOICE_ASSIST, 0)
        `when`(controller.activeAirPlaySessionToken()).thenReturn(Any())
        idle()
        verify(controller, never()).sendMediaButton(anyInt())
        verify(controller, never()).requestSiri()
        listener.onShortClick(GeelyWheelKeyCodes.KEY_PLAY_PAUSE, 0)
        listener.onLongPressTriggered(GeelyWheelKeyCodes.KEY_VOICE_ASSIST, 0)
        idle()
        verify(controller).sendMediaButton(CarPlayMediaButton.PLAY_PAUSE)
        verify(controller).requestSiri()
    }

    @Test fun avrCpRegistersOnlyVoiceAndChangingAudioPreferenceCannotDoubleFire() {
        AirPlayPersistence.saveCarBluetoothAudio(context, true)
        val controller = attach()
        val vendor = VendorInput()
        context.connections.single().onServiceConnected(component, vendor)
        idle()
        assertArrayEquals(GeelyWheelKeyCodes.SIRI_ONLY, vendor.codes.single())
        val listener = vendor.registrations.single()
        listener.onShortClick(GeelyWheelKeyCodes.KEY_NEXT, 0)
        listener.onLongPressTriggered(GeelyWheelKeyCodes.KEY_VOICE_ASSIST, 0)
        idle()
        verify(controller, never()).sendMediaButton(anyInt())
        verify(controller).requestSiri()
        AirPlayPersistence.saveCarBluetoothAudio(context, false)
        GeelyWheelKeys.stop()
        GeelyWheelKeys.start(context, controller)
        idle()
        val mediaVendor = VendorInput()
        context.connections.last().onServiceConnected(component, mediaVendor)
        idle()
        assertArrayEquals(GeelyWheelKeyCodes.ALL, mediaVendor.codes.single())
        AirPlayPersistence.saveCarBluetoothAudio(context, true)
        mediaVendor.registrations.single().onShortClick(GeelyWheelKeyCodes.KEY_NEXT, 0)
        idle()
        verify(controller, never()).sendMediaButton(anyInt())
    }

    private fun owner(): CarPlayController = mock(CarPlayController::class.java).also(owners::add)
    private fun attach(): CarPlayController = owner().also {
        `when`(it.nowPlayingSnapshot()).thenReturn(CarPlayNowPlaying())
        `when`(it.activeAirPlaySessionToken()).thenReturn(Any())
        `when`(it.sendMediaButton(anyInt())).thenReturn(true)
        `when`(it.requestSiri()).thenReturn(true)
        CarPlayMediaKeys.attach(context, it)
        idle()
    }
    private fun idle() = shadowOf(Looper.getMainLooper()).idle()
    private fun advanceRetry() = shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(GeelyWheelKeys.RETRY_MILLIS))

    private class BindingContext(base: Context) : ContextWrapper(base) {
        val connections = mutableListOf<ServiceConnection>()
        val unbound = mutableListOf<ServiceConnection>()
        var bindResult = true
        var bindFailure: RuntimeException? = null
        var service: ServiceInfo? = usableService()
        private val pm = mock(PackageManager::class.java).also { manager ->
            `when`(manager.resolveService(any(Intent::class.java), anyInt())).thenAnswer {
                service?.let { info -> ResolveInfo().apply { serviceInfo = info } }
            }
        }
        override fun getApplicationContext(): Context = this
        override fun getPackageManager(): PackageManager = pm
        override fun checkSelfPermission(permission: String): Int = PackageManager.PERMISSION_DENIED
        override fun bindService(intent: Intent, connection: ServiceConnection, flags: Int): Boolean {
            assertEquals(ComponentName(GeelyWheelKeys.INPUT_PACKAGE, GeelyWheelKeys.INPUT_CLASS), intent.component)
            connections += connection
            bindFailure?.let { throw it }
            return bindResult
        }
        override fun unbindService(connection: ServiceConnection) { unbound += connection }
    }

    private class VendorInput : IInputManager.Stub() {
        val registrations = mutableListOf<IInputListener>()
        val unregistered = mutableListOf<IInputListener>()
        val codes = mutableListOf<IntArray>()
        var registrationFailure = false
        var registrationDenied = false
        var onRegister: () -> Unit = {}
        override fun interceptKeyCode(keyCode: Int, packageName: String?): Boolean = error("must not intercept")
        override fun releaseKeyCode(keyCode: Int, packageName: String?): Boolean = error("must not intercept")
        override fun registerListener(listener: IInputListener, packageName: String?, keyCodes: IntArray) {
            registrations += listener
            codes += keyCodes
            onRegister()
            if (registrationDenied) throw SecurityException("vendor registration denied")
            if (registrationFailure) throw RemoteException("transient registration failure")
        }
        override fun unregisterListener(listener: IInputListener, packageName: String?) { unregistered += listener }
        override fun getControlIndex(): Int = 0
    }

    companion object {
        private fun usableService() = ServiceInfo().apply {
            packageName = GeelyWheelKeys.INPUT_PACKAGE
            name = GeelyWheelKeys.INPUT_CLASS
            enabled = true
            exported = true
            applicationInfo = ApplicationInfo().apply { enabled = true }
        }
    }
}
