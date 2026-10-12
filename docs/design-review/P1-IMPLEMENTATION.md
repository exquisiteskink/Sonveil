# P1 implementation — first slice

P0 and its measured-pane artwork follow-up are checkpointed separately in `9b826af` and `0b39dff`. This initial P1 slice is included in the 1.3.15 release preparation. Remaining work and restart instructions are preserved in `../../CONTINUE-TOMORROW.md`.

## Implemented

- **Recognizable navigation:** Home / Artists / Search / Settings retain their routes, selection, saved-state navigation, and icons, with always-visible text labels. Icons no longer duplicate the spoken text label. The shared navigation height is 80dp and the mini-player uses the same height for clearance.
- **Bounded Home playlists:** show the first six server-returned playlists rather than composing the full collection as Home chips. An explicit “See all” action opens a dedicated Playlists destination.
- **Complete playlist browsing:** a keyed lazy list exposes the full collection, preserves Back and measured mini-player clearance, distinguishes loading/empty/error, offers retry, and retains confirmed playlists after a failed retry. Uses the existing authenticated `getPlaylists` API and sanitized `readKeeping` helper; no server mutations.
- **Consistent song identity:** title and optional artist use existing theme typography; duration is trailing, alongside the identity column, rather than a third line. Rank, cover, favorite marker, contextual overflow actions, and existing tap/playback behavior are preserved.

## Validation

```sh
./gradlew :app:testDebugUnitTest :app:assembleDebug :app:lintDebug :app:lintRelease --max-workers=2
```

- **179 tests passed**, zero failures/errors/skips.
- Debug APK built; debug/release lint each reports **0 errors, 37 warnings, 5 informational findings**.
- Seven new Robolectric component tests cover labels/selection/callback, bounded preview, empty preview, reaching playlist 100, Back, failure vs. empty, retained content, and trailing song duration.
- `git diff --check` passed. Coverage percentage was not measured.
- Updated debug APK installed in-place on the connected Galaxy Z Fold6; app data retained. At the existing **1.0 font scale**, observed labeled navigation and mini-player spacing, six Home chips, all nine server playlists in the dedicated list, playlist detail with trailing durations, and Back to Home.
- Phone screenshots/XML remain local under `/tmp/sonveil-p0-phone/p1-*`. No font, rotation, density, theme, accessibility-service, or connectivity settings were changed for P1 testing. Playback was not started by the test actions.

This is a narrow portrait smoke test, not full TalkBack/physical-keyboard/large-text/audio acceptance. P0 acceptance gaps remain documented separately.

## Remaining P1 work

- Settings semantics, offline-delete confirmation, and accurate sleep-timer hints.
- Remaining section/header/row consistency and redundant album overflow cleanup.
- Supported large-library sort/filter/search and persisted browsing preferences.
- Library-navigation restructuring and song-tap policy require separate validation before changing existing behavior.
- Queue remove/move/undo requires a separate occurrence-safe, playback-aware change with duplicate/shuffle/repeat/restoration tests.
