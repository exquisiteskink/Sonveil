package app.sonveil.music.data.player.auto

import androidx.media3.common.MediaItem
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.LibraryResult
import androidx.media3.session.MediaLibraryService.LibraryParams
import androidx.media3.session.MediaLibraryService.MediaLibrarySession
import androidx.media3.session.MediaSession
import androidx.media3.session.SessionError
import app.sonveil.music.AppContainer
import app.sonveil.music.data.player.PlayerController
import com.google.common.collect.ImmutableList
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import com.google.common.util.concurrent.SettableFuture
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.job
import kotlinx.coroutines.launch

/**
 * Media3 library callback: four Home-aligned browse roots + leaf play via
 * [PlayerController.adoptExternalQueue] + resolved stream MediaItems (same Subsonic stream URLs).
 *
 * Connection policy: same-UID phone controllers and trusted / allowlisted car hosts get full
 * library + player commands. Unknown packages are rejected so arbitrary apps cannot drive the queue.
 */
@UnstableApi
class AutoLibraryCallback(
    private val container: AppContainer,
    private val playerController: PlayerController,
    packageName: String,
) : MediaLibrarySession.Callback {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val factory = AutoMediaItemFactory(container.client, packageName) { playerController.transcodeBitrate }
    private val tree = AutoBrowseTree(container, factory)

    override fun onConnect(
        session: MediaSession,
        controller: MediaSession.ControllerInfo,
    ): MediaSession.ConnectionResult {
        if (controller.uid == android.os.Process.myUid()) {
            return super.onConnect(session, controller)
        }
        if (AutoClientGate.mayBrowseAndPlay(controller.isTrusted, controller.packageName)) {
            // Explicit full command set — do not use reject() for Auto hosts that fail isTrusted.
            return MediaSession.ConnectionResult.accept(
                MediaSession.ConnectionResult.DEFAULT_SESSION_AND_LIBRARY_COMMANDS,
                MediaSession.ConnectionResult.DEFAULT_PLAYER_COMMANDS,
            )
        }
        return MediaSession.ConnectionResult.reject()
    }

    override fun onGetLibraryRoot(
        session: MediaLibrarySession,
        browser: MediaSession.ControllerInfo,
        params: LibraryParams?,
    ): ListenableFuture<LibraryResult<MediaItem>> {
        // Must return quickly with a non-null root (no auth / network). AA times out otherwise.
        return Futures.immediateFuture(LibraryResult.ofItem(tree.rootItem(), params))
    }

    override fun onGetChildren(
        session: MediaLibrarySession,
        browser: MediaSession.ControllerInfo,
        parentId: String,
        page: Int,
        pageSize: Int,
        params: LibraryParams?,
    ): ListenableFuture<LibraryResult<ImmutableList<MediaItem>>> = futureResult {
        val normalized = AutoBrowseIds.normalizeParentId(parentId)
        if (!tree.isRootId(normalized) && !tree.hasCredentials()) {
            return@futureResult LibraryResult.ofError(
                SessionError(
                    SessionError.ERROR_SESSION_AUTHENTICATION_EXPIRED,
                    "Sign in to Sonveil on your phone",
                ),
            )
        }
        val all = tree.childrenOf(normalized)
        val sliced = AutoBrowsePaging.slice(all, page, pageSize)
            ?: return@futureResult LibraryResult.ofError(SessionError.ERROR_BAD_VALUE)
        LibraryResult.ofItemList(ImmutableList.copyOf(sliced), params)
    }

    override fun onGetItem(
        session: MediaLibrarySession,
        browser: MediaSession.ControllerInfo,
        mediaId: String,
    ): ListenableFuture<LibraryResult<MediaItem>> = futureResult {
        val item = tree.item(mediaId)
        if (item == null) {
            if (!tree.hasCredentials() && !AutoBrowseIds.isRoot(mediaId) &&
                AutoBrowseIds.normalizeParentId(mediaId) !in rootTabIds()
            ) {
                LibraryResult.ofError(
                    SessionError(
                        SessionError.ERROR_SESSION_AUTHENTICATION_EXPIRED,
                        "Sign in to Sonveil on your phone",
                    ),
                )
            } else {
                LibraryResult.ofError(SessionError.ERROR_BAD_VALUE)
            }
        } else {
            LibraryResult.ofItem(item, null)
        }
    }

    override fun onSetMediaItems(
        session: MediaSession,
        controller: MediaSession.ControllerInfo,
        mediaItems: MutableList<MediaItem>,
        startIndex: Int,
        startPositionMs: Long,
    ): ListenableFuture<MediaSession.MediaItemsWithStartPosition> {
        if (controller.uid == android.os.Process.myUid()) {
            // Preserve the entire phone queue, metadata, URI/bitrate and start position.
            return Futures.immediateFuture(MediaSession.MediaItemsWithStartPosition(mediaItems, startIndex, startPositionMs))
        }
        return futureValue {
            check(AutoClientGate.mayBrowseAndPlay(controller.isTrusted, controller.packageName)) {
                "Untrusted controller"
            }
            val resolved = tree.resolveQueue(mediaItems, startIndex)
                ?: throw IllegalArgumentException("Unknown or stale browse item")
            val playable = factory.playableSongs(resolved.songs, resolved.parent)
            playerController.adoptExternalQueue(resolved.songs, resolved.startIndex)
            MediaSession.MediaItemsWithStartPosition(playable, resolved.startIndex, startPositionMs)
        }
    }

    override fun onPlaybackResumption(
        session: MediaSession,
        controller: MediaSession.ControllerInfo,
    ): ListenableFuture<MediaSession.MediaItemsWithStartPosition> {
        return futureValue {
            val resumed = loadPlaybackResumption(
                restoreSession = container::restoreSession,
                loadItems = playerController::resumptionMediaItems,
            )
            playerController.applyPendingRestoredPlaybackWhenReady(session.player)
            resumed
        }
    }

    override fun onAddMediaItems(
        session: MediaSession,
        controller: MediaSession.ControllerInfo,
        mediaItems: MutableList<MediaItem>,
    ): ListenableFuture<MutableList<MediaItem>> {
        if (controller.uid == android.os.Process.myUid()) return Futures.immediateFuture(mediaItems)
        // External selection uses onSetMediaItems. Arbitrary insertion has no index here
        // with which to keep the phone's Song queue synchronized.
        return Futures.immediateFailedFuture(UnsupportedOperationException("Select a browse item to replace the queue"))
    }

    fun release() {
        scope.coroutineContext.job.cancel()
    }

    private fun rootTabIds(): Set<String> = setOf(
        AutoBrowseIds.PLAYLISTS,
        AutoBrowseIds.RECENT,
        AutoBrowseIds.FAVORITES,
        AutoBrowseIds.NEWEST,
    )

    private fun <T : Any> futureResult(block: suspend () -> LibraryResult<T>): ListenableFuture<LibraryResult<T>> {
        val future = SettableFuture.create<LibraryResult<T>>()
        val job = scope.launch {
            try {
                future.set(block())
            } catch (e: Exception) {
                future.set(LibraryResult.ofError<T>(SessionError.ERROR_UNKNOWN))
            }
        }
        future.addListener({ if (future.isCancelled) job.cancel() }, { it.run() })
        return future
    }

    private fun <T> futureValue(block: suspend () -> T): ListenableFuture<T> {
        val future = SettableFuture.create<T>()
        val job = scope.launch {
            try {
                future.set(block())
            } catch (e: Exception) {
                future.setException(e)
            }
        }
        future.addListener({ if (future.isCancelled) job.cancel() }, { it.run() })
        return future
    }
}

internal suspend fun <T : Any> loadPlaybackResumption(
    restoreSession: suspend () -> Unit,
    loadItems: () -> T?,
): T {
    restoreSession()
    return loadItems() ?: throw UnsupportedOperationException("No persisted queue to resume")
}
