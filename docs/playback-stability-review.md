# Playback stability review

## Findings

Sonveil uses the same Media3 playback service for the phone, notification, and Android Auto. Before this change its audio source resolved authenticated Navidrome streams directly into OkHttp, with only a 15–50 second memory buffer. Saved offline downloads were separate; streaming did not save reusable audio bytes. Each queue locator includes a nonce, and Navidrome stream URLs receive fresh authentication salts, so caching by either complete URL would miss on retries and repeats.

The service already has network wake locks and bounded transient-error retry. However, retry replaced the current queue item with a new locator, causing the playlist/item callbacks to clear the retry budget. Authentication already resolves again on each upstream open; this replacement was unnecessary.

The reported driving failure cannot be attributed conclusively to Android Auto or connectivity from the available code. Artwork loading and audio loading are separate paths; real device testing during connectivity changes remains necessary.

## Comparison with other audio apps and Media3

| Source | Approach | Sonveil decision |
| --- | --- | --- |
| [Android Media3 network stack documentation](https://developer.android.com/media/media3/exoplayer/network-stacks) | One shared `SimpleCache`, bounded least recently used eviction, and `CacheDataSource` in front of the network source. | Use a process singleton cache for both primary and crossfade players, in the Android temporary cache directory. |
| [Tempo streaming cache source](https://github.com/CappielloAntonio/tempo/blob/main/app/src/main/java/com/cappielloantonio/tempo/util/StreamingCacheDataSource.kt) | Wraps Media3 cache reads and removes partial entries whose content length remains unknown when closed. | Retain valid partial byte spans; they can help seek/retry after a coverage gap. Keeping partial spans does not imply an entire song is available offline. |
| [Tempo API cache source](https://github.com/CappielloAntonio/tempo/blob/main/app/src/main/java/com/cappielloantonio/tempo/subsonic/utils/CacheUtil.java) | Separate online/offline HTTP cache policies for API responses. | Keep song byte caching independent from UI/library metadata caching, so an audio cache failure cannot block browsing. |
| [Symfonium offline/cache guide](https://docs.symfonium.app/wiki/other/offline-media-cache-and-downloads/) | Distinguishes provider media caches from exported downloads and allows caching at song, album, artist, and playlist scope. | Keep temporary playback bytes separate from explicit saved downloads, and apply the source cache to every streaming entry point. |
| [Media3 CacheWriter source](https://github.com/androidx/media/blob/release/libraries/datasource/src/main/java/androidx/media3/datasource/cache/CacheWriter.java) | Worker-thread caching skips existing spans and supports cancellation. | Best effort look-ahead for the next two queue songs, respecting shuffle/repeat and cancelling when playback pauses, fails, stops, or the queue changes. |

## Artwork and Android Auto review

Phone playback previously fetched an album cover separately from the song metadata used by the session and car. It also retained the previous cover during transitions. Playback now uses a shared album artwork URI: the provider resolves the current song’s album ID through getAlbum and uses that album’s server coverArt ID. Now Playing (including compact players), palette colors, notifications, and Android Auto all use this path. Album IDs are cached within the account to avoid repeated metadata lookups. It clears the old cover when the album changes and never substitutes track artwork when the album cover is missing. Library album cards use the same server album coverArt field; artist portraits remain separate. Client-only album overrides are no longer applied.

Media3 1.5.1's [CacheBitmapLoader](https://github.com/androidx/media/blob/1.5.1/libraries/session/src/main/java/androidx/media3/session/CacheBitmapLoader.java) retains its last future even after failure. Its default [BitmapLoader](https://github.com/androidx/media/blob/1.5.1/libraries/common/src/main/java/androidx/media3/common/util/BitmapLoader.java) also prefers embedded image bytes over a supplied URI. The replacement session loader prefers the server URI, shares pending/successful requests, and permits another attempt after failure or cancellation. It does not continually retry a missing cover without a new load request.

[DSub's car browser](https://github.com/daneren2005/Subsonic/blob/master/app/src/main/java/github/daneren2005/dsub/service/AutoMediaBrowserService.java) exposes the artist index and artist albums under its library. Sonveil now exposes Library → All artists → artist → albums → songs. Recently added remains under Library, preserving its existing IDs. [Google's content hierarchy guidance](https://developer.android.com/training/cars/media/create-media-browser/content-hierarchy) limits root tabs to four and says car clients do not paginate; the accepted car client receives the full artist index even when it supplies a positive page size.

## Connected phone observations

The connected Galaxy Z Fold 6 was running Sonveil 1.3.12. Retained app logs contained no playback network exception, crash, or ANR. They showed audio focus loss at 10:20:56 on 2026-10-10, followed by Android stopping the idle PlaybackService at 10:22:02. Historical process exits were background/system cleanup or task removal, without a recorded crash. These records do not establish the cause of the earlier driving failure.

## Changes

- A 256 MiB least recently used temporary audio cache under `cacheDir/playback-audio`. Android may reclaim it; it is not a permanent download or offline guarantee.
- Cache identity includes server, account/auth mode, song, and requested bitrate. Nonces and authentication salts do not change byte identity. Identity components are length delimited and hashed.
- Credentials and cache identity are snapshotted together at source open. An account switch cannot save another account's audio under the original account's key.
- Upstream sources report the opaque Sonveil locator even after a signed CDN redirect. The cache index therefore does not persist authenticated stream/redirect URLs.
- Once the primary player has a 30 second cushion (or the remainder of a shorter song is fully buffered), one background worker preloads up to 32 MiB for each of the next two songs. The audible source does not wait for a prefetch cache lock. Playback state, buffer health, and queue changes cancel/recompute look-ahead.
- The primary in-memory buffer now targets 30–120 seconds, prioritizing buffered duration. Startup still needs only one second; recovery waits for three seconds to reduce repeated short stalls.
- Transient retries prepare the existing queue item, preserving position, audio session, and retry budget while obtaining fresh authentication for uncached bytes.
- If cache initialization or later cache reads fail, playback falls back to network streaming.

## Validation

Unit tests cover stable keys, server/account/song/quality separation, delimiter collision protection, negative bitrate normalization, and identity privacy. Instrumentation tests cover production source resolution, signed redirect privacy, account-switch isolation, replay/seek without network, and least recently used eviction.

- Debug app and instrumentation APKs build successfully.
- Installed the matching-certificate debug update on the connected Galaxy Z Fold 6 with an in-place update; no app data was cleared.
- All 19 focused instrumentation tests passed: production cache replay/seek and account isolation, redirect privacy, eviction, server artwork precedence and failure recovery, artwork URI access checks, artist browse structure, and stream locator/installed Auto trust checks.
- Removed the instrumentation package and reopened Sonveil; startup logs contained no playback/crash diagnostics.
- Prior playback-stability unit suite: 126 tests passed, zero failures/errors.
- Prior playback-stability Android lint: zero errors, 34 warnings.
- Final diff whitespace check passed.

The device cache tests simulate network failure through a fake upstream source. A real Navidrome connection drop and Android Auto vehicle/head-unit UI have not been exercised in this session.

For a device check: start a queue, allow buffering/look-ahead, disconnect the network, seek within cached audio, and advance into a prefetched song. Restore the network and verify later uncached tracks resume. Repeat with shuffle, pause/resume, queue replacement, account changes, screen off, and Android Auto. Check that the current song's artwork changes at each transition independently of audio cache state.

## Album-cover follow-up verification

Now Playing, compact players, palette extraction, and session/Auto playback now use the album-cover provider path. The provider queries the server’s album coverArt field once per cached account/album and shares the downloaded image bytes across these consumers. It does not replace queue items or wait on artwork in the audio-loading path. Cover and album URI capabilities have separate signing domains.

The updated debug build is installed on the connected phone. Both APK builds passed, all 130 unit tests passed, and Android lint reported zero errors (34 warnings). All 16 focused device tests passed, including a local mock server supplying an album cover different from the song cover, phone/session image agreement, recovery after a failed album lookup, and rejecting a cover capability repurposed as an album lookup. The test package was removed and Sonveil reopened afterward.
