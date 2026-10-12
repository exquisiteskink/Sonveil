# Sonveil — visual design and polish track

Date: 2026-10-11. Baseline `d7bdd36` / 1.3.14. Role-separated assessment by the session assistant, not a separately launched agent. Evidence: current Compose/theme sources and four historical screenshots under `docs/screenshots/`. No current device render or light-mode screenshot was captured.

## Preserve the personality

Sonveil already has a strong visual idea: album art as the emotional center, soft artwork-derived background washes, restrained gold fallback, glass-like player/navigation surfaces, rounded imagery and calm playback transitions. Its player is not a generic administrative dashboard. Preserve those qualities, the simple flat scrubber, recognizable media icons and immediate playback controls. Do not bring back a decorative waveform: `Hud.kt` explicitly records its rejection.

Professional media craft is mainly hierarchy, legibility and behavioral consistency—not another layer of gradients. Official Material guidance supports role-paired colors, scalable type, accessible custom components and adaptive navigation. Plexamp publicly describes UltraBlur, gapless playback and pre-caching; Symfonium describes library tools and output-specific tuning. Treat these as category context, not templates to clone.

## Independent visual findings

| ID | Severity | Finding | Proposed treatment |
|---|---|---|---|
| V1 | High | `Components.kt`, player chrome and settings use small 11–14sp metadata at 0.4–0.55 text alpha. Historical dark screenshots show visibly recessive artist/year/duration labels. Light mode has no screenshot sign-off. | Keep subdued hierarchy but use contrast-bounded textSecondary/textTertiary roles on the actual composited surface. Do not express enabled/inactive controls as nearly invisible disabled controls. |
| V2 | Medium | `Theme.kt` defines a type ramp, but most screens bypass it with local font sizes/weights. PageTitle/ScreenTopBar/ArtistsHeader/Downloads each establish different header treatments. | Use named styles for screen title, section label, song title, album title, artist metadata and technical metadata; make screen chrome a shared component. |
| V3 | Medium | Home screenshot devotes much of the first viewport to playlist chips and a downloads text link before prominent art. Code renders every playlist in an unbounded FlowRow. | Keep text chips (valuable for playlists without art), but bound preview rows and add See all. Make Downloads a consistent compact utility destination rather than a visually competing sentence. Keep recently played reachable without a long scroll. |
| V4 | Medium | SongRow stacks title, artist and duration as three lines. Queue screenshot therefore feels tall and repetitive despite simple information. Artist album rows include a More menu that only repeats Open album. | Two-line identity row with a trailing tabular duration; preserve an optional third line for truly additional metadata. Use row variants. Remove redundant one-action overflow menus. |
| V5 | Medium | SectionHeader uses uppercase text, 1.1sp tracking, a divider and often a chevron. Applied to every shelf this creates unnecessary rules and visual weight. Header padding differs from row/grid padding. | Keep uppercase as an intentional Sonveil accent, not the only hierarchy device. Standardize section rhythm, use separators only where they group meaningfully, align shelf and title gutters. |
| V6 | High | Player fixed stack can squeeze controls at larger text sizes; MetaRow mixes codec text and favorite/lyrics actions in one narrow strip. | Separate source-format information from action buttons; allow wrapped metadata, keep controls comfortably reachable, reserve control height before artwork. Preserve large cover art on tall phones. |
| V7 | Medium | Navigation is visually icon-only and has no selected container; selection relies on accent color. pressScale removes indication despite its comment saying ripple remains. | Add short labels, shape/weight selection cue and keyboard focus. Keep gentle press scale but restore ripple/focus/hover feedback or an equivalent visible non-motion indication. |
| V8 | Low | Corners vary by local choice: image 2/3/4/6/8/14dp, card 12/16/20/28dp and pills. Borders range from 1dp glass edges to a 2dp album frame. | Formalize purposeful radius families and one restrained border weight. Not every thumbnail needs identical corners, but differences should follow semantic size/role. |
| V9 | Medium | Light defaults are grey and art-derived light surfaces use saturated pastel washes. Those can be intentional, but parity cannot be established from dark screenshots. | Keep a quiet luminous light hierarchy with distinct base/raised surfaces; bound chroma behind dense metadata, test pale yellow/white/neon art. Avoid solving light mode by merely inverting dark tokens. |
| V10 | Low | Motion tokens already exist: 160ms press, 200–420ms transitions, 900ms palette. Large artwork crossfades and wash animation are part of the identity. | Reuse existing timings before retuning. Reduce movement/fades when requested; preserve prompt playback feedback even while palette settles. Avoid new continuously animated visuals. |

