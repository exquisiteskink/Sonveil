# Sonveil — product design and UX improvement report

**Date:** 2026-10-11. **Reviewed baseline:** `d7bdd36` / Sonveil 1.3.14.

## Scope and process

Reviewed current Android/Compose source, four historical repository screenshots, existing artwork/playback review notes, palette regression-test source and targeted public guidance. No independent-agent tool was available: the session assistant conducted role-separated UX and visual assessments, then an implementation pass. Parallel file/source research is not a claim of independent agents or independent review. No production UI/audio code was changed and no new build, device, accessibility or user-study results are claimed.

Artifacts:

- [UX research and heuristics](UX-HEURISTICS.md): severity-ranked findings, all ten Nielsen heuristics, platform/accessibility interpretation and research tasks.
- [Visual design and polish](VISUAL-POLISH.md): distinct visual assessment, compositing caveats, token proposals and screenshot matrix.
- [Design systems and implementation plan](IMPLEMENTATION-PLAN.md): file-level work packages, an illustrative seek-semantics adapter, acceptance tests, parallel work boundaries and playback safeguards.

## 1. Executive summary

**Sonveil has an identity worth refining, not replacing.** Its artwork-derived atmosphere, restrained gold fallback, glass player chrome, large covers, simple flat scrubber and calm transitions already communicate a focused listening product. The code also has meaningful foundations: shared components, typography and motion definitions, a full art-derived Material color scheme, contrast tests for accent roles, lifecycle-aware playback subscriptions, album pagination and careful audio/account isolation work.

It serves audiophile/Navidrome users well in its core intent: original files by default, ReplayGain, output-specific EQ, multi-disc ordering, server album art, offline downloads and Android Auto. The weaker layer is interaction clarity around those capabilities: search failure looks like no matches; playback does not distinguish paused from buffering in UI state; album/library entry points are indirect; queue rows do not provide basic editing; and source-file format labels can be mistaken for the active stream/output path.

Visual craft is constrained less by lack of decoration than by repeated local font/spacing decisions, low-alpha secondary text, overly uniform section rules and a fixed player stack. Current light-mode polish cannot be certified from the available dark screenshots. The right direction is **a trustworthy, readable, artwork-led library and player**, not generic white cards, gratuitous gradients, a waveform revival or speculative AI discovery.

### Design decisions to preserve

- Album art remains the hero; no fake thumbnails or invented metadata.
- Keep the artwork wash and gold fallback; restrict decoration rather than remove personality.
- Keep simple direct playback controls and flat progress.
- Preserve account isolation, credentials, server artwork and existing stream/audio behavior.
- Prefer platform semantics and adaptive layouts over recreating standard interaction machinery.
- Large libraries must remain efficient without silently sorting/filtering only a partial page.

## 2. Prioritized recommendations

Priority: **P0** before design/accessibility sign-off; **P1** next usability iteration; **P2** subsequent refinements after validation. Effort is relative (Small/Medium/Large), not a delivery estimate. Severity describes the observed UX risk, not proof of a production crash.

### 1. Separate loading, empty, stale and failed states

**P0 · High severity · Very high impact · Medium effort**

- **Problem:** Search catches network failures into empty `SearchResult3`; no-results can appear during typing/loading. Its More coroutine can write into a changed query and has no repeated-request guard. Home shelves similarly convert failure into empty data. Album/playlist/artist loading may omit the normal back/title shell; album page failures suppress further loading without a footer retry.
- **Why:** Visibility of status, error recovery and time-to-first-value. A self-hosted server problem must not look like a missing music collection.
- **Change:** Explicit query/shelf/page state, true empty only after success, retain valid data, localized actionable retry, persistent screen chrome and request identity/cancellation.
- **Implementation:** `SearchScreen.kt` plus a proposed `SearchViewModel`; `HomeScreen.kt`, `AlbumsScreen.kt`, `AlbumScreen.kt`, `PlaylistScreen.kt`, `ArtistScreen.kt`; new shared `ScreenState` components. Test slow A/fast B, stale More(A), duplicate paging taps, partial shelf failures and page retry.

