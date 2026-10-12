package com.shilapi.xcertplay

import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.content.SharedPreferences
import android.os.Looper
import com.shilapi.xcertplay.airplay.AirPlayConfig
import com.shilapi.xcertplay.airplay.AirPlayDisplayConfig
import com.shilapi.xcertplay.airplay.AirPlayIdentity
import com.shilapi.xcertplay.airplay.AirPlayMediaHandler
import com.shilapi.xcertplay.airplay.AirPlaySessionListener
import com.shilapi.xcertplay.airplay.CarPlayMediaButton
import com.shilapi.xcertplay.airplay.PairingStore
import com.shilapi.xcertplay.airplay.VideoInCar
import com.shilapi.xcertplay.hud.BydOutputSettings
import com.shilapi.xcertplay.orchestration.CarPlayController
import com.shilapi.xcertplay.orchestration.CarPlayRuntimeConfig
import com.shilapi.xcertplay.orchestration.MfiTarget
import com.shilapi.xcertplay.transport.Iap2IdentificationConfig
import org.junit.After
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [29], manifest = Config.NONE)
class GeelyVideoBoundaryTest {
    private val context get() = RuntimeEnvironment.getApplication()
    private val rejectingContext = object : ContextWrapper(null) {
        override fun getApplicationContext(): Context = error("video context accessed")
        override fun getSharedPreferences(name: String, mode: Int): SharedPreferences = error("video preferences read")
        override fun getSystemService(name: String): Any = error("video service accessed")
        override fun startActivity(intent: Intent) = error("video activity launched")
    }

    @After fun reset() {
        VideoInCar.allowed = false
        for ((name, value) in mapOf("url" to null, "playing" to false, "startMillis" to 0,
            "appContext" to null, "controller" to null, "streamId" to null, "itemUuid" to null)) {
            videoField(name).set(CarPlayVideo, value)
        }
        CarPlayVideo.activity = null
        CarPlayVideo.pendingSeekMillis = null
        context.getSharedPreferences("diplay_byd_outputs", Context.MODE_PRIVATE).edit().clear().commit()
    }

    @Test fun savedTrueSettingsCannotEnableWheelSpeedOrParkedVideo() {
        val prefs = context.getSharedPreferences("diplay_byd_outputs", Context.MODE_PRIVATE)
        prefs.edit().putBoolean("wheel_speed_to_iphone", true).putBoolean("video_while_parked", true).commit()
        assertFalse(BydOutputSettings.wheelSpeedToIphone(context))
        assertFalse(BydOutputSettings.videoWhileParked(context))
        BydOutputSettings.setWheelSpeedToIphone(context, true)
        BydOutputSettings.setVideoWhileParked(context, true)
        assertFalse(BydOutputSettings.wheelSpeedToIphone(context))
        assertFalse(BydOutputSettings.videoWhileParked(context))
        assertFalse(BydOutputSettings.wheelSpeedToIphone(rejectingContext))
        assertFalse(BydOutputSettings.videoWhileParked(rejectingContext))
    }

    @Test fun disabledVideoCallbacksDoNotQueueWorkOrReadPayloads() {
        videoField("appContext").set(CarPlayVideo, rejectingContext)
        val unreadableMessage = object : AbstractMap<String, Any?>() {
            override val entries: Set<Map.Entry<String, Any?>> get() = error("video payload inspected")
        }
        repeat(3) {
            assertNull(CarPlayVideo.readParked())
            CarPlayVideo.onVideoAllowedChanged(true)
            CarPlayVideo.onVideoAllowedChanged(false)
            CarPlayVideo.onVideoSessionEnded()
            CarPlayVideo.onVideoUiRequested()
            CarPlayVideo.onVideoMessage(1, unreadableMessage)
            CarPlayVideo.setPlaying(true)
            CarPlayVideo.onPlayerFailed(VideoInCar.ERROR_DECODER)
            CarPlayVideo.onPlayerClosed(5000)
        }
        // Also exercise direct helpers, so safety cannot depend on callback routing alone.
        invokeVideo("handle", 1L, unreadableMessage)
        invokeVideo("reply", 1L, unreadableMessage)
        invokeVideo("show")
        invokeVideo("closePlayer", "disabled")
        invokeVideo("stop")
        assertNull(invokeVideo("playerState"))
        shadowOf(Looper.getMainLooper()).idle()
        assertFalse(CarPlayVideo.playing)
        assertNull(CarPlayVideo.url)
        assertNull(CarPlayVideo.pendingSeekMillis)
        for (name in listOf("main", "sender")) {
            assertFalse("video $name initialized", (videoField(name + "\$delegate").get(CarPlayVideo) as Lazy<*>).isInitialized())
        }
    }

