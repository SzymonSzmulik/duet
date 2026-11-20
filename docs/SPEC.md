# Duet: product & technical spec

## 1. Product
One app, two sources. Tracks from Spotify and YouTube Music live in the same search results, queue and playlists. Every item shows its source (colored dot + text label: Spotify = green `#3ecf7a`, YouTube Music = red `#ff6b6b`; never color alone).

### Screens (designed: see design/screens)
| Screen | Key elements |
|---|---|
| Home | avatar + source filter pills (All / Spotify / YT Music) + settings; 2-col quick tiles (Liked Songs, Saved from YT Music, Duet mix, Recents...); shelf "Your top mixes"; "Quick picks" list with Play all; floating mini player; 3-tab nav |
| Search | big title; field with clear; **source switch Both / Spotify / YT Music**; filter chips Top, Songs, Artists, Albums, Playlists; result rows (art, title, "Type · Artist · Source", add-to-queue); mini player |
| Your Library | filter chips All/Playlists/Albums/Artists; sort row; rows incl. Liked Songs (Spotify), **Saved from YT Music** (local), **Duet mix** (mixed-source playlist) |
| Now Playing | art-derived gradient background; big cover; title/artist; source + quality badge; seek bar; shuffle/prev/play-pause (accent pill)/next/repeat; volume; row: Lyrics, output device, Queue |
| (to design later) | Lyrics full screen (active line large, others faded), Queue sheet, Artist/Album/Playlist detail, Recap (Wrapped-style), Settings/accounts |

Interaction notes: tapping a mini player opens Now Playing; art-derived gradient comes from the dominant color of the cover (Palette API on Android).

### Feature list (MVP then later)
MVP: Spotify login; YT Music search (no login); unified search with source switch; unified queue with gapless-ish switching between sources; Home (Spotify top tracks, recents); Library (Spotify liked + playlists, local "Saved from YT Music"); Now Playing; lock-screen / notification controls; Bluetooth device name in player.
Later: synced lyrics (LRCLIB), Recap (top tracks/artists/genres from Spotify `top` endpoints, labeled as a recap and **not** Spotify Wrapped, which has no API), offline for YT-sourced audio only if legally acceptable to the owner, Android Auto, desktop + macOS apps.

## 2. Source integration (what is and is not possible)
### Spotify: official only
- **Auth:** Authorization Code with PKCE. Android: AppAuth or Spotify Auth library. Scopes: `user-library-read playlist-read-private user-top-read user-read-recently-played user-read-playback-state user-modify-playback-state streaming`. The owner must create an app at developer.spotify.com and register the redirect URI and the Android package name + SHA-1 fingerprint.
- **Data:** Web API: `/search`, `/me/tracks`, `/me/playlists`, `/playlists/{id}/items`, `/me/top/{tracks|artists}`, `/me/player/recently-played`. Handle 429 rate limits and API restrictions on new/development-mode apps (some endpoints are restricted; check current docs and report limits to the owner).
- **Playback on Android:** Spotify **App Remote SDK** (needs the Spotify app installed; on-demand playback needs Premium). It makes Duet a controller of the Spotify app, so Duet cannot pipe Spotify audio through its own ExoPlayer or equalizer. Spotify's Web Playback SDK is browser-only and is not a supported Android route; verify in current docs before relying on it.
- **Desktop/macOS later:** Web Playback SDK inside a webview (Premium) or Spotify Connect control of another device.
- Wrapped has no API. Never claim it does.

### YouTube Music: unofficial, isolated
- No official YT Music API and no official YT Music account login. Use the same approach as open-source clients (e.g. InnerTube "WEB_REMIX" client calls, or NewPipe Extractor; OuterTune/Metrolist/SpatialFlow are examples to study). No account needed for search/streaming.
- Reference code: `reference/web-prototype/server.js` (search request + response parser, generic JSON walk for `musicResponsiveListItemRenderer`).
- Stream playback: resolve a stream URL for a `videoId` (NewPipe Extractor handles signature/n-param decryption), play via Media3 ExoPlayer. Expect breakage when YouTube changes things; keep all of this behind `interface YtMusicSource { search(); streamUrl(); }` plus a fast fallback to the official IFrame/embedded player if feasible.
- Library/playlists of the owner's YT Music account are **out of scope** for MVP. "Saved from YT Music" is a local Room table.

## 3. Architecture (Android now, desktop/macOS later)
Recommended: **Kotlin Multiplatform + Compose Multiplatform** (Android + JVM desktop for Windows/Linux/macOS from one UI codebase). iOS can follow later if wanted.

