# Artwork and motion review — 2026-10-01

Reviewed Sonveil's shared artwork components, phone/car media metadata, palette loading,
playback state subscriptions, lyrics requests, and album pagination against GitHub main
`a65702c`. The latest published release at review time is `v1.3.10`.

## Fixes

| Finding | Change |
| --- | --- |
| Failed image requests left blank covers; failed artist URLs never tried server artwork | Show a fallback icon while loading/after failure and try local override, external image, then server artwork. Remember requests and reset fallback selection when their sources or override revision change. |
| Car artwork caches used only cover ID and size | Separate files by hashed authenticated URL, and separate content URIs by account and a private random namespace. Reject URIs from another account/session. Capture credentials when building the requested URL. |
| Parallel car requests could race while publishing the same file | Deduplicate matching requests, allow unrelated downloads concurrently, publish completed files atomically, and open descriptors under the eviction lock. Reject failed, empty, oversized, and non-image responses. Limit each image to 8 MiB and the cache to 64 MiB. |
| Tracks started on the phone supplied HTTP artwork to car clients | Supply the same scoped local content URIs used by Android Auto browse results. |
| Every playback position tick refreshed Home and visible song rows | Observe derived favorite/current-song values and collect playback state with the Android lifecycle. |
| Palette animation frames rebuilt the theme's content tree; player backgrounds changed immediately | Read animation values inside Canvas drawing, and use the same animated background component on the app, player, and queue. Decode palette input at 256 pixels. |
| Initial album loading and pagination ran in competing coroutines | Give each selected folder one coroutine for initial loading and subsequent pages; switching folders cancels the old request and resets scroll position. |
| Lyrics requests continued after cancellation and could start fallback requests | Cancel the previous track's lyrics job and propagate cancellation through optional endpoint lookups. |

The artwork override helper now shares the common fallback renderer.

## Validation

Run with JDK 17 and Android SDK 35:

```sh
./gradlew :app:testDebugUnitTest :app:lintDebug :app:assembleDebug :app:assembleRelease
git diff --check
```

Results: **100 unit tests passed**, with no failures or skipped tests. Debug lint reports
**0 errors and 30 existing warnings**. Debug and unsigned release APKs build successfully.
The debug APK signature is verified with Android's `apksigner`.

New regression coverage includes cache/account isolation, concurrent matching requests,
parallel unrelated requests, complete-file publication, failure recovery, size limits
for known/chunked bodies, eviction, readable open files after eviction, and lyrics
cancellation without a fallback request.

Artifacts:

- `app/build/outputs/apk/debug/app-debug.apk` — installable test build.
- `app/build/outputs/apk/release/app-release-unsigned.apk` — unsigned release build.

## Device checks still needed

No Android device or emulator was connected. These are source/build/regression results;
frame times, actual artwork decoding/rendering, and car-host behavior were not measured.

- Check valid/missing/failed artist and album artwork, rapid track changes, and returning
  to a screen after the server recovers.
- Open and close Now Playing/queue while scrolling, then change tracks and theme.
- Switch album folders during a slow initial/page request; scroll through several pages.
- Start tracks from the phone and car; check browse, notification, and car Now Playing art.
- Switch servers/accounts with overlapping cover IDs, then relaunch and confirm current art.

Implementation references: [Compose performance guidance](https://developer.android.com/develop/ui/compose/performance/bestpractices),
[Coil 2.7 AsyncImage source](https://github.com/coil-kt/coil/blob/2.7.0/coil-compose-base/src/main/java/coil/compose/AsyncImage.kt).
