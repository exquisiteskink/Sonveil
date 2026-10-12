# Sonveil — continuation plan

Working folder: `/home/omarchy/Work/Sonveil`
Release target: **1.3.15**, version code **35**; tag `v1.3.15`.
GitHub: https://github.com/exquisiteskink/Sonveil

## Where we stopped

P0 is checkpointed in `9b826af`; the square landscape artwork fix is in `0b39dff`. The first P1 slice is included in 1.3.15:

- Visible labels on existing Home / Artists / Search / Settings tabs, matching mini-player clearance.
- Six Home playlist previews and an explicit See all route to a complete keyed lazy playlist list.
- Loading/error/retry/empty states and Back on that destination; confirmed playlists retained after failures.
- Song title/artist use theme typography, with trailing duration. Existing playback/menu actions preserved.

Reference documents:
- `docs/design-review/REPORT.md`
- `docs/design-review/IMPLEMENTATION-PLAN.md`
- `docs/design-review/P0-IMPLEMENTATION.md`
- `docs/design-review/P1-IMPLEMENTATION.md`
- `RELEASE_NOTES_1.3.15.md` and `CHANGELOG.md`

## First steps tomorrow

1. Read this file and the two implementation records; run `git status`, `git log -5 --oneline`, and `git fetch origin --tags`.
2. Verify the published release: `gh release view v1.3.15`. Local signed assets are intended under `/home/omarchy/Work/Sonveil-releases/1.3.15/`; don't commit APKs or signing material.
3. Check `adb devices` before any phone interaction. The previous phone was a Galaxy Z Fold6, serial `RFCX71RCJKZ`.
4. Pick one small P1 package below, write failing regression tests, implement, run the full gate, review the diff, and checkpoint separately. Do not reinterpret “begin P1” as authorization for a navigation redesign or playback-policy change.

## Next P1 packages, in order

### A. Settings clarity and safeguards

- Audit existing settings semantics and selected/disabled states; use visible/readable labels and platform roles.
- Add offline-delete confirmation, keeping account isolation and download identity intact. Test cancel, confirmed delete, absent files, and failure paths before exposing it.
- Correct sleep-timer hints to match actual behavior. Test expiry, cancellation, and changed playback state.
- Inspect current source before changing anything; the design review is a baseline, not proof that every listed issue still exists.

### B. Component consistency

- Remove only genuinely redundant album overflow actions after confirming equivalent affordances remain.
- Standardize section/header rhythm and remaining row typography using existing tokens.
- Preserve cover identity, artwork atmosphere, semantic contrast, focus indication, and current playback actions.

### C. Large-library browsing

- Add only server-supported sort/filter/search behavior; never claim a loaded-page sort covers the entire library.
- Persist appropriate view/sort preferences without crossing account boundaries.
- Test empty/large libraries, pagination, changed filters, stale requests, errors/retry, and scroll restoration.

### D. Separate validated changes — not part of the first slice

- Library restructuring (possibly Home / Library / Search / Settings): prototype and validate before replacing Artists or changing routes.
- Song-tap playback policy: explicit product decision and playback regression tests first.
- Queue remove/move/undo: occurrence-aware operations for duplicate tracks; test shuffle/repeat, restoration, current item/position continuity, and undo. Never mutate queue lists directly from composables.

## Outstanding P0/device acceptance

- TalkBack exploration, seek announcements, selected states, hidden-sheet exclusion, and focus return.
- Physical keyboard/D-pad and IME interaction (previous seeking checks were ADB-injected).
- Corrected large-text landscape artwork view, 150% text, split-screen, and display scaling.
- Live Navidrome slow/offline/auth failures, duplicate pages, and account switch/sign-out during reads.
- Physical listening, background/lock-screen controls, external EQ, audio/DVC/ReplayGain regression checks.

**Phone settings:** original font scale is **1.0**, automatic rotation **1**, user rotation **0**. User strongly dislikes leaving accessibility stress-test settings enabled. Do not change text scaling or other device settings without agreement; restore any approved temporary changes immediately. P1 smoke tests used normal text size and changed no system settings.

## Verification gate

```sh
./gradlew :app:testDebugUnitTest :app:assembleDebug :app:lintDebug :app:lintRelease --max-workers=2
git diff --check
```

Pre-release baseline: **179 debug tests**, zero failures/errors/skips; lint **0 errors, 37 warnings, 5 informational findings** per variant. No coverage percentage measured. Release unit tests are not equivalent to the debug Robolectric suite because Compose's UI test host is debug-only in this project.

Local captures/logs: `/tmp/sonveil-p0-phone/` (may be ephemeral). Do not upload screenshots/XML containing private library data as release assets.

## Release guardrails

- Release builds remain unsigned in Gradle; sign privately with the existing Sonveil certificate and verify it against the preceding GitHub APK.
- Use a clean clone of the committed release revision (not a worktree) so embedded version-control metadata matches the tag. Follow the CPU-count settings documented in `docs/fdroid-submission.md`.
- Publish signed APK and SHA256SUMS to GitHub Releases, plus versioned changelog, release notes, Fastlane changelog, and updated local F-Droid recipe. No private keys/passwords in Git, notes, terminal output, or release assets.
- Existing F-Droid submission/reproducibility reports describe earlier releases. Do not claim new reproducibility or F-Droid publication without running its verification.
- Preserve ReplayGain, DVC, EQ/session behavior, authenticated artwork, and account-scoped storage. Unfinished P1 work is not advertised as shipped.
