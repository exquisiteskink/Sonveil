# Sonveil 1.3.15

A clearer library and a more accessible player, with the first round of navigation and browsing refinements.

## What's new

- Home, Artists, Search, and Settings now have visible navigation labels.
- Home shows up to six playlist previews. **See all** opens the complete playlist list.
- Song rows keep the title and artist together, with duration alongside them instead of on a third line.
- Library and search screens distinguish loading, server failures, and genuinely empty results. Retry retains confirmed content, and detail screens keep Back available.
- Search and album paging ignore stale responses and prevent duplicate in-flight page requests.

## Player improvements

- The flat seek bar supports screen-reader progress actions, keyboard seeking, and visible focus feedback.
- Sustained buffering is shown explicitly, and pending playback can be paused.
- Now Playing adapts to short windows and large text, with scrollable metadata and visible transport/queue access. Landscape artwork stays square within its available pane.
- Inactive player sheets and underlying navigation are excluded from accessibility navigation while another player surface is open.
- Metadata and error colors are more readable in both themes; saturated light artwork backgrounds are kept luminous.
- Lyrics/favorite actions have larger allocated targets. Library screens reserve the mini-player's measured space, and artist tiles adapt to available width.
- Technical format labels describe the **source file**, not an unverified device-output format.

The artwork-led styling, glass player, and flat scrubber remain. This release does not redesign the library navigation, add queue editing, or change song-tap policy or audio processing.

## Validation and limitations

- 179 debug JVM/Robolectric tests pass. Debug/release lint reports zero errors, with 37 warnings and 5 informational findings per variant.
- Normal-size Galaxy Z Fold6 smoke checks covered player/queue views, ADB-injected seeking, navigation labels, playlist browsing, song rows, and Back navigation.
- Full TalkBack, physical-keyboard, split-screen, live-server failure, and audio-listening acceptance remain pending. The landscape artwork fix has an automated regression test; its corrected 200%-text device view was not visually rechecked.

Install **Sonveil-1.3.15.apk** as an update; no uninstall or data reset is required. `SHA256SUMS` accompanies the APK. The APK uses the existing Sonveil signing certificate.
