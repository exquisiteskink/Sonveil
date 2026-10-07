package app.sonveil.music.data.download

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import app.sonveil.music.data.player.PlayerSettings
import app.sonveil.music.data.remote.Song
import app.sonveil.music.data.remote.SubsonicClient
import app.sonveil.music.data.remote.SubsonicException
import java.io.File
import java.io.IOException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.currentCoroutineContext
import app.sonveil.music.data.auth.StoredCredentials
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.Request

/**
 * Sequential offline downloader using OpenSubsonic [download](https://opensubsonic.netlify.app/docs/endpoints/download/).
 * Authenticated URLs are built per request and never written to disk.
 */
class OfflineDownloadManager(
    context: Context,
    private val client: SubsonicClient,
    private val store: DownloadStore,
    private val settings: PlayerSettings,
) {
    private val appContext = context.applicationContext
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val work = DownloadWorkQueue(scope)

    private val _state = MutableStateFlow(DownloadUiState())
    val state: StateFlow<DownloadUiState> = _state

    init {
        val cm = appContext.getSystemService(ConnectivityManager::class.java)
        cm?.registerDefaultNetworkCallback(object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) {
                resumeIfPending()
            }
        })
    }

    fun refreshBytes() {
        val creds = client.credentials ?: run {
            _state.update { it.copy(bytesUsed = 0L) }
            return
        }
        val key = store.serverKey(creds)
        _state.update { it.copy(bytesUsed = store.bytesUsed(key)) }
    }

    fun isDownloaded(songId: String): Boolean {
        val creds = client.credentials ?: return false
        return store.hasSong(store.serverKey(creds), songId)
    }

    fun playbackUri(songId: String): android.net.Uri? {
        val creds = client.credentials ?: return null
        return store.playbackUri(store.serverKey(creds), songId)
    }

    fun enqueueAlbum(albumId: String, songs: List<Song>, label: String = "Album") {
        enqueue(songs, collectionLabel = "$label", collectionId = "album:$albumId")
    }

    fun enqueuePlaylist(playlistId: String, songs: List<Song>, label: String = "Playlist") {
        enqueue(songs, collectionLabel = "$label", collectionId = "playlist:$playlistId")
    }

    fun enqueue(songs: List<Song>, collectionLabel: String? = null, collectionId: String? = null) {
        if (songs.isEmpty()) return
        val creds = client.credentials
        if (creds == null) {
            _state.update {
                it.copy(phase = DownloadPhase.Failed, message = "Not signed in")
            }
            return
        }
        if (!WifiGate.allowHiResDownload(appContext, settings.wifiOnlyHiResDownloads)) {
            store.savePending(store.serverKey(creds), PendingDownload(collectionLabel, collectionId, songs.toList()))
            _state.update {
                it.copy(
                    phase = DownloadPhase.PausedWifi,
                    message = "HiRes downloads require Wi‑Fi. They will continue when Wi‑Fi is available.",
                    collectionLabel = collectionLabel,
                    done = 0,
                    total = songs.size,
                )
            }
            return
        }
        work.replace {
            if (client.credentials != creds) return@replace
            runBatch(songs.toList(), collectionLabel, collectionId, creds)
        }
    }

    fun cancel() = work.stop {
        _state.value = DownloadUiState(message = "Cancelled")
    }

    /** Continue a batch that was saved because Wi‑Fi dropped or the process ended. */
    fun resumeIfPending() {
        val creds = client.credentials ?: return
        if (_state.value.phase == DownloadPhase.Running) return
        if (!WifiGate.allowHiResDownload(appContext, settings.wifiOnlyHiResDownloads)) return
        val pending = store.loadPending(store.serverKey(creds)) ?: return
        enqueue(pending.songs, pending.label, pending.collectionId)
    }

    suspend fun cancelAndJoin() { cancel().join() }

    fun clearDownloads() {
        val creds = client.credentials
        work.stop {
            withContext(Dispatchers.IO) {
                if (creds != null) store.clearAll(store.serverKey(creds))
                else store.clearEverything()
            }
            _state.value = DownloadUiState(message = "Offline downloads cleared")
        }
    }

    private suspend fun runBatch(
        songs: List<Song>,
        collectionLabel: String?,
        collectionId: String?,
        creds: StoredCredentials,
    ) {
        val key = store.serverKey(creds)
        val pending = songs.filter { !store.hasSong(key, it.id) }
        store.savePending(key, PendingDownload(collectionLabel, collectionId, pending))
        if (pending.isEmpty()) {
            store.clearPending(key)
            _state.update {
                it.copy(
                    phase = DownloadPhase.Done,
                    collectionLabel = collectionLabel,
                    done = songs.size,
                    total = songs.size,
                    message = "Already downloaded",
                    bytesUsed = store.bytesUsed(key),
                    currentTitle = null,
                )
            }
            return
        }
        _state.update {
            it.copy(
                phase = DownloadPhase.Running,
                collectionLabel = collectionLabel,
                done = 0,
                total = pending.size,
                message = null,
                currentTitle = pending.firstOrNull()?.title,
                bytesUsed = store.bytesUsed(key),
            )
        }
        var done = 0
        var failed = 0
        val remaining = pending.toMutableList()
        for (song in pending) {
            currentCoroutineContext().ensureActive()
            if (client.credentials != creds) return
            if (!WifiGate.allowHiResDownload(appContext, settings.wifiOnlyHiResDownloads)) {
                store.savePending(key, PendingDownload(collectionLabel, collectionId, remaining.toList()))
                _state.update {
                    it.copy(
                        phase = DownloadPhase.PausedWifi,
                        message = "HiRes downloads require Wi‑Fi. They will continue when Wi‑Fi is available.",
                        done = done,
                        currentTitle = song.title,
                        bytesUsed = store.bytesUsed(key),
                    )
                }
                return
            }
            _state.update { it.copy(currentTitle = song.title, phase = DownloadPhase.Running) }
            try {
                downloadOne(key, song, creds)
                done++
                remaining.remove(song)
                store.savePending(key, PendingDownload(collectionLabel, collectionId, remaining.toList()))
                _state.update {
                    it.copy(done = done, bytesUsed = store.bytesUsed(key))
                }
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (_: Exception) {
                failed++
                remaining.remove(song)
                store.savePending(key, PendingDownload(collectionLabel, collectionId, remaining.toList()))
            }
        }
        store.clearPending(key)
        val message = when {
            failed == 0 -> "Download complete"
            done == 0 -> "Download failed; check the connection and retry"
            else -> "Saved $done tracks. $failed could not be downloaded."
        }
        _state.update {
            it.copy(
                phase = if (done == 0 && failed > 0) DownloadPhase.Failed else DownloadPhase.Done,
                done = done,
                total = pending.size,
                currentTitle = null,
                message = message,
                bytesUsed = store.bytesUsed(key),
            )
        }
    }

    private suspend fun downloadOne(key: String, song: Song, creds: StoredCredentials) {
        val request = Request.Builder().url(client.downloadUrl(song.id, creds)).get().build()
        OfflineTransfer.download(client.http.newCall(request), store.targetFile(key, song), song.size, storageBudget = (32L * 1024 * 1024 * 1024 - store.totalBytesUsed()).coerceAtLeast(0L)) { file ->
            check(client.credentials == creds) { "Account changed" }
            store.markDownloaded(key, song, file)
        }
    }
}
