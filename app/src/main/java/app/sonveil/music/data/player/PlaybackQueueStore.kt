package app.sonveil.music.data.player

import android.content.Context
import app.sonveil.music.data.remote.Song
import java.io.File
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * Persists the active play queue so [PlayerController] / [PlaybackService] can
 * rebuild MediaItems after process death. Never stores authenticated stream URLs
 * (those are rebuilt via SubsonicClient + DownloadStore at restore time).
 */
class PlaybackQueueStore internal constructor(private val file: File) {
    constructor(context: Context) : this(
        File(context.applicationContext.filesDir, "playback_queue.json"),
    )

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
        allowSpecialFloatingPointValues = true
    }

    @Serializable
    data class Snapshot(
        val version: Int = VERSION,
        val serverKey: String,
        val songs: List<Song> = emptyList(),
        val index: Int = 0,
        val positionMs: Long = 0L,
        val playWhenReady: Boolean = false,
        val shuffle: Boolean = false,
        val repeatMode: Int = 0,
    )

    @Synchronized
    fun load(): Snapshot? {
        if (!file.isFile) return null
        return runCatching {
            val snap = json.decodeFromString(Snapshot.serializer(), file.readText())
            if (snap.version != VERSION || snap.songs.isEmpty()) {
                clear()
                return null
            }
            val songs = snap.songs.take(MAX_SONGS)
            snap.copy(
                index = snap.index.coerceIn(0, songs.lastIndex),
                positionMs = snap.positionMs.coerceAtLeast(0L),
                songs = songs,
            )
        }.getOrElse {
            clear()
            null
        }
    }

    @Synchronized
    fun save(snapshot: Snapshot) {
        if (snapshot.songs.isEmpty()) {
            clear()
            return
        }
        val songs = snapshot.songs.take(MAX_SONGS)
        val trimmed = snapshot.copy(
            version = VERSION,
            songs = songs,
            index = snapshot.index.coerceIn(0, songs.lastIndex),
            positionMs = snapshot.positionMs.coerceAtLeast(0L),
        )
        file.parentFile?.mkdirs()
        val tmp = File(file.parentFile, "${file.name}.tmp")
        runCatching {
            tmp.writeText(json.encodeToString(Snapshot.serializer(), trimmed))
            // Atomic replace: rename the fully-written temp over the target. Only if that fails
            // (some filesystems reject rename-over-existing) delete the stale target and retry —
            // the temp is already durable, so the queue is never left absent.
            if (!tmp.renameTo(file)) {
                file.delete()
                check(tmp.renameTo(file)) { "Cannot replace playback queue file" }
            }
        }.onFailure {
            tmp.delete()
        }
    }

    @Synchronized
    fun clear() {
        file.delete()
        File(file.parentFile, "${file.name}.tmp").delete()
    }

    companion object {
        const val VERSION = 1
        const val MAX_SONGS = 500
    }
}

/** A single ordered writer for queue snapshots. */
internal class PlaybackQueueWriter(
    private val saveSnapshot: (PlaybackQueueStore.Snapshot) -> Unit,
    private val clearStore: () -> Unit,
    dispatcher: CoroutineDispatcher = Dispatchers.IO,
) {
    constructor(store: PlaybackQueueStore, dispatcher: CoroutineDispatcher = Dispatchers.IO) : this(
        saveSnapshot = store::save,
        clearStore = store::clear,
        dispatcher = dispatcher,
    )

    private sealed interface Command {
        data class Save(val generation: Long, val snapshot: PlaybackQueueStore.Snapshot) : Command
        data class Flush(val done: CompletableDeferred<Unit>) : Command
    }

    private val lock = Any()
    private var generation = 0L
    private var accepting = true
    private val commands = Channel<Command>(Channel.UNLIMITED)
    private val scope = CoroutineScope(SupervisorJob() + dispatcher)

    init {
        scope.launch {
            for (command in commands) {
                when (command) {
                    is Command.Save -> synchronized(lock) {
                        if (accepting && command.generation == generation) saveSnapshot(command.snapshot)
                    }
                    is Command.Flush -> command.done.complete(Unit)
                }
            }
        }
    }

    fun submit(snapshot: PlaybackQueueStore.Snapshot) {
        val command = synchronized(lock) {
            if (!accepting) return
            Command.Save(generation, snapshot)
        }
        commands.trySend(command)
    }

    fun activate() = synchronized(lock) {
        accepting = true
    }

    /**
     * Waits for an in-progress write, invalidates queued writes, then clears.
     * Holding the same lock as [saveSnapshot] ensures no old save can recreate
     * the file after this method returns.
     */
    fun invalidateAndClear() = synchronized(lock) {
        generation += 1
        accepting = false
        clearStore()
    }

    internal suspend fun awaitIdle() {
        val done = CompletableDeferred<Unit>()
        commands.send(Command.Flush(done))
        done.await()
    }
}
