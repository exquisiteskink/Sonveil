package app.sonveil.music.data.player.auto

/**
 * Android Auto / AAOS do not support pagination for media browse. Media3 hosts may still
 * pass page/pageSize; a non-positive pageSize means “return the full list”.
 */
internal object AutoBrowsePaging {
    fun <T> slice(all: List<T>, page: Int, pageSize: Int): List<T>? {
        if (page < 0) return null
        if (pageSize <= 0) return all
        val from = (page.toLong() * pageSize).coerceAtMost(all.size.toLong()).toInt()
        val to = (from.toLong() + pageSize).coerceAtMost(all.size.toLong()).toInt()
        return all.subList(from, to)
    }
}
