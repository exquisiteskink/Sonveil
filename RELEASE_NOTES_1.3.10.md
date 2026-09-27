# Sonveil 1.3.10

This release is an Android Auto bugfix. Sonveil can show its icon in the Android Auto app list so you can launch it from the car.

- Android Auto recognizes Sonveil as a media app and can list it by the Sonveil name and launcher icon.
- The car library offers Playlists, Recently played, Favorites, and Recently added.
- Album, playlist, and song artwork is provided in the local form Android Auto accepts.

Sign in on the phone before opening the library in the car. A sideloaded APK stays hidden until Android Auto developer mode is on and Unknown sources is enabled. Then select Sonveil in the Android Auto app list.

Install `Sonveil-1.3.10.apk` on Android 8.0 or later. This version upgrades Sonveil 1.3.9 in place and keeps its app data. Sonveil 1.3.7 and earlier used a different Android package and remain separate installs.

Validation: 88 unit tests passed and Android lint reported no errors. The signed APK uses the same certificate as 1.3.9. Android Auto launcher presence was not checked on a car or Desktop Head Unit for this build.
