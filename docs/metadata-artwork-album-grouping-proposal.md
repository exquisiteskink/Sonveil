# Metadata, artwork, and unified albums: proposal

Investigation date: 2026-10-02. Scope: proposal only; no application changes or GitHub publication.

## Desired behavior

- Album covers favor artwork from the user's library over automatically fetched images.
- The artist screen displays a real artist image when the server supplies one, with reliable fallback between candidates.
- Server album entries representing pieces of the same release display as one album, with all of their tracks available to play, shuffle, and download.
- Deluxe editions, remasters, unrelated albums with the same title, and different libraries remain distinguishable.

Here, local metadata means the tags and artwork in the files hosted on the music server. Sonveil sees the server's API representation of that data. Downloaded files on the phone are an additional local source, not a reason to download every streamed track to inspect its tags.

## What exists today

| Area | Current implementation | Gap |
| --- | --- | --- |
| Album art | `AlbumCard`, `AlbumListRow`, and `AlbumScreen` call `CoverArt` with a server `coverArt` ID. | No shared album candidate resolver; missing album art does not fall back to member tracks or album-info image URLs. |
| Artist images | `ArtistScreen` calls `getArtistInfo2`; models already expose artist image URLs and small/medium/large images. | The UI picks one URL with `?:`, then the image component falls back directly to `getCoverArt`. A broken or blank first URL prevents trying the other info URLs. |
| Source ordering | `CoverArt` tries a supplied local URI, then an image URL, then `getCoverArt`. | Artist URLs currently precede server cover art; source priorities should be entity-specific. |
| Local overrides | `ArtOverrideStore` supports album and artist images. `OverrideAwareCoverArt` wraps album art. | The wrapper is not called elsewhere in the app; the artist override getter is unused in the artist UI. There is no user-facing image picker found. |
| Artwork cache | Coil disk caching is disabled because URL keys may contain credentials. Android Auto has a separate raw-image cache/provider. | Cached artwork is not consistently available to app screens offline, and different surfaces can choose different sources. |
| Album lists | Screens render API entries and mostly deduplicate by server album ID. | Different server IDs are always separate cards. |
| Album playback | `AlbumScreen` loads exactly one `getAlbum(id)`. Existing disc sections sort by disc/track. | Combined cards would still load only one fragment unless the data layer changes too. |
| Models | Core album/song fields, cover IDs, disc numbers and track numbers are decoded. | Album MusicBrainz ID, edition/version, release dates, compilation flag, album artist arrays, and song album artists are not decoded. |

Important files:

- [Components.kt](../app/src/main/java/app/sonveil/music/ui/components/Components.kt)
- [ArtistScreen.kt](../app/src/main/java/app/sonveil/music/ui/artist/ArtistScreen.kt)
- [AlbumScreen.kt](../app/src/main/java/app/sonveil/music/ui/album/AlbumScreen.kt)
- [AlbumsScreen.kt](../app/src/main/java/app/sonveil/music/ui/album/AlbumsScreen.kt)
- [SubsonicModels.kt](../app/src/main/java/app/sonveil/music/data/remote/SubsonicModels.kt)
- [MetadataRepository.kt](../app/src/main/java/app/sonveil/music/data/remote/MetadataRepository.kt)
- [ArtOverrideStore.kt](../app/src/main/java/app/sonveil/music/data/art/ArtOverrideStore.kt)
- [CoverArtContentProvider.kt](../app/src/main/java/app/sonveil/music/data/art/CoverArtContentProvider.kt)

## Artwork approach

Introduce a shared artwork resolver and cache. Album and artist screens supply entity references; the resolver supplies an ordered list of candidates and advances on failure. It must distinguish failed requests from successful placeholder images where a server exposes enough information to do so; avoid treating arbitrary plain-colored artwork as missing.

Album candidate order:

1. An image the user explicitly chose in Sonveil, if a picker is added.
2. Embedded front cover from a downloaded original file, where available and usable.
3. Album cover from the server; for a unified album, try its member album covers before an external fallback. Use track cover IDs as additional library candidates when the album cover is unavailable.
4. Images returned by `getAlbumInfo2`, fetched lazily when library candidates fail.
5. Optional direct online lookup using an exact release identity; then a placeholder.

Artist candidate order:

1. An explicit Sonveil artist override, if enabled.
2. Server artist cover art.
3. All nonblank image candidates from `artistImageUrl` and `getArtistInfo2`, in descending useful resolution, with deduplication and failure fallback.
4. Optional direct online lookup, if a provider is selected later; otherwise an artist placeholder.

Do not use album covers as artist portraits by default. A generic artist ID is not a guaranteed cover-art ID on every server, so retain existing compatibility fallbacks after explicit cover IDs.

