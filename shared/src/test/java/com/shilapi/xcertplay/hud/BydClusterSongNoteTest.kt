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

@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, sdk = [29])
class BydClusterSongNoteTest {
    private val type = BydClusterSong::class.java
    private fun field(name: String) = type.getDeclaredField(name).apply { isAccessible = true }
    private fun get(name: String) = field(name).get(BydClusterSong)
    private fun set(name: String, value: Any?) = field(name).set(BydClusterSong, value)
    private val rejectingContext = object : ContextWrapper(null) {
        override fun getApplicationContext(): Context = error("BYD context accessed")
        override fun getSharedPreferences(name: String, mode: Int): SharedPreferences = error("BYD song preferences accessed")
    }
    private fun song(title: String) = Iap2Messages.buildRaw(ClusterSongState.NOW_PLAYING_UPDATE) {
        group(0) { string(1, title); string(12, "Artist") }
    }
    private fun assertNoWorker() {
        for (name in listOf("shell", "writer")) {
            assertFalse("$name initialized", (get(name + "\$delegate") as Lazy<*>).isInitialized())
        }
    }
    private fun assertNoCard() {
        assertNull(BydClusterSong.current())
        for (name in listOf("context", "note", "wanted", "shown")) assertNull(name, get(name))
        assertNoWorker()
    }

    @Test fun notesNeverAttachVendorContextOrCreateAWriter() {
        assertFalse(GeelyVendorPolicy.bydClusterSong)
        BydClusterSong.attach(rejectingContext)
        BydClusterSong.note("Zoom", source = 7)
        BydClusterSong.note("Second note")
        assertNoCard()
    }

    @Test fun metadataAndSessionChangesCannotActivateWheelNotes() {
        repeat(3) {
            BydClusterSong.onFrame(song("Track $it"))
            BydClusterSong.note("Zoom", source = 7)
            BydClusterSong.settingChanged(true)
            BydClusterSong.end()
            BydClusterSong.settingChanged(false)
        }
        assertNoCard()
    }

    @Test fun directOldNoteExpiryIsInertBeforePreferencesOrHardware() {
        val oldNote = get("note")
        val oldWanted = get("wanted")
        val token = Any()
        val card = ClusterSong("Synthetic stale note", true, source = 7)
        try {
            set("note", token)
            set("wanted", card)
            type.getDeclaredMethod("endNote", Context::class.java, Any::class.java)
                .apply { isAccessible = true }.invoke(BydClusterSong, rejectingContext, token)
            assertSame(token, get("note"))
            assertSame(card, get("wanted"))
            assertNull(BydClusterSong.current())
            assertNoWorker()
        } finally {
            set("note", oldNote)
            set("wanted", oldWanted)
        }
    }

    @Test fun forgedEnabledPreferencesCannotDisplayOrScheduleANote() {
        val app = RuntimeEnvironment.getApplication()
        val prefs = app.getSharedPreferences("diplay_byd_outputs", Context.MODE_PRIVATE)
        prefs.edit().putBoolean("cluster_song", true).putBoolean("hud_song", true).commit()
        try {
            BydClusterSong.attach(app)
            BydClusterSong.onFrame(song("Track"))
            BydClusterSong.note("Zoom", source = 7)
            BydClusterSong.settingChanged(true)
            assertNoCard()
        } finally {
            prefs.edit().clear().commit()
        }
    }

    @Test fun pureNoteValuesRemainAvailableWithoutAnyHardware() {
        val note = ClusterSong("Zoom", true, source = 7)
        assertEquals("Zoom", note.text)
        assertEquals("Zoom", note.line)
        assertEquals(7, note.source)
        assertTrue(note.playing)
        assertNoWorker()
    }
}
