# Sonveil

[![License: MIT](https://img.shields.io/badge/License-MIT-blue.svg)](LICENSE)
[![GitHub release](https://img.shields.io/github/v/release/exquisiteskink/Sonveil)](https://github.com/exquisiteskink/Sonveil/releases/latest)
[![F-Droid](https://img.shields.io/badge/F--Droid-coming%20soon-lightgrey)](https://github.com/exquisiteskink/Sonveil#install)

**An audiophile Android player for the library you host.**

Sonveil streams and downloads music from **Navidrome**, **Subsonic**, and other **OpenSubsonic**-compatible servers. Original-quality playback by default, ReplayGain, a real equalizer with AutoEQ headphone profiles, offline downloads, and Android Auto — without accounts, ads, or telemetry.

**Android 8.0+ (API 26) · Package `app.sonveil.music` · MIT**

[Install](#install) · [Features](#features) · [Screenshots](#screenshots) · [Supported servers](#supported-servers) · [Privacy](#privacy) · [Donate](#support--donations) · [Docs](#documentation) · [Contributing](#contributing)

---

## Screenshots

| Home | Now Playing |
|:---:|:---:|
| <a href="docs/screenshots/home.png"><img src="docs/screenshots/home.png" alt="Home with playlist chips and recently played albums" width="290"></a> | <a href="docs/screenshots/now-playing.png"><img src="docs/screenshots/now-playing.png" alt="Now Playing with album art and playback controls" width="290"></a> |
| **Artist** | **Up Next** |
| <a href="docs/screenshots/artist.png"><img src="docs/screenshots/artist.png" alt="Artist page with albums and popular tracks" width="290"></a> | <a href="docs/screenshots/queue.png"><img src="docs/screenshots/queue.png" alt="Playback queue with the current song and upcoming tracks" width="290"></a> |

Captured on a Galaxy Z Fold 6. Artwork comes from the connected music server.

> **Adding more shots:** drop phone PNGs in `docs/screenshots/` (and mirror into `fastlane/metadata/android/en-US/images/phoneScreenshots/` for F-Droid). Prefer portrait frames that show Home, Now Playing, library browse, and settings. Do not invent marketing mockups.

---

## Features

Verified against the current codebase (v1.3.11):

### Library & playback
- Browse albums, artists, playlists, favorites, and genres; search songs, artists, albums, and genres
- Home shelves for playlist shortcuts, recently played, and recently added albums
- Queue with play, shuffle (keeps the current song first), repeat, and Up Next (swipe or control)
- Stream **original files** by default, or optional server-side transcoding
- Gapless playback; crossfade when gapless is enabled
- ReplayGain (track / album) with optional peak limiter
- Sleep timer (15 / 30 / 45 / 60 minutes or end of track), reboot-safe

### Sound
- 10-band graphic equalizer and parametric EQ with preamp
- **AutoEQ** headphone search (~8,800 measurements); profiles restore per connected output
- Album-art–driven accents in light and dark Material 3 themes

### Offline & controls
- Download albums or playlists; resume interrupted transfers; Wi‑Fi-only option for hi-res fetches
- Play saved tracks with no network
- Notification, lock screen, and Bluetooth media controls
- **Android Auto**: app-list presence, browse (playlists / recently played / favorites / recently added), local cover art, and voice play-from-search — see [Android Auto setup](docs/android-auto.md) for sideloaded APKs

### Privacy by design
- Talks only to **your** server — no analytics, crash reporters, or third-party trackers in the app
- Credentials encrypted on-device (Android Keystore); excluded from Android backup
- HTTPS required for public hosts; HTTP allowed for local network; system trust store for TLS

---

## Supported servers

| Protocol / server | Notes |
|-------------------|--------|
| [Navidrome](https://www.navidrome.org/) | Primary target |
| [Subsonic](http://www.subsonic.org/) API | Classic Subsonic-compatible servers |
| [OpenSubsonic](https://opensubsonic.netlify.app/) | Extensions such as API-key auth and `download` |

Sign in with username/password or an OpenSubsonic API key. You need a reachable server URL; Sonveil does not host music.

---

## Requirements

| | |
|--|--|
| Android | **8.0+** (`minSdk` 26) |
| Target SDK | 35 |
| Package | `app.sonveil.music` |
| Server | Navidrome / Subsonic / OpenSubsonic-compatible |

Versions **1.3.8+** upgrade in place under `app.sonveil.music`. Builds **1.3.7 and earlier** used `app.auralis.music` and remain separate installs with their own data.

---

## Install

### GitHub Releases (available now)

1. Download the signed `Sonveil-*.apk` from [Releases](https://github.com/exquisiteskink/Sonveil/releases).
2. Open the APK on your phone (allow installs from your browser/file manager if prompted).
3. Enter your server URL and credentials.

Latest notes: [1.3.11](RELEASE_NOTES_1.3.11.md) · full [Changelog](CHANGELOG.md)

### F-Droid (planned)

Sonveil is **not published on F-Droid yet**. The [submission guide and build metadata](docs/fdroid-submission.md) are prepared, including Ko-fi and Liberapay donation links. Store descriptions and screenshots live under [`fastlane/metadata/android/`](fastlane/metadata/android/). Use GitHub Releases until F-Droid accepts and publishes the app.

### Build from source

JDK **17** and Android SDK **35**:

```bash
export JAVA_HOME=/path/to/jdk-17
export ANDROID_HOME=/path/to/android-sdk
./gradlew :app:testDebugUnitTest :app:assembleDebug
```

Debug APK: `app/build/outputs/apk/debug/app-debug.apk`.  
`./gradlew :app:assembleRelease` builds an unsigned release APK; distribution builds must use the same signing certificate as prior Sonveil releases. Keep signing keys out of version control.

---

## Support & donations

Sonveil is free and open source. If you want to support development, you can donate through either of these services:

- [Ko-fi — exquisiteskink](https://ko-fi.com/exquisiteskink)
- [Liberapay — exquisiteskink](https://liberapay.com/exquisiteskink/)

These are Sonveil's only donation channels. Donations are optional; every feature is available without payment.

---

## Privacy

- **No telemetry.** The app does not ship analytics, crash-reporting, or advertising SDKs.
- Library metadata, artwork, and audio come from **your** Subsonic-compatible server.
- Credentials stay on the device (encrypted); `allowBackup` is disabled and backup rules exclude secrets.
- Public servers must use HTTPS; cleartext is limited to local-network hosts. Cross-origin redirects are rejected.

---

## Documentation

| Doc | Topic |
|-----|--------|
| [docs/android-auto.md](docs/android-auto.md) | Android Auto browse, voice, and sideload setup |
| [docs/poweramp-dvc.md](docs/poweramp-dvc.md) | Absolute volume / DVC notes (engineering) |
| [CHANGELOG.md](CHANGELOG.md) | Version history |
| [fastlane/metadata/android/en-US/](fastlane/metadata/android/en-US/) | F-Droid / store listing copy |

---

## Contributing

Bug reports, feature ideas, and pull requests are welcome. See [CONTRIBUTING.md](CONTRIBUTING.md).

Please keep user-facing product name **Sonveil** (package `app.sonveil.music`). Some internal class/prefs names may still say “Auralis” — that is intentional legacy and must not appear in README, store, or F-Droid copy.

---

## License

Sonveil is released under the [MIT License](LICENSE).
