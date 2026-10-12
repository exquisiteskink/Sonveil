package app.sonveil.music.ui.album

import app.sonveil.music.data.remote.AlbumID3
import app.sonveil.music.ui.components.libraryError
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

internal data class AlbumPageState(
    val albums: List<AlbumID3> = emptyList(), val loading: Boolean = false,
    val more: Boolean = true, val error: String? = null,
)
internal class AlbumPager(
    private val scope: CoroutineScope,
    private val request: suspend (String?, Int) -> List<AlbumID3>,
    private val pageSize: Int = 60,
) {
    private val mutableState = MutableStateFlow(AlbumPageState())
    val state = mutableState.asStateFlow()
    private var folder: String? = null
    private var offset = 0
    private var generation = 0L
    private var job: Job? = null

    fun selectFolder(id: String?) {
        generation++
        job?.cancel()
        folder = id
        offset = 0
        mutableState.value = AlbumPageState()
        loadMore()
    }
    fun loadMore() {
        val old = state.value
        if (old.loading || !old.more) return
        val token = generation
        val start = offset
        val selected = folder
        mutableState.value = old.copy(loading = true, error = null)
        job = scope.launch {
            try {
                val page = request(selected, start)
                if (token != generation) return@launch
                offset = start + page.size
                mutableState.value = AlbumPageState(
                    albums = (state.value.albums + page).distinctBy { it.id }, more = page.size >= pageSize)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                if (token == generation) mutableState.value = state.value.copy(loading = false, error = libraryError(e))
            }
        }
    }
}
