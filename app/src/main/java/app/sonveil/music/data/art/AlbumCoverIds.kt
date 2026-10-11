package app.sonveil.music.data.art

import java.util.concurrent.CompletableFuture
import java.util.concurrent.ConcurrentHashMap

/**
 * Reuse server album cover IDs within an account, without guessing from track IDs.
 *
 * [load] performs a network [app.sonveil.music.data.remote.SubsonicClient.getAlbum] and may take
 * seconds on a slow or hung server, so it must never run while a shared lock is held: that would
 * stall unrelated covers on the content provider's binder thread. Concurrency is handled by a
 * per-key in-flight future — concurrent resolves of one album share a single [load] call — while
 * the LRU map is only touched for O(1) reads/writes under a short critical section.
 */
internal class AlbumCoverIds(private val maxEntries: Int = 256) {
    private data class Key(val account: String, val albumId: String)
    private val covers = LinkedHashMap<Key, String>(16, 0.75f, true)
    private val inFlight = ConcurrentHashMap<Key, CompletableFuture<String?>>()

    fun resolve(account: String, albumId: String, load: () -> String?): String? {
        val key = Key(account, albumId)
        cached(key)?.let { return it }
        // First caller owns the future and runs load() with no lock held; concurrent callers for
        // the same key wait on it and reuse its result (falling back to their own load only on a
        // miss/eviction race or a load that returned nothing).
        val future = CompletableFuture<String?>()
        val existing = inFlight.putIfAbsent(key, future)
        if (existing != null) return runCatching { existing.get() }.getOrNull() ?: cached(key) ?: load()
        return try {
            val cover = load()?.takeIf { it.isNotBlank() }
            if (cover != null) put(key, cover)
            cover
        } finally {
            inFlight.remove(key)
            future.complete(cached(key))
        }
    }

    private fun cached(key: Key): String? = synchronized(covers) { covers[key] }

    private fun put(key: Key, cover: String) = synchronized(covers) {
        covers[key] = cover
        while (covers.size > maxEntries) covers.remove(covers.keys.first())
    }
}
