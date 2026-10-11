package app.sonveil.music.data.player.auto

/**
 * Android Auto / AAOS do not support pagination for media browse. Media3 hosts may still
 * pass page/pageSize; a non-positive pageSize means “return the full list”.
 */
internal object AutoBrowsePaging {
    /**
     * Android Auto / AAOS do not support pagination for media browse. Media3 hosts may still
     * pass page/pageSize; a non-positive pageSize means "return the full list". Car hosts
     * (browser.uid != our uid) cannot request subsequent pages, so they receive the full list
     * regardless of pageSize — truncating it would make items past the first page unreachable.
     * Phone/Media3-native clients still get proper slicing.
     */
    fun <T> forClient(all: List<T>, page: Int, pageSize: Int, isCarHost: Boolean): List<T>? {
        if (page < 0) return null
        return if (isCarHost) all else slice(all, page, pageSize)
    }

    fun <T> slice(all: List<T>, page: Int, pageSize: Int): List<T>? {
        if (page < 0) return null
        if (pageSize <= 0) return all
        val from = (page.toLong() * pageSize).coerceAtMost(all.size.toLong()).toInt()
        val to = (from.toLong() + pageSize).coerceAtMost(all.size.toLong()).toInt()
        return all.subList(from, to)
    }
}
