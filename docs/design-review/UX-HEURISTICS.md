# Sonveil — UX research and heuristics track

Review date: 2026-10-11. Baseline: `d7bdd36`, main, Sonveil 1.3.14.

This is a role-separated assessment by the session assistant, not the output of a separately launched agent or a user study. Scope: current source, four repository screenshots, historical validation notes, and targeted official guidance. No app/device/browser interaction or new tests ran. Screenshots are historical illustrations, not proof of current-release rendering.

## Audience and research basis

Navidrome explicitly emphasizes large collections, curated metadata, compilations, multi-disc albums and multi-library support. Consequently, efficient retrieval, faithful identity and confidence about playback are more important than decorative discovery features. Plexamp and Symfonium publicly emphasize continuity, offline use, audio tuning and collection exploration. These are useful product comparators, not evidence that copying their layouts would improve Sonveil.

Sources accessed during this review:

- Nielsen Norman Group: https://www.nngroup.com/articles/ten-usability-heuristics/
- WCAG 2.2: https://www.w3.org/TR/WCAG22/
- Android Compose accessibility defaults: https://developer.android.com/develop/ui/compose/accessibility/api-defaults
- Android semantics: https://developer.android.com/develop/ui/compose/accessibility/semantics
- Adaptive navigation: https://developer.android.com/develop/ui/compose/layouts/adaptive/build-adaptive-navigation
- Navidrome overview: https://www.navidrome.org/docs/overview/
- Plexamp: https://www.plex.tv/plexamp/
- Symfonium: https://www.symfonium.app/

These are established guidelines consulted in 2026, not newly published 2025–2026 usability experiments. Comparator pages describe advertised capabilities, not independently verified performance. Apple HIG pages were attempted but returned JavaScript-only shells. The user's Purpose/Agency/Responsibility/Clarity/Hierarchy/Consistency lens is applied conceptually; Android conventions remain normative for this Android app.

## Independent UX findings

| ID | Severity | Evidence / problem | Recommendation |
|---|---|---|---|
| UX1 | High | `SearchScreen.kt` catches request failures into empty results and can show “Nothing matched” while requests are pending. Previous-query results remain until replacement. Its More results coroutine has no query-generation guard or loading lock. | Explicit initial/loading/results/empty/error/paging states; latest-query identity; cancellation; disable duplicate page loads; page failure with retry, not empty results. |
| UX2 | High | `HomeScreen.kt` converts individual shelf failures into empty lists, then calls an empty library an error. `AlbumScreen.kt`, `PlaylistScreen.kt` and artist detail return before rendering a normal screen shell while initially loading. `AlbumsScreen.kt` hides later-page failures behind `more=false`. | Keep navigation/back available in every state. Separate empty library from unavailable server and partial shelf failure. Preserve successful data and show targeted retry. |
| UX3 | High | `Hud.kt::FlatSeekBar` is a pointer-only Canvas with no range, set-progress action, focus or keyboard handling. | Keep the flat design, add adjustable range semantics and keyboard/D-pad seeking, or use a suitably styled platform Slider. Disable seeking until duration is known. |
| UX4 | High | `PlayerUiState` exposes `isPlaying` and error but not buffering/play intent. MiniBar/ControlsDeck cannot distinguish paused from preparing or buffering. | Publish read-only playback status from Media3; show delayed buffering feedback without removing Pause/Cancel agency or changing the audio session. |
| UX5 | High | `NowPlayingPage` is a non-scrollable Column, with artwork up to 42% of available height, fixed spacers, a 36dp metadata/action strip, 82dp play control and no large-text layout policy. | Reserve the controls before sizing art; allow scrolling metadata on short windows; use a two-pane landscape/expanded variant. Verify 200% text and long titles. |
| UX6 | Medium | `MainActivity.kt` has Home/Artists/Search/Settings, icon-only, transparent selection indicator. Albums live behind Home's Recently added header; playlists are an unbounded FlowRow on Home. | First add visible labels and a persistent album/playlist/favorites entry point. Test Home/Library/Search/Settings with Library → Albums/Artists/Playlists/Favorites/Downloads, retaining existing routes and remembered state. |
| UX7 | Medium | `ArtistsScreen.kt` fixes four grid columns; album browsing has one alphabetical sort, no in-list search or alphabetical jump. All playlist chips compose on Home. | Adaptive grid; save view/sort preferences; browse search and alphabet jump; bound Home previews with See all. Add only server-supported sorts and filters. |
| UX8 | Medium | `QueuePage` reuses generic SongRow menus (Play next/Add to queue/Favorite) but has no remove/reorder/clear or occurrence-aware queue-specific affordances. SongRow recognizes the current song by media ID, not queue occurrence. | Dedicated QueueRow; remove with undo; explicit Move up/down actions before drag; preserve duplicate occurrences and live audio/position. Clear queue should be deliberate and safe. |
| UX9 | Medium | Search song tap opens another action sheet before playback, whereas normal SongRow taps play. GenreChip taps silently shuffle at most 80 returned songs rather than opening a genre. | Evaluate direct song playback plus overflow actions against an explicit inline Play action. Make genre taps browse; label any “Shuffle genre” separately and do not imply a complete genre queue without pagination. |
| UX10 | High | Arbitrary text alpha (0.4–0.55) and low-alpha enabled icons bypass contrast-checked scheme roles. Login uses fixed pale red error text. Exact on-screen failures are not measured. | Audit composited text and control contrast in both themes; generate bounded muted-text roles; use semantic error colors and focus outlines. |
| UX11 | Medium | LibraryChip/FolderChip and text-only Retry links have no explicit 48dp layout guarantee. MetaRow constrains two IconButtons to 36dp; Compose may expand hit targets but adjacent expansions risk overlap. | Guarantee 48dp allocated action bounds, retain 20–24dp glyphs, add selected/toggle state semantics and contextual action names. Check actual hit regions on device. |
| UX12 | Medium | Login is a reasonably direct connection form with HTTP consent, but has no IME Next/Done chain or differentiated generic network/TLS remediation. Long security copy competes with the immediate task. | Keep server/user/password upfront, API key secondary; add IME navigation, structured safe error guidance and a “Where do I find my server address?” disclosure. Do not weaken HTTP safeguards or enable insecure certificate fallback. |
| UX13 | Medium | Settings is one long page of radios, playback options and EQ. Clear offline downloads is an immediate destructive TextButton. Sleep hint says seek clears the timer, while current `PlayerController.seek` does not. Downloads groups by album name, not album identity. | Progressive settings groups; safe deletion confirmation; verify/correct help against actual behavior; group downloads by account/album identity and show durable availability vs temporary cache. |