The client cannot guarantee that `getCoverArt` contains embedded or folder artwork: the server may have selected an external image. Navidrome exposes `CoverArtPriority` and `ArtistArtPriority` to control that selection. To prioritize embedded album tags over folder covers, its documented source ordering supports `embedded, cover.*, folder.*, front.*, external`. Artist folder images can precede external sources. See [Navidrome artwork resolution](https://www.navidrome.org/docs/usage/library/artwork/). This is an optional server configuration recommendation, not part of the authorized app changes.

Add `getAlbumInfo2` and its response model for server-mediated fallback. OpenSubsonic defines album-info image URLs and optional release/artist metadata: [album info endpoint](https://opensubsonic.netlify.app/docs/endpoints/getalbuminfo2/), [album fields](https://opensubsonic.netlify.app/docs/responses/albumid3/), [song album artists](https://opensubsonic.netlify.app/docs/responses/child/).

Prefer server-supplied metadata first. If direct online fallback is desired, the [Cover Art Archive API](https://musicbrainz.org/doc/Cover_Art_Archive/API) supports covers by MusicBrainz release ID. It is an album-cover source, not an artist-photo source. A separate artist-image provider remains a product choice; avoid guessing portraits from artist names alone.

Use opaque cache keys scoped to server/account/entity and retain source/provenance, expiry, and artwork revisions. Share resolved image bytes with app screens, notifications and Android Auto. Keep explicitly selected images separate from replaceable fetched images. Existing overrides are keyed only by entity ID, so account scoping needs fixing before broader use. The car provider also currently looks up an override using a cover ID, which can differ from its album ID.

External image requests should use a dedicated unauthenticated client with appropriate HTTPS redirect handling. The current shared client rejects cross-origin redirects; some image/CDN chains and Cover Art Archive redirects will fail through it. Server credentials and authenticated server URLs must never be copied into provider requests or persisted as cache keys. Direct third-party access would require updating the current README statement that the app talks only to the user's server; even today, supplied artist image URLs can point off-server.

## Unified album approach

Add an `AlbumRepository` responsible for display groups rather than merging inside composables. Keep API models and original IDs intact. Each display group has an account-scoped stable key, a representative title/artist/artwork, and all member server album IDs. Resolve navigation from any member ID to its group.

Matching policy:

- Prefer the same MusicBrainz **release** ID, with edition conflicts checked. A release-group ID is broader than a release and should not collapse editions automatically. Confirm each backend's interpretation of its generic `musicBrainzId` before relying on it as a release ID.
- Without that identity, use normalized album title and album artist, plus edition/version and compatible release dates. Normalize case, Unicode and whitespace conservatively; preserve title qualifiers such as Deluxe, Live and Remastered.
- Track performers and featured artists do not define the album artist. Parse optional album artist arrays, including song `albumArtists`/`displayAlbumArtist`, to handle compilations when provided.
- Treat explicit conflicting release IDs, editions, artists, or release dates as blockers to automatic merging. Missing values are unknown, not proof of equivalence. Where fallback tags are sparse, examine disc/track evidence before auto-merging and keep ambiguous candidates separate.
- A narrow trailing `Disc 1`/`CD 2` normalization can help only with supporting release/artist/disc evidence. Do not strip parenthetical text generally.
- Add a reversible manual “Combine albums” / “Keep separate” override for cases where bad or absent tags make the intended grouping unknowable.

Navidrome itself can split albums using album identity tags, including album artist, edition and release date. Its [persistent-ID documentation](https://www.navidrome.org/docs/usage/configuration/persistent-ids/) explains the defaults. Sonveil's grouping would be a local presentation layer and would not retag files or reconfigure the server.

On opening a group, load all member albums with bounded concurrency, deduplicate only identical server song IDs, then sort by disc and track. Reuse existing disc sections. Compute counts/duration from the merged tracks. Play, shuffle and download operate on that complete list while retaining each song's real server ID and album ID. If a fragment fails, show incomplete-load/retry state rather than silently implying that the album is complete. Preserve distinct files with identical titles or track positions.

Apply the same grouping to artist albums, the full albums list, home shelves, search results, song-to-album navigation, favorites and Android Auto. Pagination must keep raw server offsets independent of displayed group counts. A persistent album-summary index is needed to find fragments beyond the current page or under different artist IDs; a list-local `groupBy` would leave those fragments undiscovered. Scope the index to account and library, refresh it incrementally, and keep first-screen rendering fast. Persist group membership needed by downloaded content for offline use.

## Delivery and validation

Recommended sequence:

1. **Artwork foundation:** models/album-info endpoint, complete artist candidate fallback, library-first resolver, account-scoped cache and consistent UI/car usage. This is the smaller change. Phone-file tag extraction and a manual image picker can be separate additions.
2. **Unified albums:** richer identity models, summary index, group repository, aggregate album loading, all entry points and downloads. This is the larger change because it affects navigation, pagination and playback semantics.
3. **Optional enrichment and overrides:** direct online covers/portraits and manual album combinations, after their provider and user-flow choices are settled.

Required validation should cover local-art priority; broken/blank URLs; account switching; cache refresh and offline images; credential isolation; albums split across pages; compilations and multiple discs; same-name different artists; standard versus deluxe/remastered editions; differing IDs; partial member fetch failure; consistent song navigation; merged playback/shuffle/downloads; and offline/Android Auto behavior. Extend existing Subsonic client, artwork cache/provider, and album-disc tests, and add grouping fixture tests.

No new builds or device tests were run for this investigation. Before implementation, one real example of a split album and the server's corresponding `getArtist`/`getAlbum` responses would resolve backend-specific ambiguity and provide a useful regression fixture. Credentials must be omitted from any shared response.