    @Test fun forcedAllowedAndDirectPlayerMethodsCannotPlayOrSeek() {
        // Neither protocol state nor a stale local queue is a verified parked signal for Geely.
        VideoInCar.allowed = true
        val player = CarPlayVideoActivity()
        CarPlayVideo.activity = player
        videoField("url").set(CarPlayVideo, "https://example.invalid/never-load.mp4")
        videoField("playing").setBoolean(CarPlayVideo, true)
        CarPlayVideo.pendingSeekMillis = 8000
        for (key in listOf(CarPlayMediaButton.PLAY, CarPlayMediaButton.PAUSE, CarPlayMediaButton.NEXT,
            CarPlayMediaButton.PREVIOUS, CarPlayMediaButton.PLAY_PAUSE)) {
            assertFalse("video must not consume projection media key $key", CarPlayVideo.onMediaKey(key))
        }
        player.load()
        player.applyRate()
        player.skip(10000)
        player.applySeek()
        assertNull(player.state())
        assertNull(activityField(player, "player"))
        assertEquals(8000, CarPlayVideo.pendingSeekMillis)
    }

    @Test fun directActivityEntryFinishesBeforeCreatingPlayerAndDestroysSafely() {
        VideoInCar.allowed = true
        val lifecycle = Robolectric.buildActivity(CarPlayVideoActivity::class.java).create()
        val player = lifecycle.get()
        assertTrue(player.isFinishing)
        assertNull(activityField(player, "player"))
        assertNull(activityField(player, "controls"))
        assertNull(CarPlayVideo.activity)
        lifecycle.start().resume().pause().stop().destroy()
        assertNull(activityField(player, "player"))
    }

    @Test fun controllerAndAttachCannotEnableVideoButPreserveProjectionConfiguration() {
        val offered = AirPlayConfig("test", "00:00:00:00:00:00", "00:00:00:00:00:00", "test",
            AirPlayDisplayConfig(800, 480), videoInCar = true)
        // Empty inert identity data: this test never starts authentication or a transport.
        val controller = CarPlayController(
            context,
            CarPlayRuntimeConfig(mfiTarget = MfiTarget.LOCAL,
                identification = Iap2IdentificationConfig("test", "test", "test", "test", "test", "test", 0)),
            offered, AirPlayIdentity(byteArrayOf(), byteArrayOf(), "disabled-video-test"), PairingStore(),
            object : AirPlaySessionListener {}, object : AirPlayMediaHandler {}, {},
        )
        try {
            CarPlayVideo.attach(rejectingContext, controller)
            assertNull(videoField("appContext").get(CarPlayVideo))
            assertNull(videoField("controller").get(CarPlayVideo))
            controller.videoListener = CarPlayVideo
            assertNull(controller.videoListener)
            assertFalse(controller.sendVideoMessage(1, emptyMap()))
            val actual = CarPlayController::class.java.getDeclaredField("airPlayConfig")
                .apply { isAccessible = true }.get(controller) as AirPlayConfig
            assertEquals(offered.copy(videoInCar = false), actual)
            assertTrue("caller-owned configuration must remain immutable", offered.videoInCar)
        } finally {
            controller.close()
        }
    }

    private fun videoField(name: String) = CarPlayVideo::class.java.getDeclaredField(name).apply { isAccessible = true }
    private fun activityField(activity: CarPlayVideoActivity, name: String) =
        CarPlayVideoActivity::class.java.getDeclaredField(name).apply { isAccessible = true }.get(activity)
    private fun invokeVideo(name: String, vararg args: Any?): Any? = CarPlayVideo::class.java.declaredMethods
        .single { it.name == name }.apply { isAccessible = true }.invoke(CarPlayVideo, *args)
}
