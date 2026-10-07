package app.sonveil.music.data.art

import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class ArtOverrideStoreTest {
    @get:Rule val temp = TemporaryFolder()

    @Test fun setReplaceClearPublishesRevisionsAndKeepsOtherIds() {
        val root = temp.newFolder()
        val store = ArtOverrideStore(root)
        val key = store.albumRevisionKey("a/b")
        store.setAlbumOverride("a/b", byteArrayOf(1))
        store.setAlbumOverride("a?b", byteArrayOf(9))
        assertEquals(1L, store.revisions.value[key])
        store.setAlbumOverride("a/b", byteArrayOf(2))
        assertEquals(2L, store.revisions.value[key])
        assertArrayEquals(byteArrayOf(2), root.resolve("album/${ArtOverrideStore.sanitizeId("a/b")}.jpg").readBytes())
        store.clearAlbumOverride("a/b")
        assertEquals(3L, store.revisions.value[key])
        assertFalse(store.hasAlbumOverride("a/b"))
        assertTrue(store.hasAlbumOverride("a?b"))
    }

    @Test fun oversizedImportPreservesExistingArtwork() {
        val root = temp.newFolder()
        val store = ArtOverrideStore(root)
        store.setAlbumOverride("album", byteArrayOf(1, 2, 3))
        assertThrows(IllegalArgumentException::class.java) {
            store.setAlbumOverride("album", ByteArray(ArtOverrideStore.MAX_IMAGE_BYTES + 1))
        }
        assertArrayEquals(byteArrayOf(1, 2, 3), root.resolve("album/${ArtOverrideStore.sanitizeId("album")}.jpg").readBytes())
        assertEquals(1L, store.revisions.value[store.albumRevisionKey("album")])
    }

    @Test fun completeIdsHaveDistinctSafeKeys() {
        val ids = listOf("a/b", "a?b", "a_b", "", "unknown", "a".repeat(200) + "x", "a".repeat(200) + "y")
        val keys = ids.map(ArtOverrideStore::sanitizeId)
        assertEquals(ids.size, keys.toSet().size)
        assertTrue(keys.all { it.matches(Regex("[0-9a-f]{64}")) })
    }
}
