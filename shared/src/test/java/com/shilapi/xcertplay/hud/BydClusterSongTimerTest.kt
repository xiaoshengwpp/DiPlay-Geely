package com.shilapi.xcertplay.hud

import android.content.Context
import android.content.ContextWrapper
import android.content.SharedPreferences
import com.shilapi.xcertplay.iap2.message.Iap2Messages
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowLog

@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, sdk = [29])
class BydClusterSongTimerTest {
    private val type = BydClusterSong::class.java
    private fun field(name: String) = type.getDeclaredField(name).apply { isAccessible = true }
    private fun get(name: String) = field(name).get(BydClusterSong)
    private fun set(name: String, value: Any?) = field(name).set(BydClusterSong, value)
    private val rejectingContext = object : ContextWrapper(null) {
        override fun getApplicationContext(): Context = error("BYD context accessed")
        override fun getSharedPreferences(name: String, mode: Int): SharedPreferences = error("BYD timer preferences accessed")
    }
    private fun song(title: String = "Track") = Iap2Messages.buildRaw(ClusterSongState.NOW_PLAYING_UPDATE) {
        group(0) { string(1, title); string(12, "Artist") }
    }
    private fun playback(playing: Boolean) = Iap2Messages.buildRaw(ClusterSongState.NOW_PLAYING_UPDATE) {
        group(1) { u8(0, if (playing) 1 else 2) }
    }
    private fun assertNoWorker() {
        for (name in listOf("shell", "writer")) {
            assertFalse("$name initialized", (get(name + "\$delegate") as Lazy<*>).isInitialized())
        }
    }
    private fun assertNoTimersOrCards() {
        assertNull(BydClusterSong.current())
        for (name in listOf("context", "note", "onChange", "announced", "wanted", "shown")) assertNull(name, get(name))
        assertNoWorker()
    }

    @Test fun repeatedSettingAndSessionChangesNeverCreateATimer() {
        BydClusterSong.attach(rejectingContext)
        repeat(3) {
            BydClusterSong.settingChanged(true)
            BydClusterSong.onChangeSettingChanged()
            BydClusterSong.onFrame(song("Track $it"))
            BydClusterSong.onFrame(playback(true))
            BydClusterSong.note("Zoom")
            BydClusterSong.settingChanged(false)
            BydClusterSong.end()
        }
        assertNoTimersOrCards()
    }

    @Test fun directSongExpiryCannotTouchContextEvenWithAForgedMatchingToken() {
        val oldChange = get("onChange")
        val oldWanted = get("wanted")
        val token = Any()
        val card = ClusterSong("Synthetic stale track", true)
        try {
            set("onChange", token)
            set("wanted", card)
            type.getDeclaredMethod("endChange", Context::class.java, Any::class.java)
                .apply { isAccessible = true }.invoke(BydClusterSong, rejectingContext, token)
            assertSame(token, get("onChange"))
            assertSame(card, get("wanted"))
            assertNull(BydClusterSong.current())
            assertNoWorker()
        } finally {
            set("onChange", oldChange)
            set("wanted", oldWanted)
        }
    }

    @Test fun directSchedulingEntrypointsCannotCreateAWriter() {
        val card = ClusterSong("Synthetic track", true)
        for (name in listOf("showForChange", "show", "write")) {
            type.getDeclaredMethod(name, Context::class.java, ClusterSong::class.java)
                .apply { isAccessible = true }.invoke(BydClusterSong, rejectingContext, card)
        }
        for (name in listOf("stop", "clear")) {
            type.getDeclaredMethod(name, Context::class.java)
                .apply { isAccessible = true }.invoke(BydClusterSong, rejectingContext)
        }
        assertNoTimersOrCards()
    }

    @Test fun forgedSavedModeCannotScheduleTimersOrLogPrivateTrackText() {
        val app = RuntimeEnvironment.getApplication()
        val prefs = app.getSharedPreferences("diplay_byd_outputs", Context.MODE_PRIVATE)
        prefs.edit().putBoolean("cluster_song", true).putBoolean("cluster_song_on_change", true).commit()
        try {
            ShadowLog.clear()
            BydClusterSong.attach(app)
            BydClusterSong.onFrame(song("Private enabled title"))
            BydClusterSong.onChangeSettingChanged()
            prefs.edit().putBoolean("cluster_song", false).commit()
            BydClusterSong.onFrame(song("Private disabled lyric"))
            BydClusterSong.settingChanged(false)
            val messages = ShadowLog.getLogs().map { it.msg }
            assertFalse(messages.any { "Private enabled title" in it || "Private disabled lyric" in it })
            assertNoTimersOrCards()
        } finally {
            prefs.edit().clear().commit()
        }
    }

    @Test fun independentMetadataAndPlaybackParserStillWork() {
        val state = ClusterSongState()
        state.accept(song("First"))
        assertEquals("First", state.current()?.line)
        state.accept(playback(true))
        assertTrue(requireNotNull(state.current()).playing)
        state.accept(song("Second"))
        assertEquals("Second", state.current()?.line)
        state.accept(playback(false))
        assertFalse(requireNotNull(state.current()).playing)
        state.clear()
        assertNull(state.current())
        assertNoWorker()
    }
}