### 2. Make the player accessible and expose playback intent

**P0 · High severity · Very high impact · Small–Medium effort**

- **Problem:** `FlatSeekBar` is a pointer-only Canvas with no range/action semantics. Player state has playing/error but no buffering/play intent. Shuffle/repeat use static descriptions and color to indicate state; custom controls need focus/role treatment.
- **Why:** Name/role/value, keyboard access and system-status visibility. Blind users must be able to seek; listeners need to know whether playback is paused or waiting for the server.
- **Change:** Adjustable seek semantics and focused keyboard/D-pad support; publish read-only Media3 state; delayed buffering feedback with continued Pause agency; stateful toggle descriptions and visible focus.
- **Implementation:** `Hud.kt`, `NowPlayingChrome.kt`, `PlayerController.kt::PlayerUiState`. Preserve flat drawing and audio behavior; no reprepare/session changes as part of UI feedback. Validate hidden/transformed sheet focus in `NowPlayingHost.kt`.

### 3. Fix composited metadata and enabled-control contrast

**P0 · High severity · High impact · Medium effort**

- **Problem:** Many enabled metadata labels/icons use 0.32–0.55 alpha; accent tests do not cover them or glass composites. Login errors use a fixed pale red.
- **Why:** WCAG 2.2 AA readability; artist, year, duration, format and errors are essential information, not decoration.
- **Change:** Named contrast-bounded neutral text and control roles, semantic error colors, deliberate focus/selected states in both themes.
- **Implementation:** Extend `ArtColorScheme.kt`, `PaletteExtractor.kt`, `Theme.kt` and `ArtColorSchemeTest.kt`; adopt in `Components.kt`, player, Search/Login/Settings. Current-source sanity check: 50% `#141414` over static light `#E2E2E2` is about 3.27:1; fixed login pink about 1.76:1. These are isolated color calculations, not measured screen contrast. Validate actual gradient/glass/transition backgrounds.

### 4. Make Now Playing and content insets resilient

**P0 · High severity · High impact · Medium effort**

- **Problem:** Fixed player Column uses up to 42% height for art before fixed text/actions/82dp play control. Long text, landscape and expanded windows lack a layout policy. Screens reserve unrelated 64/88dp bottom paddings for an overlaid mini-player.
- **Why:** Reflow/text scaling, thumb reach, unobscured focus and reliable transport access.
- **Change:** Reserve controls first; adapt artwork and scroll metadata in short windows; two-pane wide player; derive obstruction from actual chrome.
- **Implementation:** `NowPlayingHost.kt`, `NowPlayingChrome.kt::MetaRow`, `MainActivity.kt` and shared content-inset policy. Check 320dp widths, folded/unfolded, landscape/split-screen and 200% text; keep controls reachable without needless bottom whitespace or overlap.

### 5. Make the library discoverable without flattening Home

**P1 · Medium severity · High impact · Medium–Large effort**

- **Problem:** Icon-only Home/Artists/Search/Settings; Albums sits behind a Home section, playlists only appear as unbounded chips. Library organization is indirect for album-first users.
- **Why:** Recognition over recall, clear information architecture and efficient music retrieval.
- **Change:** Visible tab labels now. Prototype Home/Library/Search/Settings; Library provides Albums/Artists/Playlists/Favorites/Downloads. Bound Home playlist previews with See all, retaining text chips and recent artwork shelves.
- **Implementation:** `MainActivity.kt`, `HomeScreen.kt`; proposed Library and full Playlists screens, preserving existing routes/state. Validate with users before replacing the Artists destination. Navigation rail on wider windows should follow Android window classes, not device brand.

### 6. Optimize browsing for real collection size

**P1 · Medium severity · High impact · Medium effort**

