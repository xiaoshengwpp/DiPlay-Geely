package com.shilapi.xcertplay

import android.content.BroadcastReceiver
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.content.IntentFilter
import android.content.ServiceConnection
import android.os.Looper
import com.shilapi.xcertplay.airplay.*
import com.shilapi.xcertplay.hud.BydNavigationOutputs
import com.shilapi.xcertplay.orchestration.*
import com.shilapi.xcertplay.transport.Iap2IdentificationConfig
import com.shilapi.xcertplay.transport.UsbDeviceId
import com.shilapi.xcertplay.glance.CarPlayGlance
import com.shilapi.xcertplay.iap2.message.Iap2Messages
import com.shilapi.xcertplay.iap2.wire.Iap2Frame
import org.mockito.Mockito.mock
import org.robolectric.util.ReflectionHelpers
import org.junit.After
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.LooperMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], manifest = Config.NONE)
@LooperMode(LooperMode.Mode.PAUSED)
class CarPlayControllerUsbServiceTest {
    private val controllers = mutableListOf<CarPlayController>()
    private val statuses = mutableListOf<CarPlayStatus>()

    @After fun tearDown() {
        controllers.forEach { it.close(); assertTrue(it.awaitClosed(2_000)) }
        BydNavigationOutputs.endNow()
    }

    @Test fun neutralCallInputFollowsControllerSessionAndRejectsRetiredChannels() {
        val controller = controller(UsbContext(false), CarPlayTransport.WIRELESS, MfiTarget.LOCAL)
        val listener = ReflectionHelpers.getField<AirPlaySessionListener>(controller, "sessionListener")
        val route = controller.javaClass.getDeclaredMethod("onRouteFrame", Iap2Frame::class.java,
            kotlin.jvm.functions.Function0::class.java).apply { isAccessible = true }
        val ringing = Iap2Messages.buildRaw(0x4155) { u8(2, 2); string(4, "call") }
        fun receive(currentChannel: Boolean = true) {
            route.invoke(controller, ringing, { currentChannel })
        }
        val first = mock(AirPlaySession::class.java)
        val replacement = mock(AirPlaySession::class.java)
        receive()
        assertFalse(CarPlayCallGuard.inCall())
        listener.onSessionActive(first)
        receive(false)
        assertFalse(CarPlayCallGuard.inCall())
        receive()
        assertTrue(CarPlayCallGuard.inCall())
        listener.onSessionActive(replacement)
        assertFalse(CarPlayCallGuard.inCall())
        receive(false)
        assertFalse(CarPlayCallGuard.inCall())
        receive()
        listener.onSessionEnded(first)
        assertTrue(CarPlayCallGuard.inCall())
        listener.onSessionEnded(replacement)
        receive()
        assertFalse(CarPlayCallGuard.inCall())
        listener.onSessionActive(replacement)
        receive()
        assertTrue(CarPlayCallGuard.inCall())
        controller.close()
        receive()
        listener.onSessionActive(first)
        assertFalse(CarPlayCallGuard.inCall())
    }

    @Test fun replacingLiveSessionClearsGlanceAndExpiresConsentButRepeatedActiveDoesNot() {
        val controller = controller(UsbContext(false), CarPlayTransport.WIRELESS, MfiTarget.LOCAL)
        val listener = ReflectionHelpers.getField<AirPlaySessionListener>(controller, "sessionListener")
        val first = mock(AirPlaySession::class.java)
        listener.onSessionActive(first)
        CarPlayGlance.onFrame(Iap2Messages.buildRaw(0x5001) { group(0) { string(1, "Old phone") } })
        CarPlayGlance.onFrame(Iap2Messages.buildRaw(0x5202) { u16(1, 7); u8(3, 2); string(4, "Old road") })
        CarPlayGlance.onFrame(Iap2Messages.buildRaw(0x5201) { u8(1, 1); u16List(0x0d, listOf(7)) })
        val initial = CarPlayGlance.snapshot()
        assertEquals("Old phone", initial.song)
        assertEquals("Old road", initial.road)
        listener.onSessionActive(first)
        assertEquals(initial, CarPlayGlance.snapshot())
        listener.onSessionActive(mock(AirPlaySession::class.java))
        val next = CarPlayGlance.snapshot()
        assertTrue(next.connected)
        assertEquals(initial.disconnectEpoch + 1, next.disconnectEpoch)
        assertNull(next.song)
        assertNull(next.maneuverType)
        assertEquals("", next.road)
    }

    @Test fun wirelessLocalCanBeCreatedAndReachMfiWithoutAUsbService() {
        val context = UsbContext(false)
        val controller = controller(context, CarPlayTransport.WIRELESS, MfiTarget.LOCAL)
        controller.start()
        shadowOf(Looper.getMainLooper()).idle()
        assertTrue(statuses.contains(CarPlayStatus.DiscoveringMfi))
        assertFalse(statuses.filterIsInstance<CarPlayStatus.Failed>().any { it.message.contains("USB service") })
        assertEquals(0, context.receivers.size)
    }