```
:shared (commonMain)
  domain/   Track, Album, Artist, Playlist, Source enum, QueueItem
  data/     SpotifyApi (Ktor), YtMusicSource (interface), repositories, Room/SQLDelight store
  player/   PlaybackCoordinator (one queue, one state flow), interface SourcePlayer
  ui/       Compose Multiplatform screens + theme (tokens from design/tokens.json)
:androidApp   Media3 ExoPlayer SourcePlayer for YT, Spotify App Remote SourcePlayer, MediaSession, notification, Bluetooth device info, Palette
:desktopApp   JVM: VLCJ or JavaFX Media for YT streams, Spotify via webview/Connect, system media keys, window chrome
```
- `PlaybackCoordinator` owns the queue and routes each item to the right `SourcePlayer` (`SpotifyPlayer`, `YtPlayer`); only one is active; pause the other on switch; expose `StateFlow<PlayerState>` (item, position, duration, isPlaying, outputDevice, quality).
- Use DI (Koin works in KMP), Coil 3 for images, Ktor for HTTP, SQLDelight or Room-KMP for local data.
- If the owner prefers a faster, less native route for the first APK, the fallback is Capacitor wrapping the web prototype, but desktop/macOS then means Electron/Tauri with a second code path. KMP is the recommendation.

## 3b. Extensions and "extra stuff" (design for it from day one)
The owner wants to add extensions and extra features later. So **Spotify and YouTube Music must be built as two ordinary plug-ins of a generic extension system, not hard-wired.** If the core only knows `SourceProvider`, adding a third source (SoundCloud, local files, a podcast feed) is just another module.

Core extension points (define these interfaces in `:shared` now; ship built-ins through them):
| Extension point | Purpose | Built-in implementations |
|---|---|---|
| `SourceProvider` | search, browse, library, resolve a playable item; declares capabilities (`search`, `library`, `radio`, `lyrics`...) and its own `SourcePlayer` | Spotify, YouTube Music, (later) Local files |
| `LyricsProvider` | synced/unsynced lyrics for a track | LRCLIB |
| `ShelfProvider` | contributes shelves to Home / Search | Top mixes, Quick picks, Recap |
| `ScrobbleSink` | receives play events | (later) Last.fm / ListenBrainz |
| `AudioEffect` | processing on the device-played stream (not available for Spotify App Remote audio) | (later) EQ, normalization |
| `ThemeProvider` / `NowPlayingSkin` | looks | default theme |

Rules:
- Each extension has a manifest: `id`, `name`, `version`, `capabilities`, `permissions` (network hosts, account scopes, storage). Show a permission prompt before enabling a third-party extension.
- Each extension gets its own settings screen and its own credential store; the core never hands one extension another's tokens.
- Phase 1: all extensions are compiled-in modules behind these interfaces. Phase 2: external extensions. Recommended mechanisms: **Android**, separate extension APKs discovered via intents (the model Mihon/Tachiyomi uses; signature-checked, opt-in); **desktop/macOS**, sandboxed plug-in JARs or a small scripting layer. Never load unsigned code silently: extensions can see the user's listening data.
- Core features that should be extensions-ready from the start: lyrics, recap, home shelves, scrobbling, extra sources.

## 4. Design system (from design/tokens.json)
Dark UI, Plus Jakarta Sans, accent lime `#c8f26b` (user-tweakable; alternates `#8f7bff`, `#ffb84d`, `#5ee0d0`), ground `#0e0f14`, surface `#1c1e27`, text `#f1f2f7`, dim `#9aa0b4` (contrast >= 4.5:1 on ground). Radii: tiles 8, cards 10, chips 20 (pill), mini player 14, play button 24. Minimum touch target 44dp (the mock's 36dp add buttons should be padded to 44). Icon buttons need content descriptions.

## 5. Build order
1. Template builds and runs (`assembleDebug`), theme + nav shell with the 3 tabs and static mini player from design.
2. YT Music search -> results list -> tap to play via ExoPlayer (prove the hard part first).
3. Spotify login + search -> results list; App Remote playback.
4. PlaybackCoordinator + unified queue + Now Playing.
5. Library (Spotify liked/playlists, local saved), Home shelves.
6. Notification/lock-screen controls, Bluetooth device name, polish.
7. Later: lyrics, recap, desktop target, macOS packaging.

## 6. Owner inputs needed
- Spotify Client ID; confirm redirect URI scheme (e.g. `duet://callback`); Android package name (suggest `app.duet.music`); debug SHA-1 for the Spotify dashboard.
- Whether the owner has Spotify Premium (needed for on-demand playback).
- App name/icon decision (working name: Duet).
- Whether to stay personal-use/sideloaded (assumed) or ever publish; publishing changes the legal risk of the unofficial YT Music side.

## 7. Known risks
- Unofficial YT Music endpoints can break or violate YouTube's terms; keep isolated and personal-use.
- Spotify App Remote limits control of audio output and requires the Spotify app.
- Spotify dev-mode apps: limited users (add the owner's account in the dashboard) and some endpoints may be restricted.
