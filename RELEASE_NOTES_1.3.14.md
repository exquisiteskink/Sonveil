# Sonveil 1.3.14

This release improves playback reliability, album artwork, downloads, and Android Auto browsing. It also finishes the Sonveil naming cleanup.

## What's new

- **Reuse recently streamed music.** Sonveil keeps up to 256 MB of playback data so the phone player, Android Auto, and crossfades can reuse it. This temporary cache is separate from music you download for offline listening.
- **More consistent album artwork.** The phone player, notifications, and Android Auto share the album's cover artwork.
- **Browse your library in Android Auto.** Open Library, choose an artist, then an album and its songs. Playlists, Recently played, and Favorites remain available.

## What's fixed

- Slow artwork requests no longer block unrelated artwork lookups.
- Signing back into the same account no longer breaks artwork links already used by Android Auto.
- Signing out while music is loading no longer leaves playback stuck with a permanent loading error.
- Fix an equalizer timing issue that could apply audio filters to the wrong channel.
- Audio formats unsupported by the DVC safety filter bypass that filter instead of stopping playback.
- Equalizer settings are saved correctly when the phone's language uses commas in decimal numbers.
- Batch downloads move file work away from the screen's main thread to reduce freezes.
- A damaged download list is kept for recovery instead of silently discarded. This does not automatically restore a damaged list.

## Naming and upgrades

Active application code, themes, and the notification icon now consistently use Sonveil. Existing storage names, compatibility IDs, and the signing certificate are deliberately preserved so the cleanup does not break saved settings, credentials, or upgrades.

## Install

Download `Sonveil-1.3.14.apk` and install it on Android 8.0 or later. It uses the same signing certificate as the previous published Sonveil APK. The existing certificate is a shared test certificate; this release preserves upgrade compatibility and does not introduce a new production signing key.

## Testing limits

Phone launch, on-device visuals, Android Auto in a connected car, and external Poweramp EQ/Wavelet behavior have not been checked for this release. No phone is connected to ADB.

## Validation

- All 146 release-build unit tests passed.
- Release APK and instrumentation-test APK built successfully. Instrumentation tests were not run on a device.
- Android release lint reported no errors, with 36 warnings and 6 informational findings.
- The APK's signature and alignment were verified. Its signing certificate matches the published 1.3.13 APK.
- The packaged app identifies itself as Sonveil 1.3.14 (version code 34), requires Android 8.0 or later, and is not debuggable.