- **Problem:** Artists has four fixed grid columns. Albums supports one sort and pagination but no in-list search/alphabet jump. Dense names/compilations/large playlist counts increase effort.
- **Why:** Flexibility/efficiency; Navidrome explicitly supports large curated collections and multi-library users.
- **Change:** Adaptive tiles, preserved list/grid choice, supported sort/filter controls, alphabet jump and browse search, remembered state. No whole-library claims based on sorting a single page.
- **Implementation:** `ArtistsScreen.kt`, `AlbumsScreen.kt`, `ArtistScreen.kt` and documented Subsonic client routes. The artist-albums grid already supplies an adaptive-grid precedent. Test thousands of records and long/duplicate titles.

### 7. Give the queue real control and clear feedback

**P1 · Medium severity · High impact · Large effort**

- **Problem:** Generic queue-row menus can add another copy or play next, but no remove/reorder/clear controls. Current-row recognition is song-ID-based despite supported duplicate occurrences.
- **Why:** User agency and error prevention; listeners curate sequence intentionally.
- **Change:** Queue-specific rows; remove with undo; Move up/down first, drag later with accessible alternatives; contextual additions and operation feedback. Keep a clear current/next distinction.
- **Implementation:** `QueuePage` in `NowPlayingChrome.kt`, dedicated `QueueRow`, occurrence-aware controller operations and persistence tests. Preserve live stream, position, shuffle/repeat order and repeated media IDs. This requires a separate playback-aware review, not just a cosmetic PR.

### 8. Standardize identity rows, type and surface rhythm

**P1 · Medium severity · High visual impact · Small–Medium effort**

- **Problem:** Type tokens exist but local sizes dominate. Song rows stack duration on a third line. Headers/top bars vary; dividers and borders compete with artwork; album overflow sometimes only repeats Open album.
- **Why:** Hierarchy and consistency; professional polish means scannable music identity with less redundant chrome.
- **Change:** Named type/spacing/radius roles; two-line track identity with trailing duration; context-specific variants, shared title/state chrome, purposeful dividers and no redundant one-action menus.
- **Implementation:** `Theme.kt`, new `DesignTokens.kt`, `Components.kt`; apply incrementally to album/artist/queue/download screens. Retain compilations and long-name detail access, not aggressive truncation everywhere.

### 9. Guarantee comfortable hit areas and predictable interactions

**P1 · Medium severity · High impact · Small–Medium effort**

- **Problem:** 36dp metadata buttons and small custom chips/Retry links lack sufficient allocated bounds. Compose can expand touch targets but adjacent expansions can overlap. Search song taps require a second Play action; genre taps silently shuffle up to 80 tracks.
- **Why:** Error prevention, mobile reachability and platform consistency.
- **Change:** >=48dp allocated actions with smaller glyphs; meaningful selected states; restore ripple/focus to pressScale. Validate one song-tap contract (direct play or explicit inline play) and let genres browse with a separately labeled shuffle action.
- **Implementation:** `Components.kt`, `MetaRow`, `AlbumsScreen.kt::FolderChip`, Search/Login/player retry actions. `pressScale` currently sets `indication=null` despite its ripple comment. Keep gesture alternatives; do not make essential actions swipe-only.

### 10. Tell the truth about sound and offline availability

**P1 · Medium severity · High audience impact · Medium effort; deeper output inspection P2**

- **Problem:** Codec/bit-depth/rate come from `Song` metadata, not verified decoded stream/DAC output. Downloads groups by album title, which can merge different albums. Download presentation is sparse; global download phase can be shown on unrelated album screens. Clear offline downloads is immediate.
- **Why:** Metadata honesty, responsibility and confidence in high-quality/offline listening.
- **Change:** Label existing format as Source file; distinguish selected transcode policy from verified playback facts; never imply bit-perfect. Separate durable downloaded files from temporary cache. Identity-based download groups and collection-specific status; confirm destructive clearing.
- **Implementation:** `SubsonicModels.kt`, `NowPlayingHost.kt`, `DownloadsScreen.kt`, Album/Playlist screens, `SettingsScreen.kt`. Optional audio detail sheet for output/EQ/ReplayGain only when backed by real state. Do not change the audio engine to decorate this sheet.

