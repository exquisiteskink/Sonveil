# Sonveil — design systems and implementation track

Baseline: `d7bdd36` / 1.3.14, reviewed 2026-10-11. This plan translates the separate UX and visual track findings into coding-agent work. No production changes or new build/device checks were made for this review. Suggested component/file names marked “new” are proposals, not existing APIs.

## Guardrails

- Preserve Sonveil's artwork atmosphere, restrained glass chrome, static gold fallback, flat seek bar and current playback/queue restoration behavior.
- Do not alter ReplayGain, DVC guard, EQ attachment, audio session identity, stream authentication, signed art capabilities or account-scoped caches as a visual refactor.
- UI buffering state must observe existing Media3 behavior, not cause new prepare/play calls. Queue editing needs its own audited controller work and regression tests, not direct list mutation in a composable.
- Keep dependencies and Compose/Media3 APIs compatible with the repo's pinned versions. Do not apply copied web CSS or assume a SwiftUI/iOS implementation.
- No analytics, AI discovery service, metadata rewrite or server-side library edits are authorized by this plan. Use consented research and synthetic fixtures.

## Work package 1 — semantics and allocated control sizes (P0/P1; small to medium)

Existing files:

- `ui/player/Hud.kt::FlatSeekBar`: normalized progress range, set-progress accessibility action, focus and Left/Right keyboard behavior. The flat Canvas can remain. A custom seek control must support both non-drag pointer input and non-pointer input. For duration=0, expose disabled/unavailable state rather than allowing a 1ms seek.
- `ui/player/NowPlayingChrome.kt::MetaRow`: allocate 48dp for lyrics/favorite controls rather than 36dp; keep glyphs 22dp. Separate technical metadata from these buttons to avoid narrow-window crowding.
- `ControlsDeck`: state descriptions “Shuffle on/off”, “Repeat off/all/one”; button roles for custom Box play controls. Preserve next/previous hit sizes. Keep Pause accessible during buffering if playback intent is active.
- `ui/components/Components.kt`: contextual action names (“More actions for [song]”), heading semantics, nonduplicated cover/title announcements; 48dp chips and header actions; pressScale must retain a visible indication/focus state rather than `indication=null`.
- `ui/album/AlbumsScreen.kt::FolderChip`: selected semantics and explicit allocated minimum size.
- `MainActivity.kt` / `NowPlayingHost.kt`: validate focus traversal through translated/alpha-hidden sheets and nav. Inactive sheet surfaces must not retain actionable semantics/focus. Move focus into the opened player/queue and restore it on close.

Illustrative semantics adapter (not a complete patch; imports, keyboard support, localization and unknown-duration handling remain required):

```kotlin
// In FlatSeekBar, wrap the existing drawing/pointer logic.
val canSeek = durationMs > 0L
val fraction = if (canSeek) {
    (positionMs.toFloat() / durationMs).coerceIn(0f, 1f)
} else 0f
val accessibleModifier = modifier.semantics {
    contentDescription = "Playback position"
    if (canSeek) {
        progressBarRangeInfo = ProgressBarRangeInfo(fraction, 0f..1f)
        setProgress { target ->
            onSeek((target.coerceIn(0f, 1f) * durationMs).toLong())
            true
        }
    } else {
        disabled()
    }
}
// Apply accessibleModifier.focusable(enabled = canSeek) to the Canvas.
// Handle focused Left/Right/D-pad separately; clamp to the same range.
```

Acceptance: TalkBack can seek without touch dragging, announce repeat state and invoke song actions. Tab/D-pad traversal is visible and never enters hidden sheets. Target-bound assertions verify >=48dp allocated actions and no overlapping adjacent hit expansion. Preserve pointer drag/cancel preview behavior.

## Work package 2 — contrast roles and foundational tokens (P0; medium)

- Extend `data/player/ArtColorScheme.kt` / `PaletteExtractor.kt` with readable neutral text roles derived/validated against the actual surface family. Reuse `ArtColorMath`; do not create a parallel accent pipeline.
- Add `ui/theme/DesignTokens.kt` (new) for spacing, radius and component size constants. Let `Theme.kt` typography be the actual source of styles instead of local `fontSize`/weight everywhere.
- Replace arbitrary alpha text for meaningful metadata with named roles. Keep alpha for decorative borders, scrims and genuinely disabled controls where appropriate.
- `LoginScreen.kt`: replace `Color(0xFFFF8A80)` with semantic error/onErrorContainer, with an actionable message. Use contrast-safe focused outlines for Login/Search fields.
- Adopt explicit component surface styles: quiet transparent list, raised player, glass card, opaque modal. Avoid increasing every border or wrapping every row in a card.

