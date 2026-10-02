# Sonveil 1.3.11

This release fixes artwork loading and reduces work during scrolling, playback updates, and palette animations.

- Album and artist images try the next available source when loading fails, with visible placeholders for missing artwork.
- Notification artwork decodes local content URIs correctly. Phone-started playback and Android Auto browsing use account-scoped artwork with a bounded, concurrent cache.
- Home and song rows avoid refreshing on every playback position tick; backgrounds animate during drawing.
- Album pagination no longer competes with initial loading. Obsolete lyrics requests are cancelled.
- Support development through [Ko-fi](https://ko-fi.com/exquisiteskink) or [Liberapay](https://liberapay.com/exquisiteskink/), both under **exquisiteskink**. The same links are available in Settings.
- F-Droid submission metadata and donation links are prepared. Sonveil is not yet available in the official F-Droid repository.

Install `Sonveil-1.3.11.apk` on Android 8.0 or later. It upgrades Sonveil 1.3.8–1.3.10 in place using the existing signing certificate. Sonveil 1.3.7 and earlier use a different package and remain separate installs.

Validation: 100 unit tests and two artwork device tests passed; Android lint has no errors. Manual release checks on a Galaxy Z Fold6 running Android 16 confirmed artwork, playback controls, album pagination, notifications, and theme switching. A short scrolling/player/queue sample recorded 1.86% janky frames and a 16 ms 99th-percentile frame time. Android Auto on a connected car and the unfolded display have not been tested.
