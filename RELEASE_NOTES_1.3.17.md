# Sonveil 1.3.17

A reliability fix release: hardening found by an internal defect audit. No new features.

## Fixed

- **Equalizer changes apply promptly.** The live equalizer state is now shared safely with the audio thread, so a preset change cannot keep playing the previous curve indefinitely.
- **Album artwork lookup is bounded.** When the music server hangs, the Android Auto artwork resolver no longer parks its worker thread for up to a full minute; the lookup gives up after 10 seconds.
- **Clearing downloads can no longer crash the app.** If the filesystem refuses the operation (for example low storage), Sonveil now shows "Could not clear downloads" instead of failing fatally.
- **Position kept when queueing early.** Using "Play next" or "Add to queue" while the player is still connecting no longer discards the saved playback position of a restored queue.
- **Seeking with an unknown duration works.** During buffering or for tracks without duration metadata, the seek bar previously snapped to the start; it now seeks to the requested position.
- **Recent searches are stored safely.** A query containing a control character could corrupt the recent-search list; storage is now a JSON array and older lists migrate automatically.
- **Prefetch follows account changes.** Switching accounts now re-populates the playback look-ahead cache for the new account.

## What stays the same

- No changes to playback behavior, audio processing policy, downloads, or library navigation beyond the fixes above.
- Credentials and authenticated URLs remain never persisted; no new permissions.

## Validation

- 183 debug JVM/Robolectric tests pass.
- Debug and release builds compile.

The pending acceptance checks from 1.3.15/1.3.16 (full TalkBack, physical keyboard, live-server failure, audio listening with external EQ) remain outstanding and carry forward.