### 11. Streamline connection and advanced settings

**P2 · Medium severity · Medium impact · Small–Medium effort**

- **Problem:** Long setup/security copy and a long settings page increase cognitive load; generic network errors offer little remediation; radio rows and child radios can duplicate interaction focus.
- **Why:** Progressive disclosure, time-to-first-value and recoverable self-hosted setup.
- **Change:** Keep Server URL/user/password upfront and API key secondary; IME Next/Done; safe TLS/auth/network help; concise expandable explanations; Audio/Downloads/Account detail groups. Single semantic selectable/toggleable rows. Correct inaccurate sleep-timer help now, not after a redesign.
- **Implementation:** `LoginScreen.kt`, `SettingsScreen.kt`, `EqControls.kt`; preserve HTTPS and explicit LAN HTTP consent. Current sleep hint claims seek clears the timer, but current `PlayerController.seek` does not.

### 12. Add parity and motion validation rather than speculative animation

**P2 · Low–Medium severity · Medium impact · Medium effort**

- **Problem:** No current light-mode or expanded-window visual evidence; motion tokens exist but no app-level reduced-motion policy is evident in the reviewed code.
- **Why:** Equal theme quality, accessibility and restraint. More animation is not inherently more polished.
- **Change:** Light/dark screenshot pairs for art extremes and states; respect system animation preferences; optional reduced motion; visible non-motion feedback for cards/buttons.
- **Implementation:** Extend `Motion.kt` only where necessary; retain existing 160/200–420/900ms timings initially. Screenshot tests plus actual TalkBack/keyboard/device interaction checks; a historical frame sample is not a new-release performance guarantee.

## 3. Design system / token suggestions

Extend what exists; do not replace the palette architecture.

| Token family | Suggested starting policy |
|---|---|
| Spacing | 4/8/12/16/20/24/32/48dp; compact page gutter 20dp, player 24dp, internal gap 8–12dp, section rhythm 24–32dp. |
| Typography | Screen 24/30sp semibold; primary rows 16/22sp; card titles 14/20sp; metadata 14/20sp; technical caption 12/16sp; player title 22–26/28–32sp. Use semantic TextStyles, scalable lines and full accessible names. |
| Text roles | textPrimary/textSecondary/textTertiary with contrast floors, not generic alpha; explicit error/onError and focusRing. |
| Dark neutrals | Preserve ink `#070B13`, blue `#101B2A`/`#1C3044`, controlled art washes and gold seed. |
| Light neutrals | Start from current `#E2E2E2`/`#EAEAEA`/`#D8D8D8`, dark text `#141414`; validate more luminous variants rather than imposing white SaaS cards. Bound chroma behind dense text. |
| Accent/state | Keep art-derived primary and paired onPrimary. Error/warning/offline states retain stable meanings independent of art. Active shuffle/repeat has semantics and shape/text cue, not color alone. |
| Radius | Compact cover 4dp; tile 8dp; player/card 12–16dp; larger modal 20–28dp; circular artist/play controls and pill chips intentionally separate. |
| Elevation | Quiet list 0dp; player 2–4dp; artwork 8–12dp; modal tonal surface/scrim. Border 1dp only where separation is useful. |
| Touch/density | 48×48dp allocated controls, 20–24dp glyphs; two-line track rows around 64–72dp but expandable for text. |
| Motion | Reuse existing short/medium/art/palette durations, respect system scale, provide visible focus/press feedback when movement is suppressed. |

These are design starting values, not certified color pairs. Validate composed surfaces and gradient transition frames. WCAG AA ordinary text is 4.5:1, qualifying large text 3:1, essential control graphics 3:1. Its AA minimum target is 24 CSS px subject to exceptions; this Android plan deliberately uses the stronger requested 48dp mobile target. Do not equate CSS px, dp and iOS pt.

## 4. Quick wins

