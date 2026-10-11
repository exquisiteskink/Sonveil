package app.sonveil.music.data.art

/** Reuse server album cover IDs within an account, without guessing from track IDs. */
internal class AlbumCoverIds(private val maxEntries: Int = 256) {
    private data class Key(val account: String, val albumId: String)
    private val covers = LinkedHashMap<Key, String>(16, 0.75f, true)
    private val locks = Array(16) { Any() }

    fun resolve(account: String, albumId: String, load: () -> String?): String? {
        val key = Key(account, albumId)
        return synchronized(locks[(key.hashCode() and Int.MAX_VALUE) % locks.size]) {
            synchronized(covers) { covers[key] } ?: load()?.takeIf { it.isNotBlank() }?.also { cover ->
                synchronized(covers) {
                    covers[key] = cover
                    while (covers.size > maxEntries) covers.remove(covers.keys.first())
                }
            }
        }
    }
}
