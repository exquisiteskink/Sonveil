package app.sonveil.music.data.search

import android.content.Context

/**
 * Client-side recent search queries (SharedPreferences).
 * Server suggestions use Subsonic/OpenSubsonic [search3] only — no invented APIs.
 */
class RecentSearchStore(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun list(): List<String> {
        val raw = prefs.getString(KEY, "").orEmpty()
        if (raw.isBlank()) return emptyList()
        return raw.split(SEP).map { it.trim() }.filter { it.isNotEmpty() }
    }

    fun add(query: String) {
        val q = query.trim()
        if (q.length < 2) return
        val next = (listOf(q) + list().filter { !it.equals(q, ignoreCase = true) }).take(MAX)
        prefs.edit().putString(KEY, next.joinToString(SEP)).apply()
    }

    fun clear() {
        prefs.edit().remove(KEY).apply()
    }

    companion object {
        // Keep the legacy preference file so upgrades retain recent searches.
        private const val PREFS = "auralis_search"
        private const val KEY = "recent_queries"
        private const val SEP = "\u0001"
        private const val MAX = 12
    }
}
