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
| The media session bitmap loader could not decode local content URIs | Use Media3's cached DataSourceBitmapLoader for notification/session artwork; verify real provider decoding on a device. |
| Every playback position tick refreshed Home and visible song rows | Share one lifecycle-aware playback subscription across the UI and observe derived favorite/current-song values. |
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

Results: **100 unit tests and 2 device tests passed**, with no failures or skipped tests.
Debug lint reports **0 errors and 32 warnings** (the original 30 plus two dependency-update
warnings for the new Android test libraries). Debug and unsigned release APKs build successfully.
Debug and signed review release APK signatures are verified with Android's `apksigner`.

New regression coverage includes cache/account isolation, concurrent matching requests,
parallel unrelated requests, complete-file publication, failure recovery, size limits
for known/chunked bodies, eviction, readable open files after eviction, and lyrics
cancellation without a fallback request.

Artifacts:

- `app/build/outputs/apk/debug/app-debug.apk` — installable test build.
- `app/build/outputs/apk/release/app-release-unsigned.apk` — unsigned release build.
- `app/build/outputs/apk/release/Sonveil-1.3.10-review.apk` — signed release installed on the phone.

## Physical device testing — 2026-10-01

Test device: Samsung Galaxy Z Fold6 (`SM-F956U1`), Android 16, folded display at 968 × 2376.
The test APK's certificate matched the installed 1.3.10 build.

Initial authenticated checks confirmed visible artwork in Home and expanded Now Playing,
queue navigation, and repeated Home/Artists scrolling. Device logs exposed a session and
notification regression: `SimpleBitmapLoader` rejected the new content URIs with
`MalformedURLException: unknown protocol: content`.

The session now uses `CacheBitmapLoader(DataSourceBitmapLoader(context))`. Both device
regression tests pass: decoding a real PNG through the scoped artwork content provider,
and rejecting an artwork URI belonging to another account. Playback state collection is
shared across the UI, and successfully loaded images remove the underlying placeholder
icon to reduce extra drawing.

After signing back in, final release checks confirmed valid Home/album/Now Playing artwork,
visible placeholders for records without covers, playback progress, pause/resume, previous/next
controls, and rapid changes between tracks from different albums. Notification inspection
confirmed a decoded bitmap (`81 × 81`) in `android.largeIcon`. No app-process fatal crash,
playback exception, failed bitmap load, or unsupported-content-URI diagnostic was found
in the final log sample. Album-list scrolling advanced beyond the first 60-record page
with images and missing-art placeholders intact. Artist-grid images were visible; switching
to Dark updated the player background and text correctly, then Match system was restored.
The app was left signed in on Home with playback paused. A car/Android Auto host has not
been tested.

### Frame timing

On the signed release, three repeated cycles of Home/Artists scrolling, queue scrolling,
and opening/closing Now Playing rendered 4,945 frames: 92 janky frames (**1.86%**),
median **8 ms**, 95th percentile **12 ms**, 99th percentile **16 ms**; GPU 99th percentile
**9 ms**, with no slow bitmap uploads. This is a short observation on the folded display
with playback paused, not a guarantee that every screen is stutter-free. The pre-change
sample used a different queue/theme, so it does not establish a performance improvement.

### Device-test cleanup

The device test runner removed this phone's local Sonveil app data along with the apps.
The fixed release was reinstalled and the user signed in again; prior local settings, queue,
and downloads were not restored. The server library and playlists were not modified.

AGP 8.7.3's default test runner uninstalls both the target and test app after
`connectedDebugAndroidTest`. The project now sets
`android.injected.androidTest.leaveApksInstalledAfterRun=true` to preserve installed apps
after future runs. Use a disposable emulator for automated tests; on a personal phone,
verify matching signatures and use explicit `adb install -r` / `am instrument` commands
without uninstalling the target app.

The two tests only create and remove a uniquely named artwork override and change
credentials in memory; they do not save test credentials or clear application storage.

### Remaining checks

- Check artwork recovery after a live server failure, including artist-image fallback.
- Check the unfolded display and extended playback.
- Switch album folders during a slow initial/page request (the connected library only
  exposed the unfiltered album list).
- Start tracks from a car; check Android Auto browse and car Now Playing art.
- Switch servers/accounts with overlapping cover IDs, then relaunch and confirm current art.

Implementation references: [Compose performance guidance](https://developer.android.com/develop/ui/compose/performance/bestpractices),
[Coil 2.7 AsyncImage source](https://github.com/coil-kt/coil/blob/2.7.0/coil-compose-base/src/main/java/coil/compose/AsyncImage.kt),
[Media3 1.5.1 DataSourceBitmapLoader](https://github.com/androidx/media/blob/1.5.1/libraries/datasource/src/main/java/androidx/media3/datasource/DataSourceBitmapLoader.java),
[AGP test cleanup option](https://issuetracker.google.com/issues/295039976).
