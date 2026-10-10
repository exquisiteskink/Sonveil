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
 * [PlayerController.adoptExternalQueue] + resolved stream MediaItems (opaque locators;
 * the player mints Subsonic stream URLs).
 *
 * Connection policy: same-UID phone controllers and trusted / signature-checked car hosts
 * get full library + player commands. Unknown packages, and allowlisted names that are not
 * system or platform-signed, are rejected.
 */
@UnstableApi
class AutoLibraryCallback(
    private val container: AppContainer,
    private val playerController: PlayerController,
    packageName: String,
    private val hostIdentity: (String?) -> AutoClientGate.HostIdentity = { pkg -> AutoClientGate.HostIdentity(pkg, false) },
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
        val host = hostIdentity(controller.packageName)
        if (AutoClientGate.mayBrowseAndPlay(controller.isTrusted, controller.packageName, host.trustedHost)) {
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
            return Futures.immediateFuture(MediaSession.MediaItemsWithStartPosition(mediaItems, startIndex, startPositionMs))
        }
        return futureValue {
            val host = hostIdentity(controller.packageName)
            check(AutoClientGate.mayBrowseAndPlay(controller.isTrusted, controller.packageName, host.trustedHost)) {
                "Untrusted controller"
            }
            val voiceRequest = mediaItems.singleOrNull()?.requestMetadata
                ?.takeIf { it.searchQuery != null }
            val resolved = if (voiceRequest != null) {
                tree.resolveVoice(voiceRequest.searchQuery.orEmpty(), voiceRequest.extras)
            } else {
                tree.resolveQueue(mediaItems, startIndex)
            }
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
        val request = mediaItems.singleOrNull()?.requestMetadata
            ?.takeIf { it.searchQuery != null }
            ?: return Futures.immediateFailedFuture(
                UnsupportedOperationException("Select a browse item to replace the queue"),
            )
        return futureValue {
            val host = hostIdentity(controller.packageName)
            check(AutoClientGate.mayBrowseAndPlay(controller.isTrusted, controller.packageName, host.trustedHost)) {
                "Untrusted controller"
            }
            val resolved = tree.resolveVoice(request.searchQuery.orEmpty(), request.extras)
                ?: throw IllegalArgumentException("No music matches the voice request")
            playerController.adoptExternalQueue(resolved.songs, 0)
            factory.playableSongs(resolved.songs, resolved.parent).toMutableList()
        }
    }

    fun release() {
        scope.coroutineContext.job.cancel()
    }

    /**
     * Root tab IDs (from the single source of truth in [AutoBrowseTree]) plus Home's children,
     * so a logged-out client requesting a browse root gets AUTH_EXPIRED, not BAD_VALUE.
     */
    private fun rootTabIds(): Set<String> = buildSet {
        addAll(AutoBrowseTree.rootOrder)
        addAll(factory.homeChildIds())
    }

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
