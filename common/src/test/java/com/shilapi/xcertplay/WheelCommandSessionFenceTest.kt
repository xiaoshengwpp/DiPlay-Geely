package com.shilapi.xcertplay

import android.media.AudioManager
import com.shilapi.xcertplay.airplay.*
import com.shilapi.xcertplay.orchestration.CarPlayController
import com.shilapi.xcertplay.orchestration.CarPlayRuntimeConfig
import com.shilapi.xcertplay.orchestration.MfiTarget
import com.shilapi.xcertplay.transport.Iap2IdentificationConfig
import java.net.Socket
import java.util.concurrent.AbstractExecutorService
import java.util.concurrent.ExecutorService
import java.util.concurrent.RejectedExecutionException
import java.util.concurrent.TimeUnit
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mockito.*
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.LooperMode

/** Both queue boundaries use real controller/session ownership, with execution explicitly paused. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [25, 30, 33], manifest = Config.NONE)
@LooperMode(LooperMode.Mode.PAUSED)
class WheelCommandSessionFenceTest {
    private lateinit var controller: CarPlayController
    private lateinit var queue: PausedExecutor
    private val phones = mutableListOf<AirPlaySession>()
    private val context get() = RuntimeEnvironment.getApplication()
    private val config get() = AirPlayConfig(deviceName = "test", deviceId = "02:00:00:00:00:02",
        btMac = "02:00:00:00:00:01", sourceVersion = "1",
        main = AirPlayDisplayConfig(widthPixels = 800, heightPixels = 480))

    @Before fun setUp() {
        AirPlayPersistence.saveCarBluetoothAudio(context, false)
        GeelyInputSettings.setManualEnabled(context, false)
        context.getSystemService(AudioManager::class.java).mode = AudioManager.MODE_NORMAL
        controller = CarPlayController(context,
            CarPlayRuntimeConfig(mfiTarget = MfiTarget.LOCAL, identification = Iap2IdentificationConfig(
                name = "test", modelIdentifier = "test", manufacturer = "test", serialNumber = "test",
                firmwareVersion = "1", hardwareVersion = "1", carPlayUsbInterfaceNumber = 3)),
            config, AirPlayIdentity.generate(), PairingStore(), object : AirPlaySessionListener {},
            object : AirPlayMediaHandler {}, {})
        val field = controller.javaClass.getDeclaredField("touchExecutor").apply { isAccessible = true }
        (field.get(controller) as ExecutorService).shutdownNow()
        queue = PausedExecutor().also { field.set(controller, it) }
    }

    @After fun tearDown() {
        CarPlayMediaKeys.detach(controller)
        controller.close()
        controller.awaitClosed(1000)
        phones.forEach { it.close() }
        AirPlayPersistence.saveCarBluetoothAudio(context, false)
        context.getSystemService(AudioManager::class.java).mode = AudioManager.MODE_NORMAL
    }

    private fun newPhone(): AirPlaySession = spy(AirPlaySession(Socket(), config,
        AirPlayIdentity.generate(), PairingStore(), null, object : AirPlaySessionListener {},
        object : AirPlayMediaHandler {
            override fun onScreen(session: AirPlaySession, type: Int, stream: Map<String, Any?>): Int = 1
        })).also { phones += it; setupPrimary(it) }

    private fun setupPrimary(phone: AirPlaySession) {
        phone.javaClass.getDeclaredMethod("handleStreams", List::class.java).apply { isAccessible = true }
            .invoke(phone, listOf(mapOf("type" to 110L)))
    }

    private fun attach(phone: AirPlaySession) {
        controller.javaClass.getDeclaredField("activeSession").apply { isAccessible = true }.set(controller, phone)
    }

    private fun enqueuePair(token: Any) {
        assertTrue(controller.sendMediaButton(CarPlayMediaButton.NEXT, token))
        assertTrue(controller.requestSiri(token))
    }

    private fun assertUntouched(phone: AirPlaySession) {
        verify(phone, never()).sendMedia(anyInt())
        verify(phone, never()).invokeSiri()
    }

    @Test fun queuedCommandsCannotTransferToAReplacementPrimaryStream() {
        val phone = newPhone()
        attach(phone)
        val old = controller.activeAirPlaySessionToken()!!
        enqueuePair(old)
        setupPrimary(phone)
        assertNotSame(old, controller.activeAirPlaySessionToken())
        queue.runPending()
        assertUntouched(phone)
        assertFalse(controller.sendMediaButton(CarPlayMediaButton.NEXT, old))
        assertFalse(controller.requestSiri(old))
        enqueuePair(controller.activeAirPlaySessionToken()!!)
        queue.runPending()
        verify(phone).sendMedia(CarPlayMediaButton.NEXT)
        verify(phone).invokeSiri()
    }

    @Test fun queuedCommandsCannotReachEitherPhoneAfterWholeSessionReplacement() {
        val first = newPhone()
        val second = newPhone()
        attach(first)
        val old = controller.activeAirPlaySessionToken()!!
        enqueuePair(old)
        attach(second)
        queue.runPending()
        assertUntouched(first)
        assertUntouched(second)
        assertFalse(controller.requestSiri(old))
        assertFalse(controller.sendMediaButton(CarPlayMediaButton.NEXT, old))
        enqueuePair(controller.activeAirPlaySessionToken()!!)
        queue.runPending()
        verify(second).sendMedia(CarPlayMediaButton.NEXT)
        verify(second).invokeSiri()
    }

    @Test fun legacyOverloadsAlsoDropCommandsAfterPrimaryReplacement() {
        val phone = newPhone()
        attach(phone)
        assertTrue(controller.sendMediaButton(CarPlayMediaButton.NEXT))
        assertTrue(controller.requestSiri())
        setupPrimary(phone)
        queue.runPending()
        assertUntouched(phone)
    }

    @Test fun controllerCloseRejectsAlreadyDequeuedCommands() {
        val phone = newPhone()
        attach(phone)
        enqueuePair(controller.activeAirPlaySessionToken()!!)
        val pending = queue.takePending()
        controller.close()
        pending.forEach { it.run() }
        assertUntouched(phone)
    }

    @Test fun aStoppedBridgeDropsQueuedPrivateMediaAndSiriWithoutChangingThePhone() {
        val phone = newPhone()
        attach(phone)
        CarPlayMediaKeys.attach(context, controller)
        var currentBridge = true
        assertTrue(CarPlayMediaKeys.onExternalMediaKey(CarPlayMediaButton.NEXT, controller) { currentBridge })
        assertTrue(CarPlayMediaKeys.onExternalSiriKey(controller) { currentBridge })
        currentBridge = false
        queue.runPending()
        assertUntouched(phone)
    }

    @Test fun aCallStartingWhileQueuedDropsPrivateSiriAndForegroundMedia() {
        val phone = newPhone()
        attach(phone)
        CarPlayMediaKeys.attach(context, controller)
        assertTrue(CarPlayMediaKeys.onForegroundMediaKey(CarPlayMediaButton.NEXT, controller))
        assertTrue(CarPlayMediaKeys.onExternalSiriKey(controller) { true })
        context.getSystemService(AudioManager::class.java).mode = AudioManager.MODE_IN_COMMUNICATION
        queue.runPending()
        assertUntouched(phone)
    }

    @Test fun switchingToAvrcpWhileQueuedDropsMediaButPreservesSiri() {
        val phone = newPhone()
        attach(phone)
        CarPlayMediaKeys.attach(context, controller)
        assertTrue(CarPlayMediaKeys.onExternalMediaKey(CarPlayMediaButton.NEXT, controller) { true })
        assertTrue(CarPlayMediaKeys.onExternalSiriKey(controller) { true })
        AirPlayPersistence.saveCarBluetoothAudio(context, true)
        queue.runPending()
        verify(phone, never()).sendMedia(anyInt())
        verify(phone).invokeSiri()
    }

    @Test fun detachedMediaOwnerDropsQueuedCommandsEvenIfControllerRemainsOpen() {
        val phone = newPhone()
        attach(phone)
        CarPlayMediaKeys.attach(context, controller)
        assertTrue(CarPlayMediaKeys.onForegroundMediaKey(CarPlayMediaButton.NEXT, controller))
        assertTrue(CarPlayMediaKeys.onExternalSiriKey(controller) { true })
        CarPlayMediaKeys.detach(controller)
        queue.runPending()
        assertUntouched(phone)
        assertFalse(controller.isClosed())
    }

    private class PausedExecutor : AbstractExecutorService() {
        private val tasks = mutableListOf<Runnable>()
        private var stopped = false
        override fun execute(command: Runnable) {
            if (stopped) throw RejectedExecutionException()
            tasks += command
        }
        fun takePending(): List<Runnable> = tasks.toList().also { tasks.clear() }
        fun runPending() = takePending().forEach { it.run() }
        override fun shutdown() { stopped = true }
        override fun shutdownNow(): MutableList<Runnable> { stopped = true; return takePending().toMutableList() }
        override fun isShutdown(): Boolean = stopped
        override fun isTerminated(): Boolean = stopped && tasks.isEmpty()
        override fun awaitTermination(timeout: Long, unit: TimeUnit): Boolean = isTerminated
    }
}
