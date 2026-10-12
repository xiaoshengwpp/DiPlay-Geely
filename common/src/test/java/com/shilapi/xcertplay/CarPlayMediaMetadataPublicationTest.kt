package com.shilapi.xcertplay

import android.graphics.Bitmap
import android.media.MediaMetadata
import android.media.session.MediaSession
import android.media.session.PlaybackState
import android.os.Looper
import com.shilapi.xcertplay.media.CarPlayNowPlaying
import com.shilapi.xcertplay.orchestration.CarPlayController
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.ArgumentCaptor
import org.mockito.Mockito.any
import org.mockito.Mockito.clearInvocations
import org.mockito.Mockito.mock
import org.mockito.Mockito.never
import org.mockito.Mockito.times
import org.mockito.Mockito.verify
import org.mockito.Mockito.verifyNoInteractions
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.LooperMode

/** Exercise the real queued update path, mocking only the controller and Android publication boundary. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [29], manifest = Config.NONE)
@LooperMode(LooperMode.Mode.PAUSED)
class CarPlayMediaMetadataPublicationTest {
    private lateinit var controller: CarPlayController
    private lateinit var session: MediaSession
    private val bitmaps = mutableListOf<Bitmap>()
    private val song = CarPlayNowPlaying(
        title = "Song", artist = "Artist", durationMillis = 180_000,
        artworkTransferId = 7, elapsedMillis = 1_000, playing = true,
    )

    @Before fun setUp() {
        controller = mock(CarPlayController::class.java)
        session = mock(MediaSession::class.java)
        CarPlayMediaKeys.attach(RuntimeEnvironment.getApplication(), controller)
        setField("session", session)
    }

    @After fun tearDown() {
        drainMainThread()
        CarPlayMediaKeys.detach(getField("controller") as? CarPlayController)
        bitmaps.forEach { if (!it.isRecycled) it.recycle() }
    }

    @Test fun elapsedAndPlayOnlyUpdatesSkipMetadataButStillPublishEveryPlaybackState() {
        publish(song)
        verify(session).setMetadata(any(MediaMetadata::class.java))
        clearInvocations(session)

        val updates = listOf(
            song.copy(elapsedMillis = 1_450),
            song.copy(elapsedMillis = 1_450, playing = false),
            song.copy(elapsedMillis = 1_450, playing = true),
            song.copy(elapsedMillis = 2_000),
        )
        updates.forEach { queueUpdate(controller, it) }
        drainMainThread()

        verify(session, never()).setMetadata(any(MediaMetadata::class.java))
        val states = ArgumentCaptor.forClass(PlaybackState::class.java)
        verify(session, times(updates.size)).setPlaybackState(states.capture())
        assertEquals(updates.map { it.elapsedMillis }, states.allValues.map { it.position })
        assertEquals(
            listOf(PlaybackState.STATE_PLAYING, PlaybackState.STATE_PAUSED,
                PlaybackState.STATE_PLAYING, PlaybackState.STATE_PLAYING),
            states.allValues.map { it.state },
        )
        assertEquals(listOf(1f, 0f, 1f, 1f), states.allValues.map { it.playbackSpeed })
        assertEquals(updates.last(), getField("nowPlaying"))
    }

    @Test fun songAndCachedArtworkChangesPublishTheNewMetadataAndArtworkRemoval() {
        val firstArtwork = bitmap()
        val nextArtwork = bitmap()
        artworkCache()[7] = firstArtwork
        artworkCache()[8] = nextArtwork
        publish(song)

        val retitled = song.copy(title = "Next song")
        publish(retitled)
        val nextTransfer = retitled.copy(artworkTransferId = 8)
        publish(nextTransfer)
        publish(nextTransfer.copy(artworkTransferId = null))

        val metadata = ArgumentCaptor.forClass(MediaMetadata::class.java)
        verify(session, times(4)).setMetadata(metadata.capture())
        verify(session, times(4)).setPlaybackState(any(PlaybackState::class.java))
        assertEquals(listOf("Song", "Next song", "Next song", "Next song"),
            metadata.allValues.map { it.getString(MediaMetadata.METADATA_KEY_TITLE) })
        assertSame(firstArtwork, metadata.allValues[0].getBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART))
        assertSame(firstArtwork, metadata.allValues[1].getBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART))
        assertSame(nextArtwork, metadata.allValues[2].getBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART))
        assertSame(nextArtwork, metadata.allValues[2].getBitmap(MediaMetadata.METADATA_KEY_DISPLAY_ICON))
        // Upstream 0.2.16 substitutes its quiet placeholder instead of the bright launcher icon.
        val placeholder = getField("placeholder") as Bitmap
        assertSame(placeholder, metadata.allValues[3].getBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART))
        assertSame(placeholder, metadata.allValues[3].getBitmap(MediaMetadata.METADATA_KEY_DISPLAY_ICON))
        org.junit.Assert.assertNotSame(nextArtwork, placeholder)
        assertNull(getField("artwork"))
    }

    @Test fun decodedArtworkForTheSameTransferStillPublishesWithoutReintroducingPositionSpam() {
        publish(song)
        clearInvocations(session)
        val decoded = bitmap()
        val owner = getField("artworkOwner")!!

        CarPlayMediaKeys::class.java.getDeclaredMethod(
            "onArtworkDecoded", Any::class.java, Int::class.javaPrimitiveType, Bitmap::class.java,
        ).apply { isAccessible = true }.invoke(CarPlayMediaKeys, owner, 7, decoded)
        publish(song.copy(elapsedMillis = 2_000))

        val metadata = ArgumentCaptor.forClass(MediaMetadata::class.java)
        verify(session, times(1)).setMetadata(metadata.capture())
        assertSame(decoded, metadata.value.getBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART))
        assertSame(decoded, getField("artwork"))
        verify(session, times(1)).setPlaybackState(any(PlaybackState::class.java))
    }

    @Test fun anUpdateQueuedByTheOldControllerCannotPublishIntoTheReplacementSession() {
        queueUpdate(controller, song)
        val replacement = mock(CarPlayController::class.java)
        val replacementSession = mock(MediaSession::class.java)
        CarPlayMediaKeys.attach(RuntimeEnvironment.getApplication(), replacement)
        setField("session", replacementSession)

        drainMainThread()

        verifyNoInteractions(replacementSession)
        assertEquals(CarPlayNowPlaying(), getField("nowPlaying"))
        assertNull(getField("artwork"))
        queueUpdate(replacement, song.copy(title = "Current controller"))
        drainMainThread()
        val metadata = ArgumentCaptor.forClass(MediaMetadata::class.java)
        verify(replacementSession, times(1)).setMetadata(metadata.capture())
        assertEquals("Current controller", metadata.value.getString(MediaMetadata.METADATA_KEY_TITLE))
        verify(replacementSession, times(1)).setPlaybackState(any(PlaybackState::class.java))
    }

    private fun publish(update: CarPlayNowPlaying) {
        queueUpdate(controller, update)
        drainMainThread()
    }

    private fun queueUpdate(expected: CarPlayController, update: CarPlayNowPlaying) {
        CarPlayMediaKeys::class.java.getDeclaredMethod(
            "onNowPlayingChanged", CarPlayController::class.java, CarPlayNowPlaying::class.java,
        ).apply { isAccessible = true }.invoke(CarPlayMediaKeys, expected, update)
    }

    private fun drainMainThread() = shadowOf(Looper.getMainLooper()).idle()

    private fun bitmap(): Bitmap = Bitmap.createBitmap(2, 2, Bitmap.Config.ARGB_8888).also(bitmaps::add)

    @Suppress("UNCHECKED_CAST")
    private fun artworkCache(): MutableMap<Int, Bitmap?> =
        getField("artworkCache") as MutableMap<Int, Bitmap?>

    private fun getField(name: String): Any? = CarPlayMediaKeys::class.java.getDeclaredField(name)
        .apply { isAccessible = true }.get(CarPlayMediaKeys)

    private fun setField(name: String, value: Any?) {
        CarPlayMediaKeys::class.java.getDeclaredField(name).apply { isAccessible = true }
            .set(CarPlayMediaKeys, value)
    }
}