## Source-level contrast sanity check (not a screenshot audit)

Against the *uncomposited static light default* background `#E2E2E2`, `onBackground=#141414` at 50%, 45%, and 40% alpha computes to approximately **3.27:1**, **2.84:1**, and **2.48:1** respectively. Those are below 4.5:1 for ordinary metadata text. Login's fixed `#FF8A80` against the same base is approximately **1.76:1**.

These calculations isolate known source colors; actual radial washes/glass surfaces and animation intermediate frames change the background. They identify a token-system problem, not a measured on-device failure of every row. `ArtColorSchemeTest.kt` already checks accent/button/seek-role contrast against representative colors. It does not certify arbitrary alpha metadata, login error text, glass compositing or rendered focus/toggle states.

## Suggested lightweight system

Extend `Theme.kt` / `Motion.kt` / `PaletteExtractor.kt` / `ArtColorScheme.kt`; do not introduce a second unrelated theme or discard art-derived schemes.

- **Space:** 4, 8, 12, 16, 20, 24, 32, 48dp. Keep 20dp compact page gutters where already characteristic; 24dp player gutters; 8–12dp internal gaps; 24–32dp section gaps. Responsive gutters are policy, not scattered constants.
- **Type:** screen 24/30sp semibold, song/album row 16/22sp normal/medium, card title 14/20sp medium, metadata 14/20sp, caption/technical 12/16sp only when sufficiently legible, now-playing title 22–26/28–32sp semibold, section label 13/18sp semibold. Values are proposed initial tokens, not a mandate to enlarge every screen. Preserve sentence case for identity; long names must get detail views, wrapping or accessible full labels.
- **Color roles:** page/surface/surfaceRaised; textPrimary/textSecondary/textTertiary; artworkAccent/onAccent; focusRing; playing; error/onError/errorContainer; warning/offline; outline/subtleOutline. Error/warning meanings should not change with album art. Art accent is not an error role.
- **Dark:** keep ink base `#070B13`, muted blue surfaces `#101B2A` / `#1C3044`, gold seed and controlled art washes. Avoid loud borders around every row.
- **Light:** keep the existing grey family as the baseline (`#E2E2E2` / `#EAEAEA` / `#D8D8D8`, text `#141414`) until comparative previews validate a brighter alternative. Contrast-bound metadata, differentiated surfaces and lower-chroma washes matter more than arbitrary new hex codes.
- **Radius:** 4dp compact covers; 8dp album tiles; 12–16dp player/card surfaces; 20–28dp sheets; circle/pill only for controls, artist portraits and chips. Use explicit `CoverShape` rather than treating any radius >=48dp as a circle.
- **Elevation:** ordinary content 0dp, raised player 2–4dp, cover art 8–12dp, modal separation via tonal surface/scrim. Keep a stronger artwork shadow only where it improves focus; no decorative floating every row.
- **Density:** 48dp allocated actions, 20–24dp glyphs, 64–72dp two-line track rows with adaptive height. Optional dense browse mode later; never shrink actionable hit bounds.

## Visual validation matrix

Capture current renders: Home, Artists grid/list, Albums, Search loading/results/error, album detail, player, queue, Downloads and Settings in both themes. Include neutral, red, blue, near-white, near-black, pale-yellow and missing art. Use compact and expanded windows, portrait/landscape and 100/150/200% font scales. Include focused keyboard controls, selected repeat/shuffle and offline/error states. Check text and icon contrast on actual composed backgrounds, not just palette seeds. Compare against the historical screenshots to ensure glass warmth, art prominence and the refined listening atmosphere survive.

Sources: https://developer.android.com/develop/ui/compose/designsystems/material3 ; https://www.w3.org/TR/WCAG22/ ; https://www.plex.tv/plexamp/ ; https://www.symfonium.app/ .
