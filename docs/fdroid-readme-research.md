# Brief research notes — README / F-Droid prep

Mirror **structure and tone** from popular FOSS Android projects; do not copy their text.

## What works well on peer GitHub pages

| Project | Patterns worth mirroring |
|---------|---------------------------|
| **AntennaPod** | Badges first (license, release; CI only if real), short product sentence, clear Feedback / License / Build sections, honest store badges |
| **Auxio** | Strong one-liner, scannable feature bullets verified against real capabilities, Screenshots early, Donate section with live links, Privacy called out, Contributing + License footer |
| **NewPipe** | TOC anchors, Screenshots + install paths, explicit Donate + Privacy, F-Droid/GitHub install honesty (signing keys), contribution welcome |
| **Vinyl Music Player** | Compact pitch, license + CI + F-Droid badges when true, changelog pointer, screenshot art |
| **Open Subsonic clients** (general) | Lead with protocol compatibility (Navidrome / Subsonic / OpenSubsonic), auth modes, offline — users shop by server |

## F-Droid listing expectations

- Fastlane tree: `fastlane/metadata/android/en-US/` with `title`, `short_description` (≤80 chars), `full_description` (≤4000), `changelogs/<versionCode>.txt` (≤500), optional `images/icon.png` + `phoneScreenshots/`
- Never claim an F-Droid package or live F-Droid version badge before acceptance
- LICENSE must be clear; funding links help maintainers but are optional
- Build metadata is prepared in `fdroid/metadata/app.sonveil.music.yml`; inclusion remains pending
- Screenshots should be real device captures, not invented store mockups

## Applied to Sonveil

- Badges: MIT + GitHub release + grey “F-Droid coming soon” (not a live fdroid v badge)
- Features audited against `PlayerSettings`, Auto EQ/RG/crossfade/sleep/download/AA code paths
- Donations: only Ko-fi and Liberapay, both using the owner-supplied exquisiteskink account name
- Product name in all user-facing copy: **Sonveil** only
