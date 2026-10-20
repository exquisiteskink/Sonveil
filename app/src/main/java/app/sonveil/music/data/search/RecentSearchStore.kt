package app.sonveil.music.data.search

import android.content.Context
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json

/**
 * Client-side recent search queries (SharedPreferences).
 * Server suggestions use Subsonic/OpenSubsonic [search3] only — no invented APIs.
 */
class RecentSearchStore(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    private val json = Json { ignoreUnknownKeys = true }

    fun list(): List<String> {
        val raw = prefs.getString(KEY, "").orEmpty()
        if (raw.isBlank()) return emptyList()
        // New format: JSON array of strings. Legacy value was a "\u0001"-joined list,
        // which a query containing the separator character could corrupt.
        return runCatching {
            json.decodeFromString(ListSerializer(String.serializer()), raw)
        }.getOrElse {
            if (raw.startsWith("[")) emptyList() else raw.split('\u0001')
        }
            .map { it.trim() }
            .filter { it.isNotEmpty() }
    }

    fun add(query: String) {
        val q = query.trim()
        if (q.length < 2) return
        val next = (listOf(q) + list().filter { !it.equals(q, ignoreCase = true) }).take(MAX)
        prefs.edit().putString(KEY, json.encodeToString(ListSerializer(String.serializer()), next)).apply()
    }

    fun clear() {
        prefs.edit().remove(KEY).apply()
    }

    companion object {
        // Keep the legacy preference file so upgrades retain recent searches.
        private const val PREFS = "auralis_search"
        private const val KEY = "recent_queries"
        private const val MAX = 12
    }
}
