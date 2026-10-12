package app.sonveil.music.ui.search

import app.sonveil.music.data.remote.SearchResult3
import app.sonveil.music.ui.components.libraryError
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

internal data class SearchRequest(
    val query: String, val artistOffset: Int = 0, val albumOffset: Int = 0, val songOffset: Int = 0,
)
internal data class MusicSearchState(
    val query: String = "",
    val loading: Boolean = false,
    val hasSearched: Boolean = false,
    val result: SearchResult3 = SearchResult3(),
    val error: String? = null,
    val paging: Boolean = false,
    val pageError: String? = null,
    val canLoadMore: Boolean = false,
)

/** Owned by the screen's coroutine scope. All mutations are on its UI dispatcher. */
internal class MusicSearch(
    private val scope: CoroutineScope,
    private val request: suspend (SearchRequest) -> SearchResult3,
    private val debounceMs: Long = 300,
) {
    private val mutableState = MutableStateFlow(MusicSearchState())
    val state = mutableState.asStateFlow()
    private var generation = 0L
    private var job: Job? = null
    private var offsets = SearchRequest("")

    fun setQuery(query: String) {
        if (query == state.value.query) return
        start(query)
    }
    fun retry() = start(state.value.query)

    private fun start(query: String) {
        val token = ++generation
        job?.cancel()
        offsets = SearchRequest(query.trim())
        val valid = query.trim().length >= 2
        mutableState.value = MusicSearchState(query = query, loading = valid)
        if (!valid) return
        job = scope.launch {
            delay(debounceMs)
            try {
                val page = request(SearchRequest(query.trim()))
                if (token != generation) return@launch
                offsets = SearchRequest(query.trim(), page.artist.size, page.album.size, page.song.size)
                mutableState.value = state.value.copy(loading = false, hasSearched = true,
                    result = page, canLoadMore = hasMore(page))
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                if (token == generation) mutableState.value = state.value.copy(loading = false, error = libraryError(e))
            }
        }
    }
    fun loadMore() {
        val current = state.value
        if (current.loading || current.paging || !current.canLoadMore) return
        val token = generation
        val next = offsets
        mutableState.value = current.copy(paging = true, pageError = null)
        job = scope.launch {
            try {
                val page = request(next)
                if (token != generation) return@launch
                // Offsets count server rows, not deduplicated UI rows.
                offsets = next.copy(artistOffset = next.artistOffset + page.artist.size,
                    albumOffset = next.albumOffset + page.album.size, songOffset = next.songOffset + page.song.size)
                val old = state.value.result
                val merged = SearchResult3(
                    artist = (old.artist + page.artist).distinctBy { it.id },
                    album = (old.album + page.album).distinctBy { it.id },
                    song = (old.song + page.song).distinctBy { it.id },
                )
                mutableState.value = state.value.copy(result = merged, paging = false, canLoadMore = hasMore(page))
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                if (token == generation) mutableState.value = state.value.copy(paging = false, pageError = libraryError(e))
            }
        }
    }
    private fun hasMore(page: SearchResult3) = page.artist.size >= 20 || page.album.size >= 20 || page.song.size >= 20
}