## Nielsen's ten heuristics

| Heuristic | Assessment |
|---|---|
| 1. Visibility of system status | Playback position and retry exist; buffering, search activity and partial network errors are insufficiently distinguished (UX1–4). |
| 2. Match with real world | Music vocabulary and disc ordering are good; genre chip playback behavior and ambiguous source-format labels are less aligned with expectations (UX9). |
| 3. User control and freedom | Back and explicit Up next are present; queue editing and safe offline deletion need improvement (UX8, UX13). |
| 4. Consistency and standards | Rounded icons and shared rows help. Search vs normal song taps, mixed headers and icon-only navigation weaken predictability (UX6, UX9). |
| 5. Error prevention | HTTPS consent is strong. Repeated paging clicks and destructive clearing need guards (UX1, UX13). |
| 6. Recognition over recall | Art, recent searches and recent albums work well. Albums/playlist destinations and toggle state need stronger visible labels (UX6, UX11). |
| 7. Flexibility and efficiency | Artist list/grid and album pagination exist; large-library filtering/sorting/jump and accessible queue moves are missing (UX7, UX8). |
| 8. Aesthetic/minimalist design | Artwork-led atmosphere is coherent. Unbounded chips, repeated suggestion/full results and three-line song rows add avoidable visual/cognitive load. |
| 9. Error recognition/recovery | Playback Retry is a good precedent; generic ErrorText and empty-on-failure browsing/search should adopt actionable recovery (UX1, UX2). |
| 10. Help/documentation | Connection security and EQ hints exist, but should be concise, accurate, contextual and readable in light mode (UX12, UX13). |

## Platform and accessibility interpretation

Purpose: center listening/retrieval rather than settings and ornamental effects. Agency: visible queue actions and explicit playback intent. Responsibility: preserve privacy, metadata honesty and safe download removal. Clarity/hierarchy: legible artist/album identity and explicit destinations. Consistency: one song interaction contract, one state/retry pattern.

WCAG is used as a native-app accessibility benchmark, not a claim of web conformance. Relevant AA checks: 1.4.3 text contrast (4.5:1 ordinary text, 3:1 qualifying large text), 1.4.11 non-text contrast (3:1 essential graphical controls), 1.4.4 resize, 2.1.1 keyboard, 2.4.7 visible focus, 2.4.11 focus not obscured, 2.5.7 alternatives to dragging, 4.1.2 name/role/value and 4.1.3 status messages. WCAG 2.2 AA target minimum is 24×24 CSS px subject to exceptions; the requested stronger mobile bar is 48×48dp on Android (44×44pt if an iOS implementation is ever built). Do not conflate those units or levels.

TalkBack must expose seek position/actions, shuffle enabled/disabled and repeat Off/All/One; headings should be discoverable; repeated cover/title labels should not create duplicate speech. Announce meaningful buffering/errors/queue additions politely, not every progress tick. Motion should respect system animator scale and an optional reduced-motion preference; new drag interactions need button/custom-action equivalents.

## Research and validation next

Recruit a small discovery sample of Navidrome users and audiophiles (for example 5–8, not a statistically representative survey). Tasks: connect on first run; find a specific album in a large library; search a long/classical title; recover from server loss; play one song without unexpected queue replacement; remove one duplicate queue occurrence; distinguish source file quality from active stream and downloaded availability. Include light-mode users, a screen-reader user, a large-text user and a Fold/tablet user.

Record task completion, mis-taps, first usable library time, time-to-first-audio, query latency and navigation reversals. Establish baseline before choosing targets; do not invent improvement percentages or add telemetry without consent. Observe thumb reach on folded phones before changing control placement. All findings that require actual rendering, focus order or touch hit testing remain validation gaps, not confirmed device failures.