Acceptance: expand `ArtColorSchemeTest.kt` to neutral/error/focus/selected roles and representative composites. Add screenshot/contrast checks for gradient extrema and transition frames across both themes. Existing accent tests are useful but insufficient for rendered UI certification. All ordinary enabled text should meet 4.5:1, essential graphical controls 3:1.

## Work package 3 — honest screen/search state (P0; medium)

- Add `ui/components/ScreenState.kt` (new): loading, success-empty, success-content, retained-content-with-error, retryable-error. `ErrorText` can remain a primitive, not the whole interaction.
- Add `ui/search/SearchViewModel.kt` (new, or an equivalent tested state holder). Own query, query generation, suggestions, result pages, pending/error states and independent category offsets. Use latest-query cancellation plus generation checks on *all* result writes; More results must have a mutex/active-request guard.
- Keep the previous results only if explicitly labeled as stale/updating; never present them as results for the new query. Show true No results only after the corresponding request succeeds.
- Preserve the 180/120ms existing typing debounce behavior initially, but avoid two unconditionally duplicated result presentations: one recommendation is suggestions while focused/typing, full typed-category results after submission or selection. Validate before removing either path.
- `HomeScreen.kt`: per-shelf results instead of converting every exception to an empty list; genuine empty server gets helpful starter actions, not “Could not load library”. Keep successful shelves during refresh.
- `AlbumScreen.kt`, `PlaylistScreen.kt`, `ArtistScreen.kt`: render the back/title shell before branching on loading/error. `AlbumsScreen.kt`: footer retry for page failures, preserve existing albums/offset, do not permanently disable more on transient failure.
- `PlayerController.kt::PlayerUiState`: expose read-only playbackState/playWhenReady/buffering state. `NowPlayingChrome.kt`: delayed indeterminate feedback for stalls and localized safe retry errors; don't announce position ticks.

Acceptance tests: empty vs 401/network/TLS/timeout distinctions; slow query A then fast B; More(A) completes after query switches to B; repeated More taps; cancelled navigation; partial Home shelf failure; album page 2 failure then retry; slow artwork must never block playback controls.

## Work package 4 — responsive player and shared content insets (P0; medium)

- `NowPlayingHost.kt::NowPlayingPage`: compute art bounds only after reserving close, transport and queue controls. In short/large-text windows use scrollable metadata; in wide windows use cover/lyrics left and controls/metadata right. Keep controls in the lower reachable region on phones without arbitrarily pinning them under a gesture inset.
- `MetaRow`: format summary separate from lyrics/favorite actions. Long title/album wraps and remaining content stays reachable.
- `MainActivity.kt`: provide actual mini-player obstruction to content via shared layout/inset state. Current 64/88dp padding and mini-player's 59dp height are unrelated magic constants across screens; future adaptive heights make those assumptions worse.
- `ArtistsScreen.kt`: replace `GridCells.Fixed(4)` with adaptive minimum tiles (start at 112–140dp, validate) and larger text policy. `ArtistScreen.kt::ArtistAlbumsScreen` already uses `GridCells.Adaptive(140.dp)` as a local precedent.

Acceptance: 320dp compact width, short landscape, Fold cover/unfolded, tablet and split screen at 100/150/200% text. No clipped transport/queue controls, last rows and focused fields scroll above mini-player/IME/system insets. Capture light/dark pairs on matching artwork.

## Work package 5 — rows, navigation and large-library discovery (P1; medium to large)

- `Components.kt`: a shared TrackRow layout with Browse/Album/Queue variants, two-line identity, trailing duration and optional identity/quality detail. Use typography roles and tabular duration if the font supports it. Queue variants must not implicitly add another copy to themselves.
- `AlbumListRow`: no overflow for a sole Open album command; artist/year identity in browse context, year-only can remain appropriate inside a known artist.
- `SectionHeader` and ScreenTopBar: common gutters and heading semantics; clickable section rows at least 48dp. Reduce redundant separators, not all meaningful structure.
- `MainActivity.kt`: visible nav labels first. Then prototype Library navigation containing existing Albums/Artists plus dedicated full Playlists/Favorites/Downloads destinations. Keep old deep-link routes and saved tab/scroll state; use adaptive navigation rail when window size warrants it.
- `HomeScreen.kt`: bound playlist preview and provide See all; optionally user-selected pinning after research. Keep art shelves and list controls familiar. Move to LazyColumn if growing sections would otherwise eagerly compose long content.
- `AlbumsScreen.kt`: supported sort choices, persisted per-account view/filter/scroll; artist browse in-list search and alphabet jump. Do not implement fake local sorting of only a downloaded page while labeling it whole-library sort.
- `SearchScreen.kt`: visible category scopes and one predictable song action policy. Preserve ability to browse an album without accidentally playing it. Genre opens browse; a separately labeled Shuffle action must disclose partial queues or paginate server data.

