# P0 implementation

Implemented against the 1.3.14 source baseline (`d7bdd36`). This records the initial automated validation before the requested checkpoint and phone smoke test. No version bump, push, or release was performed.

## Implemented

- **Honest library states:** shared loading/error/empty components; Back and Retry remain available when album, artist, or playlist details fail. Home shelves load independently and retain confirmed content on failed reads. Library error messages do not display raw transport URLs or credentials.
- **Race-safe retrieval:** screen-scoped search and album-paging controllers guard query/folder generations, serialize pagination, preserve successful pages on failure, and advance offsets only after success. Search distinguishes pending/failed reads from genuinely empty results and retains its existing two-character threshold.
- **Accessible playback:** the flat seek control has a 56dp allocated touch area, progress/SetProgress semantics, disabled unknown-duration state, a visible focus outline, and Left/Right (five seconds), Home, and End keyboard actions. Pause remains available while playback is buffering, including before the media session connects. Sustained buffering is announced politely after 400ms; position ticks are not live announcements.
- **Readable roles:** opaque neutral metadata roles are contrast-solved rather than alpha-dimmed. Light artwork washes retain their hue but have bounded saturation/luminance. Metadata regressions cover extreme solid artwork, static fallbacks, wash composites, and intermediate surface blends. Error text uses error-container/on-error-container pairs on login and the expanded player.
- **Usable player bounds:** portrait metadata scrolls; wide/short layouts use two panes. Transport and queue access remain outside the metadata scrolling region. Lyrics/favorite controls allocate 48dp. Player/queue/mini-player focus ownership and semantics follow the active surface; inactive sheets and underlying navigation are excluded while the player is open.
- **Content clearance:** the mini-player reports its measured height, and library/settings/download/favorites screens consume a shared content inset. Artist grids are adaptive rather than fixed at four columns. Row/card press feedback and focus indication are restored.
- **Metadata honesty:** technical labels explicitly describe the **Source file**, not the current transcoded stream, DAC format, or bit-perfect output.

The existing artwork atmosphere, glass surfaces, gold fallback, covers, and flat scrubber remain. ReplayGain, DVC, EQ attachment, the audio sink/service, artwork authentication, and account-scoped storage implementations were not changed. `PlayerController` changes concern observable playback state and the existing play/pause action only.

## Automated verification performed

```sh
./gradlew :app:testDebugUnitTest :app:assembleDebug :app:lintDebug :app:lintRelease --max-workers=2
```

- **171 debug JVM/Robolectric tests passed**, with zero failures, errors, or skips.
- **Debug APK built:** `app/build/outputs/apk/debug/app-debug.apk`.
- **Debug and release lint passed:** each reports 0 errors, 37 warnings, and 5 informational findings. Remaining findings include dependency update notices, existing manifest/network exposure warnings, unused resources, and state-boxing suggestions; they have not all been remediated by this UI change.
- `git diff --check` passed.
- New regressions exercise ignored cancellation/stale search and folder pages, failure/retry offsets, cancellation propagation, sanitized retained reads, color roles, seeking semantics/keyboard actions, 200% text in a 320×480dp player viewport, measured large-text mini-player height, hidden-sheet semantics, delayed buffering status, and pending-autoplay cancellation.

Robolectric is automated JVM UI testing, not device instrumentation. No coverage percentage was measured; release unit tests and release APK packaging/signing were not run for this change.

## Phone smoke test and landscape follow-up

P0 was checkpointed in `9b826af`. An in-place debug update was installed on the connected Galaxy Z Fold6 after confirming the signing certificate matched; app data was retained.

- Observed portrait light/dark player, queue navigation, and ADB-injected keyboard seeking (1:26 → 1:31 → 1:26). These are not physical-keyboard or TalkBack listening tests.
- The 200% landscape stress test exposed artwork squeezed into a rectangle. The follow-up now sizes square artwork against the measured pane after transport/queue allocation.
- The new regression failed before the fix; the full suite afterward passed **172 tests**, zero failures/errors/skips. Debug APK and debug/release lint tasks succeeded; `git diff --check` passed.
- The follow-up APK is installed. Normal-size portrait player was captured successfully. A corrected 200% landscape device capture was not completed before the user requested normal text size.
- Original phone settings were restored and font scale verified at **1.0**: automatic rotation enabled, user rotation 0. No display density change was made.
- Captures and logs are local under `/tmp/sonveil-p0-phone/`; they are not committed.

## Remaining acceptance checks

**P0 device/accessibility sign-off is still pending.** These automated results are not a WCAG certification or a visual/performance sign-off.

| Check | Status |
| --- | --- |
| Current-device light/dark screenshots and visual comparison | Portrait player/queue smoke-tested; comprehensive visual comparison pending |
| TalkBack exploration, seek announcements, and focus return | NOT RUN |
| Physical keyboard/D-pad and IME interaction | ADB-injected seeking passed; physical keyboard and IME pending |
| 100/150/200% system text, display scaling, split screen, and landscape | Normal portrait and 200% stress test observed; landscape fix covered in Robolectric, corrected stress-test device capture pending; split-screen/150% pending |
| Live Navidrome slow/offline/auth/error and duplicate-page behavior | NOT RUN |
| Physical playback, background/lock-screen controls, external EQ, and audio regression listening | NOT RUN |
| Sign-out/account switch during in-flight reads and playback | NOT RUN |

Library navigation redesign, queue editing/undo, song-tap policy changes, offline album identity/deletion safeguards, and device-output diagnostics remain outside this P0 implementation.
