package app.sonveil.music.data.player

import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.media3.common.util.UnstableApi
import androidx.media3.database.StandaloneDatabaseProvider
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.ResolvingDataSource
import androidx.media3.datasource.cache.CacheDataSource
import androidx.media3.datasource.cache.CacheWriter
import androidx.media3.datasource.cache.LeastRecentlyUsedCacheEvictor
import androidx.media3.datasource.cache.SimpleCache
import app.sonveil.music.data.remote.SubsonicClient
import app.sonveil.music.data.auth.StoredCredentials
import app.sonveil.music.data.remote.SubsonicException
import java.io.File
import java.util.concurrent.Executors

/** One process cache shared by all players, separate from saved downloads. */
@UnstableApi
internal class PlaybackCache(context: Context, private val client: SubsonicClient, http: DataSource.Factory) {
    private val prefetchExecutor = Executors.newSingleThreadExecutor()
    private val prefetchLock = Any()
    @Volatile private var released = false
    private var prefetchGeneration = 0L
    private var prefetchWriter: CacheWriter? = null
    private var prefetchedUris: List<Uri> = emptyList()
    private val cache = runCatching {
        synchronized(PlaybackCache::class.java) {
            sharedCache ?: SimpleCache(File(context.cacheDir, "playback-audio"), LeastRecentlyUsedCacheEvictor(MAX_BYTES),
                StandaloneDatabaseProvider(context.applicationContext)).also { sharedCache = it }
        }
    }.onFailure { Log.w(TAG, "Temporary audio cache unavailable; streaming directly", it) }.getOrNull()

    private val resolvingHttp = DataSource.Factory {
        var locator: Uri? = null
        ResolvingDataSource(http.createDataSource(), object : ResolvingDataSource.Resolver {
            override fun resolveDataSpec(spec: DataSpec): DataSpec {
                val songId = PlaybackStreamUri.songId(spec.uri)
                locator = spec.uri.takeIf { songId != null }
                val credentials = spec.customData as? StoredCredentials
                return if (songId == null) spec else spec.withUri(Uri.parse(client.streamUrl(songId, PlaybackStreamUri.bitrate(spec.uri), credentials)))
            }

            override fun resolveReportedUri(uri: Uri): Uri = locator ?: uri
        })
    }

    private val cachedHttp = cache?.let { audioCache ->
        CacheDataSource.Factory()
            .setCache(audioCache)
            .setUpstreamDataSourceFactory(resolvingHttp)
            // Disk trouble must not stop otherwise healthy network playback.
            .setFlags(CacheDataSource.FLAG_IGNORE_CACHE_ON_ERROR)
    }

    // Snapshot credentials and byte identity together, before the cache or network
    // opens. Account changes cannot write another account's bytes under this key.
    private val scopedSource = ResolvingDataSource.Factory(cachedHttp ?: resolvingHttp) { spec -> scope(spec) }
    val dataSourceFactory: DataSource.Factory = DefaultDataSource.Factory(context, scopedSource)

    private fun scope(spec: DataSpec): DataSpec {
        val songId = PlaybackStreamUri.songId(spec.uri) ?: return spec
        // Sign-out can null credentials mid-load. Throw a retryable IOException (not a domain
        // exception): Media3 treats a non-IOException from a DataSource as an unretryable
        // UnexpectedLoaderException that permanently wedges the player.
        val credentials = client.credentials ?: throw java.io.IOException("Not signed in")
        val account = listOf(credentials.authMode.toString(), credentials.username, credentials.apiKey)
            .joinToString("") { "${it.length}:$it" }
        return spec.buildUpon().setCustomData(credentials).setKey(PlaybackCacheKey.build(
            credentials.serverUrl, account, songId, PlaybackStreamUri.bitrate(spec.uri))).build()
    }

    /** Queue look-ahead is best effort and never blocks the audible player's reads. */
    fun prefetch(uris: List<Uri>) {
        if (released) return
        val factory = cachedHttp ?: return
        val remote = uris.filter { PlaybackStreamUri.songId(it) != null }.take(2)
        val generation = synchronized(prefetchLock) {
            if (remote == prefetchedUris) return
            prefetchedUris = remote
            prefetchGeneration++
            prefetchWriter?.cancel()
            prefetchGeneration
        }
        if (remote.isEmpty()) return
        prefetchExecutor.execute {
            for (uri in remote) {
                try {
                    val spec = scope(DataSpec.Builder().setUri(uri).setLength(PREFETCH_BYTES_PER_SONG).build())
                    val writer = CacheWriter(factory.createDataSource(), spec, null, null)
                    synchronized(prefetchLock) {
                        if (generation != prefetchGeneration) return@execute
                        prefetchWriter = writer
                    }
                    writer.cache()
                } catch (_: java.io.IOException) {
                    // A coverage gap must not pause/skip the actual player.
                } catch (_: SubsonicException) {
                    // Sign-out can race with a queued best-effort prefetch.
                    return@execute
                } finally {
                    synchronized(prefetchLock) {
                        if (generation == prefetchGeneration) prefetchWriter = null
                    }
                }
            }
        }
    }

    /**
     * Stop look-ahead. The [SimpleCache] and its executor are process-scoped and shared across
     * service restarts, so they are intentionally NOT torn down here (releasing the disk cache on
     * every onDestroy would defeat reuse and leak its init thread only at true process death).
     * After this, [prefetch] is a no-op rather than throwing on a shut-down executor.
     */
    fun release() {
        released = true
        prefetch(emptyList())
    }

    companion object {
        const val MAX_BYTES = 256L * 1024 * 1024
        private const val PREFETCH_BYTES_PER_SONG = 32L * 1024 * 1024
        private const val TAG = "PlaybackCache"
        private var sharedCache: SimpleCache? = null
    }
}
