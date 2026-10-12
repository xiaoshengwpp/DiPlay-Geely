package com.shilapi.xcertplay.hud

import android.content.Context
import com.shilapi.xcertplay.iap2.body.Iap2BodyReader
import com.shilapi.xcertplay.iap2.wire.Iap2Frame

/** Dashboard song; [line] preserves title-only HUD text, [source] selects a note's music icon. */
internal data class ClusterSong(val text: String, val playing: Boolean, val line: String = text, val source: Int? = null)

/**
 * The CarPlay song for the dashboard, from iAP2 NowPlayingUpdate (0x5001): title (1) and artist (12)
 * in MediaItemAttributes, playback status in PlaybackAttributes. Updates carry only what changed;
 * omitted fields retain their previous values, while an explicitly cleared title forgets the item.
 */
internal class ClusterSongState {
    private var title: String? = null
    private var artist: String? = null
    private var playing = false
    private var last: ClusterSong? = null

    /** Updates the cached card; null can mean unchanged or cleared, so consumers compare [current]. */
    fun accept(frame: Iap2Frame): ClusterSong? {
        if (frame.messageId != NOW_PLAYING_UPDATE) return null
        val body = runCatching { Iap2BodyReader.of(frame) }.getOrNull() ?: return null
        runCatching { body.optionalGroup(ITEM) }.getOrNull()?.let { item ->
            val nextTitle = runCatching { item.optionalString(TITLE) }.getOrNull()
            if (nextTitle != null) {
                title = nextTitle
                if (nextTitle.isBlank()) artist = null
            }
            if (nextTitle?.isBlank() != true) {
                runCatching { item.optionalString(ARTIST) }.getOrNull()?.let { artist = it }
            }
        }
        runCatching { body.optionalGroup(PLAYBACK)?.optionalU8(STATUS) }.getOrNull()?.let { status ->
            playing = status == STATUS_PLAYING || status == STATUS_SEEK_FORWARD || status == STATUS_SEEK_BACKWARD
        }
        val next = text(title, artist)?.let { ClusterSong(it, playing, title!!.trim()) }
        if (next == last) return null
        last = next
        return next
    }

    /** The card for the song known so far, if any. */
    fun current(): ClusterSong? = last

    /** The session ended: forget the song. */
    fun clear() {
        title = null
        artist = null
        playing = false
        last = null
    }

    companion object {
        const val NOW_PLAYING_UPDATE = 0x5001
        private const val ITEM = 0
        private const val TITLE = 1
        private const val ARTIST = 12
        private const val PLAYBACK = 1
        private const val STATUS = 0
        private const val STATUS_PLAYING = 1
        private const val STATUS_SEEK_FORWARD = 3
        private const val STATUS_SEEK_BACKWARD = 4

        /** The dashboard takes at most 255 bytes of UTF-16LE. */
        const val MAX_TEXT_BYTES = 255

        /** "Title — Artist", shortened to what the dashboard takes; null without a title. */
        fun text(title: String?, artist: String?): String? {
            val name = title?.trim()?.takeIf { it.isNotEmpty() } ?: return null
            val full = artist?.trim()?.takeIf { it.isNotEmpty() }?.let { "$name — $it" } ?: name
            var end = full.length
            while (full.substring(0, end).toByteArray(Charsets.UTF_16LE).size > MAX_TEXT_BYTES) {
                end--
                if (end > 0 && Character.isLowSurrogate(full[end])) end--
            }
            return full.substring(0, end)
        }
    }
}

/** No-op compatibility interface. This product has no live vehicle access implementation. */
internal object BydClusterSong {
    private val shell by lazy { Unit }
    private val writer by lazy { Unit }
    private var context: Context? = null
    private var note: Any? = null
    private var onChange: Any? = null
    private var announced: String? = null
    private var wanted: ClusterSong? = null
    private var shown: ClusterSong? = null
    fun attach(appContext: Context) = Unit
    fun onFrame(frame: Iap2Frame) = Unit
    fun onChangeSettingChanged() = Unit
    fun settingChanged(enabled: Boolean) = Unit
    fun end() = Unit
    fun note(text: String, source: Int? = null) = Unit
    fun current(): ClusterSong? = null
    private fun showForChange(app: Context, song: ClusterSong) = Unit
    private fun endChange(app: Context, token: Any) = Unit
    private fun endNote(app: Context, token: Any) = Unit
    private fun show(app: Context, song: ClusterSong) = Unit
    private fun stop(app: Context) = Unit
    private fun write(app: Context, song: ClusterSong) = Unit
    private fun clear(app: Context) = Unit
}

internal object ClusterSongWriteResult {
    // The PR reporter observed this result on a DiLink 4 Seal while the music card updated.
    // Accept it only for the instrument writes below; it is not a general shell success code.
    private const val OBSERVED_VENDOR_RESULT = -2147482648

    fun accepted(output: String, clearing: Boolean = false): Boolean {
        val expected = if (clearing) setOf("state") else setOf("source", "state", "text")
        val received = mutableSetOf<String>()
        for (line in output.lineSequence().map { it.trim() }.filter { '=' in it }) {
            val name = line.substringBefore('=').trim()
            val result = line.substringAfter('=').trim().toIntOrNull() ?: return false
            if (name !in expected || !received.add(name) ||
                (result != 0 && result != OBSERVED_VENDOR_RESULT)) return false
        }
        return received == expected
    }
}

/** No-op compatibility interface. This product has no live vehicle access implementation. */
object BydClusterSongTool {
    @JvmStatic fun main(args: Array<String>) = Unit
    private fun write(args: Array<String>) = Unit
}
