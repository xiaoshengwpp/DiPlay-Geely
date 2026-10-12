package com.shilapi.xcertplay

import android.content.Context
import android.content.Intent
import android.os.Binder
import android.os.Bundle
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.Message
import android.os.Messenger
import android.view.Display
import android.view.SurfaceControlViewHost
import android.view.View
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.Implementation
import org.robolectric.annotation.Implements
import org.robolectric.annotation.LooperMode

/** Isolate service consent/lifecycle handling from native SurfaceControl rendering. */
@Implements(SurfaceControlViewHost::class)
class MapEmbedSurfaceControlHostShadow {
    @Implementation fun __constructor__(context: Context, display: Display, hostToken: IBinder) = Unit
    @Implementation fun setView(view: View, width: Int, height: Int) { lastView = view }
    companion object { var lastView: View? = null; var releases = 0 }
    @Implementation fun relayout(width: Int, height: Int) = Unit
    @Implementation fun getSurfacePackage(): SurfaceControlViewHost.SurfacePackage? = null
    @Implementation fun release() { releases++ }
}

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], shadows = [MapEmbedSurfaceControlHostShadow::class])
@LooperMode(LooperMode.Mode.PAUSED)
class MapEmbedServiceTest {
    @Test fun forgedSharingCannotExposeABinderOrAttachMapsThroughStaleIpc() {
        MapEmbedSurfaceControlHostShadow.releases = 0
        MapEmbedSurfaceControlHostShadow.lastView = null
        MapMirrors.streamAspect = MapMirrors.PHYSICAL_STREAM_ASPECT
        val controller = Robolectric.buildService(MapEmbedService::class.java).create()
        val service = controller.get()
        try {
            val prefs = service.getSharedPreferences("xcertplay_airplay", Context.MODE_PRIVATE)
            prefs.edit().putBoolean("launcher_map_sharing", true)
                .putBoolean("cluster_map_enabled", true).commit()
            assertFalse(AirPlayPersistence.loadLauncherMapSharing(service))
            assertNull(service.onBind(Intent(MapEmbedService.ACTION)))
            val replies = mutableListOf<Message>()
            val client = Messenger(Handler(Looper.getMainLooper()) { replies += Message.obtain(it); true })
            val data = Bundle().apply {
                putBinder(MapEmbedService.KEY_HOST_TOKEN, Binder())
                putInt(MapEmbedService.KEY_DISPLAY_ID, Display.DEFAULT_DISPLAY)
                putInt(MapEmbedService.KEY_WIDTH, 1200)
                putInt(MapEmbedService.KEY_HEIGHT, 450)
            }
            // Defense in depth: stale/internal dispatch still cannot allocate an embed.
            val handle = service.javaClass.getDeclaredMethod("handle", Message::class.java)
                .apply { isAccessible = true }
            for (operation in listOf(MapEmbedService.MSG_ATTACH, MapEmbedService.MSG_RESIZE,
                MapEmbedService.MSG_DETACH, MapEmbedService.MSG_ATTACH)) {
                handle.invoke(service, Message.obtain(null, operation).apply {
                    replyTo = client
                    this.data = data
                })
            }
            service.javaClass.getDeclaredMethod("attach", Messenger::class.java, String::class.java, Bundle::class.java)
                .apply { isAccessible = true }.invoke(service, client, "test launcher", data)
            shadowOf(Looper.getMainLooper()).idle()
            val embeds = service.javaClass.getDeclaredField("embeds").apply { isAccessible = true }
                .get(service) as Map<*, *>
            assertTrue(embeds.isEmpty())
            assertNull(MapEmbedSurfaceControlHostShadow.lastView)
            assertEquals(0, MapEmbedSurfaceControlHostShadow.releases)
            assertFalse(MapMirrors.launcherShowsMap)
            assertTrue(replies.isEmpty())
            assertNull(service.javaClass.getDeclaredField("stopObservingSharing")
                .apply { isAccessible = true }.get(service))
            assertEquals(MapMirrors.PHYSICAL_STREAM_ASPECT, MapMirrors.streamAspect, 0.0001)
            for (enabled in listOf(false, true)) {
                prefs.edit().putBoolean("launcher_map_sharing", enabled).commit()
                shadowOf(Looper.getMainLooper()).idle()
                assertNull(service.onBind(Intent(MapEmbedService.ACTION)))
                assertTrue(embeds.isEmpty())
                assertFalse(MapMirrors.launcherShowsMap)
            }
        } finally {
            controller.destroy()
            MapMirrors.streamAspect = MapMirrors.PHYSICAL_STREAM_ASPECT
        }
        assertEquals(0, MapEmbedSurfaceControlHostShadow.releases)
    }

    @Test fun cropGeometryStillFollowsStreamAspectWithoutCreatingAnIpcEmbed() {
        val context = org.robolectric.RuntimeEnvironment.getApplication()
        val video = android.view.TextureView(context)
        try {
            MapMirrors.streamAspect = MapMirrors.PHYSICAL_STREAM_ASPECT
            MapEmbedService.cropToFill(video, 1200, 450)
            val matrix = FloatArray(9)
            video.getTransform(android.graphics.Matrix()).getValues(matrix)
            assertEquals(1f, matrix[android.graphics.Matrix.MSCALE_X], 0.001f)
            assertEquals(1f, matrix[android.graphics.Matrix.MSCALE_Y], 0.001f)
            MapMirrors.streamAspect = MapMirrors.VIRTUAL_STREAM_ASPECT
            MapEmbedService.cropToFill(video, 1200, 450)
            video.getTransform(android.graphics.Matrix()).getValues(matrix)
            assertEquals(1f, matrix[android.graphics.Matrix.MSCALE_X], 0.001f)
            assertEquals(1.5f, matrix[android.graphics.Matrix.MSCALE_Y], 0.001f)
            val expected = matrix.copyOf()
            MapEmbedService.cropToFill(video, 0, 450)
            MapEmbedService.cropToFill(video, 1200, 0)
            video.getTransform(android.graphics.Matrix()).getValues(matrix)
            assertArrayEquals(expected, matrix, 0.001f)
        } finally {
            MapMirrors.streamAspect = MapMirrors.PHYSICAL_STREAM_ASPECT
        }
    }
}
