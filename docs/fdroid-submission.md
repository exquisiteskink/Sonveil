# F-Droid submission

Sonveil (`app.sonveil.music`) is not published in the official F-Droid repository yet.
No existing packaging request or package metadata was found on 2026-10-01.

## Prepared files

- [`fdroid/metadata/app.sonveil.music.yml`](../fdroid/metadata/app.sonveil.music.yml): build recipe for tag `v1.3.11` (version code 31), MIT license, source/issue/changelog links, automatic tag updates, and exactly two donation URLs.
- [`fastlane/metadata/android/en-US/`](../fastlane/metadata/android/en-US/): title, summary, description, icon, real device screenshots, and release changelog.
- [`third-party-assets.md`](third-party-assets.md): attribution for the bundled MIT AutoEq catalog, with its full license notice included in the APK.

Donation URLs:

- https://ko-fi.com/exquisiteskink
- https://liberapay.com/exquisiteskink/

F-Droid receives the Ko-fi HTTPS URL in `Donate` and `exquisiteskink` in its dedicated
`Liberapay` field, which creates the Liberapay link with special formatting. These URLs were supplied by the owner;
automated profile-page requests received HTTP 403, so account availability was not verified.

## Build requirements

- JDK 17, Android platform/build tools 35, Gradle 8.9 (repository wrapper), Android Gradle plugin 8.7.3.
- Build from the public release tag using `./gradlew :app:assembleRelease`.
- Release builds are unsigned; the build recipe needs no private key, ignored local keystore, or proprietary dependency.
- The app supports self-hosted Navidrome/OpenSubsonic servers and has no advertising, analytics, Firebase, or Google Play services SDK dependency. Android Auto is optional.
- The compressed AutoEq JSON is preset data, not executable code.

## Submit

1. Sign into GitLab and fork [`fdroid/fdroiddata`](https://gitlab.com/fdroid/fdroiddata).
2. Create a branch named `app.sonveil.music` in the fork.
3. Copy the prepared YAML into the fork's `metadata/app.sonveil.music.yml`.
4. Run current `fdroid rewritemeta` and `fdroid lint`; use the fork's CI to check scanning/building.
5. Open a merge request with the new-app template and follow up on maintainer feedback.

F-Droid builds and publishes apps after maintainer acceptance. A GitHub release or local recipe
alone does not create an official listing. This recipe requests reproducible builds using the GitHub release APK and the existing
upstream signing certificate, so accepted builds can retain the same application identity.
F-Droid's isolated build must reproduce and verify that APK before publication.

References: [submission guide](https://f-droid.org/docs/Submitting_to_F-Droid_Quick_Start_Guide/),
[metadata reference](https://f-droid.org/docs/Build_Metadata_Reference/),
[fdroiddata contribution guide](https://gitlab.com/fdroid/fdroiddata/-/blob/master/CONTRIBUTING.md).
