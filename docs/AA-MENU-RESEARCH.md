# Android Auto menu presence — research (Sonveil)

Date: 2026-09-26 (America/New_York). Sources: Android for Cars / Media3 docs (developer.android.com), Media3 session demo, Play distribute/testing guides.

## Path for a Subsonic/Navidrome media player

**Use Media3 `MediaLibraryService` + `MediaLibrarySession` (media category), not the generic Car App Library templates.**

| Path | Role | Menu presence |
|------|------|---------------|
| Media3 `MediaLibraryService` / platform `MediaBrowserService` + `automotive_app_desc` `<uses name="media"/>` | Audiophile / catalog browse + playback; AA draws the driver UI | **Yes** — this is how media apps appear in the AA app list |
| `androidx.car.app` Car App Library (`CATEGORY_MEDIA` / templated media) | Custom templates; still needs a media session underneath for voice/smart | Optional; **not required** for drawer presence for classic media apps |

Sonveil is a library browser + streamer → stay on the Media3 media path.

## Checklist for AA **app drawer / menu** presence

1. **Manifest meta-data** on `<application>`:
   - `com.google.android.gms.car.application` → `@xml/automotive_app_desc`
2. **`res/xml/automotive_app_desc.xml`**:
   ```xml
   <automotiveApp>
     <uses name="media"/>
   </automotiveApp>
   ```
3. **Exported service** extending `MediaLibraryService` with intent filters:
   - `androidx.media3.session.MediaLibraryService`
   - `androidx.media3.session.MediaSessionService` (optional but kept)
   - `android.media.browse.MediaBrowserService` (required for PackageManager / AA discovery + MediaBrowserCompat)
4. **`onGetLibraryRoot` returns a non-null root quickly** (no auth / network in the root call).
5. **`onGetChildren(root)` returns ≤4 browsable-only tabs** (root hints; default 4 / FLAG_BROWSABLE).
6. **Do not hard-reject Android Auto hosts** in `onConnect` (accept `isTrusted` **or** known car/assistant packages with full library + player commands).
7. **Launcher label / icon / applicationId** are what the car UI shows (Sonveil / `app.sonveil.music`).
8. **Car App Library / `androidx.car.app.minCarApiLevel`**: not required for media-category drawer presence.

## Play Console vs DHU / sideload

| Channel | Requirement |
|---------|-------------|
| **Desktop Head Unit (DHU) / developer phone** | Install APK; enable Android Auto **Developer settings** → **Unknown sources**. Sideloaded (GitHub) builds will not appear in the car list without this. |
| **Production / customer cars** | App must come from a trusted source (Play). Declare **Android Auto** form factor in Play Console; ship an AA-capable AAB/APK to a testing track. Internal/closed testing can unblock real cars without full production review. |
| **Signed release** | Use the same signing key as Play / prior Sonveil releases for updates. Debug/test key is fine for DHU + Unknown sources only. |

## What was already on Sonveil main (post-#8)

- `automotive_app_desc` + car.application meta-data
- `PlaybackService` : `MediaLibraryService` + MediaBrowserService intent
- Browse MVP: Playlists / Recently played / Favorites / Recently added
- Session id `app.sonveil.music.session`, label Sonveil

## Gaps that break or weaken **menu / selectable** behavior

1. **`onConnect` rejected every non-`isTrusted` controller** — some Auto/host probes are not marked trusted; rejection → app connects as media-session-only or fails browse attach (background controls without drawer browse).
2. **`childrenOf(ROOT)` required credentials first** — if restore had not published credentials yet, root children were `[]` → empty Auto UI after select.
3. **`pageSize <= 0` returned `ERROR_BAD_VALUE`** — AA/Media3 hosts must not depend on pagination; treat non-positive pageSize as “return all”.
4. **No root-tab icons** — AA prefers monochrome tab icons (discoverability / polish, not hard drawer gate).
5. **No `BitmapLoader` on the library session** — artwork URIs (including `android.resource://` tab icons) may not decode for the host.
6. **Owner one-time**: Unknown sources (sideload) and/or Play Console Android Auto form factor (production cars).

## Non-goals for this PR

- Car App Library templates
- EQ / ReplayGain AudioSink changes
- Inventing Subsonic APIs
- Full cosmetic rename of leftover `AuralisApp` / `Theme.Auralis` (not shown in AA menu)