    @Test fun wiredStartReportsMissingUsbServiceWithoutRegisteringReceivers() {
        missingServiceFailsNormally(CarPlayTransport.WIRED, MfiTarget.LOCAL)
    }

    @Test fun wirelessCh341ReportsMissingUsbServiceWithoutRegisteringReceivers() {
        missingServiceFailsNormally(CarPlayTransport.WIRELESS, MfiTarget.USB_CH341)
    }

    @Test fun reconnectMfiAlsoReportsMissingUsbServiceNormally() {
        val context = UsbContext(false)
        val controller = controller(context, CarPlayTransport.WIRED, MfiTarget.LOCAL)
        controller.reconnectMfi()
        shadowOf(Looper.getMainLooper()).idle()
        assertTrue(statuses.single() is CarPlayStatus.Failed)
        assertTrue((statuses.single() as CarPlayStatus.Failed).message.contains("USB service"))
        assertEquals(0, context.receivers.size)
    }

    @Test fun availableWiredUsbStillRegistersAndClosesBothIphoneReceivers() {
        val context = UsbContext(true)
        val controller = controller(context, CarPlayTransport.WIRED, MfiTarget.LOCAL)
        controller.start()
        assertEquals(2, context.receivers.size)
        assertTrue(context.receivers.values.any { it.hasAction("${context.packageName}.IPHONE_USB_PERMISSION") })
        assertTrue(context.receivers.values.any { it.hasAction("android.hardware.usb.action.USB_DEVICE_ATTACHED") })
        controller.close()
        assertTrue(controller.awaitClosed(2_000))
        assertEquals(0, context.receivers.size)
        assertEquals(2, context.unregistered)
    }

    @Test fun availableWirelessCh341StillWaitsForMfiAndClosesItsPermissionReceiver() {
        val context = UsbContext(true)
        val controller = controller(context, CarPlayTransport.WIRELESS, MfiTarget.USB_CH341)
        controller.start()
        shadowOf(Looper.getMainLooper()).idle()
        assertTrue(statuses.contains(CarPlayStatus.WaitingForMfi))
        assertEquals(1, context.receivers.size)
        assertFalse(context.receivers.values.any { it.hasAction("android.hardware.usb.action.USB_DEVICE_ATTACHED") })
        controller.close()
        assertTrue(controller.awaitClosed(2_000))
        assertEquals(0, context.receivers.size)
        assertEquals(1, context.unregistered)
    }

    private fun missingServiceFailsNormally(transport: CarPlayTransport, target: MfiTarget) {
        val context = UsbContext(false)
        val controller = controller(context, transport, target)
        controller.start()
        shadowOf(Looper.getMainLooper()).idle()
        val failure = statuses.single() as CarPlayStatus.Failed
        assertTrue(failure.message.contains("USB service"))
        assertEquals(0, context.receivers.size)
    }

    private fun controller(context: Context, transport: CarPlayTransport, target: MfiTarget): CarPlayController =
        CarPlayController(context, CarPlayRuntimeConfig(
            mfiTarget = target, transport = transport,
            ch341Devices = if (target == MfiTarget.USB_CH341) listOf(UsbDeviceId(0x1a86, 0x7523)) else emptyList(),
            identification = Iap2IdentificationConfig(name = "test", modelIdentifier = "test", manufacturer = "test",
                serialNumber = "test", firmwareVersion = "1", hardwareVersion = "1", carPlayUsbInterfaceNumber = 3),
        ), AirPlayConfig("test", "test", "", "1", AirPlayDisplayConfig(800, 480)),
            AirPlayIdentity(ByteArray(32), ByteArray(32), "test"), PairingStore(),
            object : AirPlaySessionListener {}, object : AirPlayMediaHandler {}, statuses::add).also(controllers::add)

    private class UsbContext(private val hasUsb: Boolean) : ContextWrapper(RuntimeEnvironment.getApplication()) {
        val receivers = mutableMapOf<BroadcastReceiver, IntentFilter>()
        var unregistered = 0
        override fun getApplicationContext(): Context = this
        override fun bindService(service: Intent, conn: ServiceConnection, flags: Int): Boolean = false
        override fun getSystemService(name: String): Any? =
            if (name == USB_SERVICE && !hasUsb) null else super.getSystemService(name)
        override fun registerReceiver(receiver: BroadcastReceiver?, filter: IntentFilter): Intent? {
            if (receiver != null) receivers[receiver] = filter
            return super.registerReceiver(receiver, filter)
        }
        override fun unregisterReceiver(receiver: BroadcastReceiver) {
            if (receivers.remove(receiver) != null) unregistered++
            super.unregisterReceiver(receiver)
        }
    }
}
