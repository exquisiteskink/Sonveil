package app.sonveil.music.data.player

import app.sonveil.music.data.remote.ReplayGain
import app.sonveil.music.data.remote.Song
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.util.concurrent.CountDownLatch
import kotlin.concurrent.thread
import kotlinx.coroutines.runBlocking

class PlaybackQueueStoreTest {
    @get:Rule
    val tmp = TemporaryFolder()

    private fun store(): PlaybackQueueStore =
        PlaybackQueueStore(File(tmp.root, "playback_queue.json"))

    @Test
    fun roundTripPreservesQueueAndPosition() {
        val s = store()
        val songs = listOf(
            Song(id = "1", title = "A", artist = "X", replayGain = ReplayGain(trackGain = 1.5f)),
            Song(id = "2", title = "B"),
        )
        s.save(
            PlaybackQueueStore.Snapshot(
                serverKey = "srv",
                songs = songs,
                index = 1,
                positionMs = 12_345L,
                playWhenReady = true,
                shuffle = true,
                repeatMode = 2,
            ),
        )
        val loaded = s.load()!!
        assertEquals("srv", loaded.serverKey)
        assertEquals(2, loaded.songs.size)
        assertEquals("2", loaded.songs[1].id)
        assertEquals(1, loaded.index)
        assertEquals(12_345L, loaded.positionMs)
        assertTrue(loaded.playWhenReady)
        assertTrue(loaded.shuffle)
        assertEquals(2, loaded.repeatMode)
        assertEquals(1.5f, loaded.songs[0].replayGain!!.trackGain, 0.01f)
    }

    @Test
    fun corruptFileClearsAndReturnsNull() {
        val file = File(tmp.root, "playback_queue.json")
        file.writeText("{not-json")
        val s = PlaybackQueueStore(file)
        assertNull(s.load())
        assertTrue(!file.exists())
    }

    @Test
    fun emptySongsClears() {
        val s = store()
        s.save(PlaybackQueueStore.Snapshot(serverKey = "srv", songs = listOf(Song(id = "1"))))
        s.save(PlaybackQueueStore.Snapshot(serverKey = "srv", songs = emptyList()))
        assertNull(s.load())
    }

    @Test
    fun failedSaveLeavesPreviousQueueIntact() {
        // A save that cannot write its temp must not destroy the already-persisted queue.
        val s = store()
        s.save(PlaybackQueueStore.Snapshot(serverKey = "srv", songs = listOf(Song(id = "keep"))))
        // Make the temp path unwritable by turning the .tmp target into a directory.
        File(tmp.root, "playback_queue.json.tmp").mkdirs()
        s.save(PlaybackQueueStore.Snapshot(serverKey = "srv", songs = listOf(Song(id = "new"))))
        assertEquals("keep", s.load()!!.songs.single().id)
    }

    @Test
    fun rapidSnapshotsCompleteInLogicalOrder() = runBlocking {
        val completed = mutableListOf<String>()
        val writer = PlaybackQueueWriter(
            saveSnapshot = { snapshot ->
                if (snapshot.songs.single().id == "old") Thread.sleep(40)
                synchronized(completed) { completed += snapshot.songs.single().id }
            },
            clearStore = {},
        )

        writer.submit(PlaybackQueueStore.Snapshot(serverKey = "srv", songs = listOf(Song("old"))))
        writer.submit(PlaybackQueueStore.Snapshot(serverKey = "srv", songs = listOf(Song("new"))))
        writer.awaitIdle()

        assertEquals(listOf("old", "new"), completed)
    }

    @Test
    fun clearRacingPendingWriteCannotRecreateOldQueue() = runBlocking {
        val enteredSave = CountDownLatch(1)
        val releaseSave = CountDownLatch(1)
        var stored: PlaybackQueueStore.Snapshot? = null
        val writer = PlaybackQueueWriter(
            saveSnapshot = {
                enteredSave.countDown()
                releaseSave.await()
                stored = it
            },
            clearStore = { stored = null },
        )
        writer.submit(PlaybackQueueStore.Snapshot(serverKey = "srv", songs = listOf(Song("old"))))
        enteredSave.await()

        val clearing = thread { writer.invalidateAndClear() }
        releaseSave.countDown()
        clearing.join()
        writer.awaitIdle()

        assertNull(stored)
    }

    @Test
    fun restoredStatePreservesPositionShuffleAndRepeat() {
        val snapshot = PlaybackQueueStore.Snapshot(
            serverKey = "srv",
            songs = listOf(Song("one"), Song("two", duration = 180)),
            index = 1,
            positionMs = 42_000L,
            shuffle = true,
            repeatMode = 2,
        )

        val restored = PlayerUiState().withRestoredQueue(snapshot)

        assertEquals(1, restored.index)
        assertEquals(42_000L, restored.positionMs)
        assertTrue(restored.shuffle)
        assertEquals(2, restored.repeatMode)
    }
}
