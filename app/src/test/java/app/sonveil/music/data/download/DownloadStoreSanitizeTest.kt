package app.sonveil.music.data.download

import app.sonveil.music.data.auth.AuthMode
import app.sonveil.music.data.auth.StoredCredentials
import app.sonveil.music.data.remote.Song
import java.io.File
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class DownloadStoreSanitizeTest {
    @get:Rule val temp = TemporaryFolder()

    @Test fun keysAreCollisionResistantAndDoNotExposeSecrets() {
        val ids = listOf("song/a?b", "song_a_b", "../../etc/passwd", "a&apiKey=secret", "a".repeat(200) + "x", "a".repeat(200) + "y")
        val keys = ids.map(DownloadStore::sanitizeId)
        assertEquals(ids.size, keys.toSet().size)
        assertTrue(keys.all { it.matches(Regex("[a-f0-9]{64}")) })
        val store = DownloadStore(temp.newFolder())
        val a = StoredCredentials("https://music.example", apiKey = "12345678-a", authMode = AuthMode.ApiKey)
        assertNotEquals(store.serverKey(a), store.serverKey(a.copy(apiKey = "12345678-b")))
    }

    @Test fun coldStoreReconstructsSongsAndExcludesMissingOrTruncatedFiles() {
        val root = temp.newFolder()
        val store = DownloadStore(root)
        val key = store.serverKey(StoredCredentials("https://music.example", "listener"))
        val song = Song("a/b", title = "Track", album = "Album", track = 2, duration = 120, samplingRate = 96000, bitDepth = 24, suffix = "../../bad")
        val file = store.targetFile(key, song)
        assertEquals("songs", file.parentFile!!.name)
        file.writeBytes(byteArrayOf(1, 2, 3))
        store.markDownloaded(key, song, file)
        assertEquals(listOf(song), DownloadStore(root).availableSongs(key))
        file.writeBytes(byteArrayOf(1))
        assertTrue(DownloadStore(root).availableSongs(key).isEmpty())
        assertFalse(store.hasSong(key, song.id))
    }

    @Test fun guessSuffixFromContentType() {
        assertEquals("flac", DownloadStore.guessSuffix("audio/flac"))
        assertEquals("mp3", DownloadStore.guessSuffix("audio/mpeg"))
    }

    @Test fun corruptIndexIsQuarantinedNotSilentlyDiscarded() {
        // A truncated index.json (power loss mid-write) must not be silently treated as empty:
        // that hides every downloaded track while orphaning the files. Preserve it for recovery.
        val root = temp.newFolder()
        val store = DownloadStore(root)
        val key = store.serverKey(StoredCredentials("https://music.example", "listener"))
        val indexFile = File(File(root, key), "index.json")
        indexFile.parentFile?.mkdirs()
        indexFile.writeText("{truncated")
        assertTrue(store.loadIndex(key).songs.isEmpty())
        assertFalse("corrupt index must be moved aside, not left in place", indexFile.isFile)
        val quarantined = File(indexFile.parentFile, "index.json.corrupt")
        assertTrue("corrupt index must be quarantined for recovery", quarantined.isFile)
        assertEquals("{truncated", quarantined.readText())
    }
}
