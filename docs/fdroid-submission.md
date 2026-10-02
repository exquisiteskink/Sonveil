# F-Droid submission

Sonveil (`app.sonveil.music`) is not published in the official F-Droid repository yet.
Submission: [https://gitlab.com/fdroid/fdroiddata/-/merge_requests/50847](https://gitlab.com/fdroid/fdroiddata/-/merge_requests/50847), opened on 2026-10-01.

The metadata merge request is open and awaiting F-Droid review. The fork pipeline failed
before creating any jobs and supplied no YAML error; the submission asks maintainers to
trigger upstream CI. No existing Sonveil packaging request or package metadata was found
before this submission.

## Prepared files

- [`fdroid/metadata/app.sonveil.music.yml`](../fdroid/metadata/app.sonveil.music.yml): build recipe pinned to the full `v1.3.11` commit hash (version code 31), MIT license, source/issue/changelog links, automatic tag updates, and exactly two donation URLs.
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

## Submission workflow

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

## Local build and scan verification

- 100 unit tests pass; Android lint reports zero errors and 32 warnings.
- Current F-Droid metadata lint passes.
- Current F-Droid source scan reports zero fatal findings with one documented exception:
  `app/src/main/assets/autoeq.json.gz`, which contains MIT preset JSON rather than code.
- Two clean clones of the release tag, compiled with the Gradle build cache disabled,
  produced byte-identical unsigned APKs. Their SHA-256 is
  `1f05a928ac254fc7027f6f8c21e824a259c2513adc2d12dae6979fb394377f05`.
- F-Droid's `verify_apks` signature-copy check passes against the signed release.
- The public signed APK SHA-256 is
  `1a290b124b6586e95fc62711413134e2d980a1c8ec16c581356bbf8b49467a76`.

The clean clones use a normal `.git` directory so AGP includes the correct tag commit
in `META-INF/version-control-info.textproto`. A worktree build can instead record
`NO_VALID_GIT_FOUND`, producing a different APK.

For the same generated DEX/profile ordering, the release build constrains the JVM's
reported CPU count. The F-Droid recipe applies the equivalent setting in `gradle.properties`.
After selecting JDK 17 and SDK 35, build from a clean clone of `v1.3.11`:

```sh
JAVA_TOOL_OPTIONS=-XX:ActiveProcessorCount=1 ./gradlew clean :app:assembleRelease --no-build-cache \
  '-Dorg.gradle.jvmargs=-Xmx1536m -XX:MaxMetaspaceSize=384m -Dfile.encoding=UTF-8 -XX:+UseParallelGC -XX:ActiveProcessorCount=1'
```

The upstream APK is signed with build-tools 34 `apksigner` and APK signature schemes
v2/v3, using the existing Sonveil certificate. v1 is unnecessary for minSdk 26 and was
omitted to avoid a ZIP metadata compatibility issue in the local signature-copy tool.
No private key is published. These local checks do not replace F-Droid's isolated CI
and build-server verification.
