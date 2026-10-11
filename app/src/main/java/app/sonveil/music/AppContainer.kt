package app.sonveil.music

import android.content.Context
import app.sonveil.music.data.auth.CredentialStore
import app.sonveil.music.data.auth.StoredCredentials
import app.sonveil.music.data.player.AudioOutputMonitor
import app.sonveil.music.data.player.PlayerController
import app.sonveil.music.data.player.PlayerSettings
import app.sonveil.music.data.download.DownloadStore
import app.sonveil.music.data.download.OfflineDownloadManager
import app.sonveil.music.data.remote.MetadataRepository
import app.sonveil.music.data.remote.SubsonicClient
import app.sonveil.music.data.search.RecentSearchStore
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.CancellationException

class AppContainer(context: Context) {
    val credentials = CredentialStore(context)
    val client = SubsonicClient()
    val metadata = MetadataRepository(client)
    val playerSettings = PlayerSettings(context.applicationContext)
    val outputs = AudioOutputMonitor(context.applicationContext, playerSettings)
    val downloadStore = DownloadStore(context.applicationContext)
    val downloads = OfflineDownloadManager(context.applicationContext, client, downloadStore, playerSettings)
    val player = PlayerController(context.applicationContext, client, downloadStore)
    val recentSearches = RecentSearchStore(context.applicationContext)

    init {
        outputs.start()
    }

    private val _loggedIn = MutableStateFlow(false)
    val loggedIn: StateFlow<Boolean> = _loggedIn

    /**
     * False while a cold-start restore with saved credentials is still in flight.
     * UI must not show [Login] until this is true — otherwise saved sessions flash Login.
     * Starts true when there is no credential blob (Login is the correct first screen).
     */
    private val _authResolved = MutableStateFlow(!credentials.hasCredentials())
    val authResolved: StateFlow<Boolean> = _authResolved

    private val loginMutex = Mutex()
    private var restored = false

    suspend fun restoreSession() = loginMutex.withLock {
        // Allow retries after transient failures even if we already opened the app shell.
        if (restored) return@withLock
        val stored = credentials.load()
        if (stored == null) {
            if (credentials.hasCredentials()) credentials.clear()
            restored = true
            setLoggedIn(false)
            _authResolved.value = true
            return@withLock
        }
        // Older HTTP accounts must consent before any authenticated network request.
        // Keep the encrypted account so Login can prefill it, without silently opting in.
        if (stored.serverUrl.startsWith("http://", ignoreCase = true) && !stored.allowInsecureLanHttp) {
            client.credentials = null
            restored = true
            setLoggedIn(false)
            _authResolved.value = true
            return@withLock
        }
        // Credentials were saved only after a successful login. Prefer the app shell
        // (or a brief splash) over Login while we validate — never loop Login on valid store.
        client.credentials = stored
        val hasDownloads = try {
            kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                downloadStore.availableSongs(downloadStore.serverKey(stored)).isNotEmpty()
            }
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            false
        }
        if (hasDownloads) {
            restored = true
            setLoggedIn(true)
            _authResolved.value = true
            downloads.resumeIfPending()
            return@withLock
        }
        try {
            val (accepted, _) = client.login(stored)
            credentials.save(accepted)
            restored = true
            setLoggedIn(true)
            _authResolved.value = true
            downloads.resumeIfPending()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            if (e is app.sonveil.music.data.remote.SubsonicException && e.code in listOf(40, 41, 42, 43, 44)) {
                // Permanent auth failure — forget the saved session and show Login.
                client.credentials = null
                credentials.clear()
                restored = true
                setLoggedIn(false)
                _authResolved.value = true
            } else {
                // Transient network/server errors: keep encrypted store, stay in the app
                // (or finish splash into app). Leave restored=false so Auto / Login can retry.
                setLoggedIn(true)
                _authResolved.value = true
                downloads.resumeIfPending()
            }
        }
    }

    suspend fun signIn(candidate: StoredCredentials) = loginMutex.withLock {
        downloads.cancelAndJoin()
        try {
            val (accepted, _) = client.login(candidate)
            credentials.save(accepted)
            restored = true
            setLoggedIn(true)
            _authResolved.value = true
            downloads.resumeIfPending()
        } catch (e: Exception) {
            client.credentials = null
            throw e
        }
    }

    fun signOut() {
        downloads.cancel()
        player.stopAndReset()
        client.http.dispatcher.cancelAll()
        client.credentials = null
        client.rotateSessionSalt()
        credentials.clear()
        restored = true
        setLoggedIn(false)
        _authResolved.value = true
    }

    fun setLoggedIn(value: Boolean) {
        _loggedIn.value = value
        if (value) player.connect()
    }
}
