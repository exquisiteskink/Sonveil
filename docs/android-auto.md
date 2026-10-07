# Android Auto (Sonveil)

Sonveil is a **media-category** Android Auto app: Media3 `MediaLibraryService` + `MediaBrowserService`, not a Car App Library template host.

## What the car UI shows

| Surface | Source |
|---------|--------|
| App list label | `android:label` → `@string/app_name` → **Sonveil** |
| App list icon | `@mipmap/ic_launcher` |
| Package identity | `applicationId` / namespace `app.sonveil.music` |
| Browse tabs | Playlists, Recently played, Favorites, Recently added |
| Attribution icon | Monochrome `@drawable/ic_stat_auralis` on media cards |

Internal class names (`AuralisApp`, `Theme.Auralis`) and prefs keys are **not** shown in the AA drawer.

## Architecture (menu presence)

1. `AndroidManifest`: `com.google.android.gms.car.application` → `@xml/automotive_app_desc` with `<uses name="media"/>`.
2. Exported `PlaybackService` (`MediaLibraryService`) advertises:
   - `androidx.media3.session.MediaLibraryService`
   - `androidx.media3.session.MediaSessionService`
   - `android.media.browse.MediaBrowserService`
3. `AutoLibraryCallback` returns a root immediately; root children (four browsable tabs) do not require login.
4. Car hosts (`com.google.android.projection.gearhead`, Automotive media, Assistant) are allowlisted so browse+play works even when Media3 `isTrusted` is false on a device build. An allowlisted name is accepted only when that package is a system image app, shares a signer with Play services or the Play Store, or matches the known Android Auto production certificate. A sideloaded package that only copies the name is rejected.
5. Playlist / album / song artwork uses `content://app.sonveil.music.coverart/…` via `CoverArtContentProvider` using signed capabilities for each account, cover and size (Android Auto rejects HTTP artwork URIs). Root tab icons stay on `android.resource://`. Session keeps `CacheBitmapLoader(SimpleBitmapLoader())`.
6. Assistant and Gemini play-from-search requests resolve through `AutoVoiceSearch` using the signed-in Subsonic library. Empty requests play favorites or the latest recent album; explicit song, album, artist, playlist and genre requests use the matching server APIs. Voice playback must use the media service and pass its controller authorization; the phone activity does not execute legacy `MEDIA_PLAY_FROM_SEARCH` intents.
7. Playable item URIs are `sonveil://stream` locators. `PlaybackService` resolves them to authenticated Subsonic stream URLs inside the player, so session controllers do not receive API keys or salted tokens.

Car App Library (`androidx.car.app`) is **not** required for drawer presence for media apps.

## Owner one-time steps

### A) Sideload / GitHub APK / DHU

1. Install Sonveil 1.3.10 or later (`app.sonveil.music`); earlier APKs lack the merged Android Auto menu fix. A new build from this branch also includes voice search.
2. Open **Android Auto** app settings → tap **Version** ~10× → enable developer mode.
3. Developer settings → enable **Unknown sources**.
4. Optional: run Desktop Head Unit against the phone (see [Test using the DHU](https://developer.android.com/training/cars/testing/dhu)).
5. In Android Auto settings, open **Customize launcher** and make sure **Sonveil** is enabled. Reconnect the phone and open the car app list.

Without Unknown sources, sideloaded media apps stay out of the AA app list even when the manifest is correct.

### B) Production cars (Play)

1. Play Console → form factors → add **Android Auto**.
2. Upload an AA-capable AAB/APK to a testing track (internal/closed is enough to start).
3. Complete the Android Auto declaration / quality checklist for media apps.
4. Install from Play (or internal sharing) on the phone used in the car — trusted source, so Unknown sources is not required.

## Residual limits

- Sign-in must happen on the phone; Auto shows an authentication error if the library is opened while signed out.
- Voice requests need the phone to reach the music server, except when the requested tracks are already playing. Validate query wording and server search behavior with a real device.
- **Menu / launcher presence is not claimed as fully closed without DHU or vehicle confirmation.** Unit tests and `assembleDebug` cover code + packaging only; owner should validate once on DHU or a car after install (Unknown sources or Play track).
- Cover art downloads happen lazily in `CoverArtContentProvider.openFile`; first browse may show placeholders until cache fills.
- DVC / Poweramp EQ / ReplayGain paths are unchanged by the Auto menu work.
- If a legitimate Android Auto or Assistant build is signed with a key that is neither a system image, the pinned production cert, nor the Play services / Play Store signer, browse will fail closed. Add that cert digest to `AutoClientGate.pinnedCertSha256` rather than dropping the check.

## Related research

See [AA-MENU-RESEARCH.md](./AA-MENU-RESEARCH.md) (checklist + gap analysis).
