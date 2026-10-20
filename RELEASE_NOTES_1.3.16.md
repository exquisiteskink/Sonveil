# Sonveil 1.3.16

A focused fix release: swiping works again on the Now Playing screen.

## Fixed

- **Swiping from Now Playing works again.** Swiping up opens the play queue and swiping down collapses back to the mini-player. Both gestures had stopped working after 1.3.15 introduced a scrollable metadata area for large-text accessibility: that column consumed vertical drags before the player sheet could see them.
- The fix forwards any scroll or fling not used by the content to the player sheet, so the whole screen responds to a swipe. Normal content scrolling and screen-reader behavior are unchanged, and the queue screen keeps its existing dismiss gesture.

## What stays the same

- No changes to audio processing, downloads, caching, library navigation, or song-tap policy.
- No new features. This release only restores the intended gesture behavior.

## Validation

- Compose UI tests cover the previously failing gestures: fast, medium, and short upward swipes on the artwork open the queue; a deliberately slow swipe settles back on the player.
- 180 debug JVM/Robolectric tests pass; release build compiles and lint shows no errors.

The pending acceptance checks from 1.3.15 (full TalkBack, physical keyboard, live-server failure, and audio listening) remain outstanding and carry forward.
