# Sonveil 1.3.12

ReplayGain keeps its Off, Track, and Album modes and now applies gain directly to decoded PCM. This avoids enabling Android DynamicsProcessing for tagged tracks and keeps AudioTrack volume at unity during ordinary playback. Gain is set before startup buffering to prevent a loud first burst. Crossfades retain separate per-track gain, and malformed peak tags no longer suppress playback volume.

- Protect exported artwork with account-scoped signed capabilities and restrict server artwork to the configured server origin.
- Require explicit consent for authenticated LAN HTTP. Existing HTTP accounts return to sign-in with saved fields retained; HTTPS accounts continue normally.
- Verify Android Auto, Automotive, and Assistant host identities instead of trusting package names alone.
- Keep API keys and authentication tokens out of session playback URIs; resolve opaque stream locators inside the player.
- Bound offline downloads to 2 GiB per file, a 32 GiB total storage budget, a 128 MiB free-space reserve, and a 30-minute request timeout. Limit artwork imports to 8 MiB.
- Route voice playback through the authorized media service.

Install `Sonveil-1.3.12.apk` on Android 8.0 or later. The APK uses the existing signing certificate and upgrades Sonveil 1.3.8–1.3.11 in place.

Validation: 116 unit tests and 11 release device tests passed; Android lint reports no errors. Galaxy Z Fold6 checks covered artwork, playback, ReplayGain mode changes, and pause/resume. The Shinedown *The Sound of Madness* regression was checked on the phone. Android Auto host identity was verified, but a connected car and external Poweramp EQ/Wavelet were not tested.