1. Label bottom destinations and visibly indicate selection; check bar height accommodates labels.
2. Add state descriptions for shuffle/repeat and contextual More labels.
3. Keep back/title shell visible while album/playlist/artist loads.
4. Allocate 48dp for lyrics/favorite; replace text-only Retry with an accessible action.
5. Use semantic login error color and meaningful focused field outline.
6. Remove album overflow when its only action is Open album.
7. Add confirmation before clearing saved offline files.
8. Correct the sleep-timer hint against current behavior.
9. Restore non-motion press/focus feedback; preserve artwork atmosphere.
10. Mark existing technical values as Source file, without claiming an actual stream/DAC format.

## 5. Next steps and coding-agent execution order

1. **Baseline captures and tests:** current light/dark, compact/wide, large text, long metadata, missing/extreme art; inspect TalkBack/keyboard and thumb reach. Record task baseline; do not call existing unit tests a visual sign-off.
2. **P0 semantics + foundation:** seek/accessibility, contrast roles, control bounds and shared tokens. Define shared component/state contracts before parallel consumer edits.
3. **P0 request and status reliability:** latest-query search, error/empty distinctions, retained data, paging retry and read-only buffering status. These state-holder tests can run alongside token work in disjoint files.
4. **P0 player layout:** responsive cover/metadata/transport, focus exclusion for inactive sheets, real content obstruction/insets.
5. **P1 rows and navigation prototype:** shared track/header patterns; labels now, Library and song-tap policies validated with users before broad rollout.
6. **P1 large-library retrieval:** supported sorts/scopes, adaptive artists, remembered browse state and bounded Home previews.
7. **P1 queue/offline trust:** occurrence-safe editing with undo, identity-based downloaded groups, honest audio information and safe deletion.
8. **P2 advanced refinements:** progressive settings, richer verified output details, reduced-motion preference and adaptive expanded layouts where not already covered.
9. **Independent sign-off:** reviewer checks screenshot parity/contrast, accessibility semantics and actual device behavior, large-library responsiveness and playback invariants. Keep audio/DVC/ReplayGain/Android Auto/account regression tests as separate release gates.

### Acceptance evidence

- Successful empty search/library is distinguishable from timeout/401/connection failure. Old query/page responses never populate a newer query.
- All primary controls remain reachable at 200% text and short/wide windows, without mini-player/IME/focus obstruction.
- TalkBack can seek and operate queue moves; repeat mode is spoken; keyboard focus is visible; hidden sheets are excluded.
- Light and dark have measured readable metadata/errors/controls over representative art and composed surfaces.
- Queue edits preserve the playing stream and position and can target one duplicate occurrence.
- Technical UI clearly distinguishes source tags, selected quality, actual stream facts (when available) and output facts (when verified).
- Downloaded availability is distinct from reclaimable playback cache; destructive local-file removal is deliberate.
- Self-hosters can connect and reach a first playable song without optional tutorials or new accounts/services. No false claim of offline, bit-perfect or full genre/library coverage.

## Research references and limitations

Accessed: [Nielsen heuristics](https://www.nngroup.com/articles/ten-usability-heuristics/), [WCAG 2.2](https://www.w3.org/TR/WCAG22/), [Compose accessibility](https://developer.android.com/develop/ui/compose/accessibility/api-defaults), [semantics](https://developer.android.com/develop/ui/compose/accessibility/semantics), [Material 3](https://developer.android.com/develop/ui/compose/designsystems/material3), [adaptive navigation](https://developer.android.com/develop/ui/compose/layouts/adaptive/build-adaptive-navigation), [Navidrome overview](https://www.navidrome.org/docs/overview/), [Plexamp](https://www.plex.tv/plexamp/) and [Symfonium](https://www.symfonium.app/).

Apple HIG URLs returned JavaScript-only shells, so no freshly inspected HIG wording is claimed. The cross-platform principles supplied by the user are a comparison lens, not replacements for Material/Android conventions. No source here proves that a particular style improves user task performance; comparative user testing remains needed. Historical screenshots and device performance notes are retained as context, not present-release validation.