Acceptance: fixture libraries with thousands of albums/artists, many playlists, long duplicate titles, compilations, multiple folders and missing art. Navigation Back restores query/view/scroll, with no leakage across account changes. Avoid dependence on Navidrome-private APIs for core browsing when documented Subsonic APIs suffice.

## Work package 6 — queue agency and trusted audio information (P1/P2; medium to large)

- `NowPlayingChrome.kt::QueuePage`: QueueRow remove/undo, then Move up/down, then optional reorder drag with equivalent accessible custom actions. Show current context and queue count/remaining duration as secondary information.
- `PlayerController.kt`: add occurrence-aware move/remove APIs, synchronize Media3 timeline/state/persistence and preserve live AudioTrack/position. Do not key edits by media ID when two occurrences of the same song are present. Add controller tests before enabling UI actions.
- `NowPlayingHost.kt` / `SubsonicModels.kt`: label existing codec/bit-depth/rate values as **Source file**. They are read from Song metadata and do not establish the active decoded stream or DAC output. Show selected streaming policy separately. Add actual stream/output facts only when real Media3/platform data is available; never imply “bit-perfect” from server tags.
- Optional detail sheet: source format, selected original/transcode policy, output name, ReplayGain/EQ state. Keep deep technical detail progressively disclosed so transport controls remain uncluttered.

Acceptance: duplicate song occurrences, shuffle/repeat variants, next item prefetch, crossfade, restart restoration, remove current vs upcoming, undo after timeline changes, and no unintended restart/seek of current playback. Transcoded stream never appears as a verified FLAC/DAC path purely because the source file is FLAC.

## Work package 7 — offline trust, setup help and settings (P1/P2; small to medium)

- `SettingsScreen.kt`: confirm Clear offline downloads with what/size/context; make radio/toggle rows single semantic controls using selectable/toggleable and null child handlers, not duplicate focus stops. Clarify Track/Album ReplayGain for their listening contexts; keep external EQ restrictions transparent.
- `DownloadsScreen.kt`: group using album identity, not only the album title; shared top bar; initial loading vs truly no downloads; durable download state and available original files. Temporary playback cache is not an offline guarantee.
- `AlbumScreen.kt`/`PlaylistScreen.kt`: status should refer to the actual collection being downloaded, not infer completion from one global phase; clarify pending/Wi-Fi-paused/failed/available states without color alone.
- `LoginScreen.kt`: IME Next/Done and keyboard traversal; optional server-address help and safe error categories. Never auto-accept invalid certificates or remove explicit insecure-HTTP consent.
- Settings advanced subpages / output profiles are a later progressive-disclosure refactor. Correct the sleep-timer hint now: current seek code does not clear the timer, and history explicitly records its preservation across seeks/automatic transitions. Verify other clearing behavior before rewriting the hint.

## Suggested coding-agent coordination

These are proposed assignments, not agents launched in this session:

1. Foundation owner: tokens, contrast and shared accessibility contract. Own `Theme.kt`, `Components.kt`; publish component APIs before consumer work.
2. Search/state owner: query state, request safety, shared ScreenState consumers; no concurrent edits to Components without foundation handoff.
3. Player owner: semantic seek, status and responsive sheets; separate review for controller-state fields versus queue-edit implementation.
4. Library/navigation owner: Library prototype, adaptive grid, browse controls and bounded Home previews after token/state contracts settle.
5. Validation/review owner: independent contrast, semantics, screenshot and regression review. Integration owner resolves Theme/Components/MainActivity overlaps.

Parallelize isolated state-holder tests and token/contrast investigation first. Integrate common components before simultaneously changing every screen. Deliver small reviewable PRs in the ordered packages above; P0 packages 1–4 precede IA/queue expansion. Confirm the Library and search-tap decisions with task testing before finalizing navigation behavior.
