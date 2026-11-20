# Prior-art notes

Both repos cloned (shallow) into `~/prior-art/` on 2026-11-16:

| Repo | Commit | Size | License |
|---|---|---|---|
| Myspoti (now renamed **Orpheon**), https://github.com/kota-zo/myspoti | `70f323c` (2026-11-12) | ~150k lines Kotlin, 15 Gradle modules | GPL-3.0 |
| SpatialFlow, https://github.com/MythicalSHUB/SpatialFlow | `ab6336c` (2026-10-29) | ~38k lines Kotlin, 1 module (`:app`) | Apache-2.0 (+ NOTICE asking for attribution) |

License decision for tonight (from the project brief "Unattended mode"): **Myspoti option (b)**: study and re-implement, copy no Myspoti code. SpatialFlow code may be copied with Apache-2.0 attribution. So far nothing has been copied verbatim from either repo; see `docs/THIRD_PARTY.md`.

Coverage note: Myspoti is far too large to read line by line in one night. I read the module layout, the README, the `:api` interfaces, the `:api-ytmusic` module, the extension manifest, and located (but did not study) the Spotify host and ad-block code. SpatialFlow: the `data/innertube` package, the NewPipe wrapper, and the outline of the playback service. Sections below say which.

## Big-picture findings

1. **Myspoti does not use official Spotify APIs for playback.** Its README: "the official Spotify Web Player plays the music, hidden inside the app, with your phone's DRM. Orpheon draws its own screens on top and drives it." Modules `:host-android` (`WebPlayerHost.kt`, `ControlRelay.kt`) and `:host-cdp` drive open.spotify.com in a hidden WebView. That is not the official App Remote route SPEC.md §2 requires, and it is the same machinery the ad blocking hooks into (`api-spotify/.../bridge/AdBlock.kt`, `connect/RemoteAdMute.kt`, `engine/NativeEngine.kt`). **Duet will not reuse the Spotify playback host at all**, not only the ad blocking. Its downloads are also extension-driven ("download-source extension") and are out of scope.
2. **Myspoti's YouTube Music module is account-only and stream-free.** `api-ytmusic/InnerTube.kt` does WEB_REMIX `browse`/`search`/`next` with the user's cookies and SAPISIDHASH, and its KDoc says "nothing that gets a stream". No help for step 2 streaming.
3. **SpatialFlow is the useful one for YouTube Music** (search, browse, stream URLs) but leans on **NewPipe Extractor (GPL-3.0)** for signature timestamps and as the final stream fallback. Linking NewPipe makes Duet GPL on distribution, which contradicts the "keep license open" default. Live test tonight (below) shows Duet can resolve streams without NewPipe for now.

## Feature by feature

### Module layout
- **Myspoti** (`base/settings.gradle.kts`): `:api` (UI-facing interfaces: `MyspotiApi` with `player`, `search`, `catalog`, `library`, ...), `:api-spotify`, `:api-ytmusic`, `:api-web`, `:engine` (KMP, `commonMain` + `jvmTest`), `:engine-android`, `:host-android`, `:host-cdp`, `:extensions`, `:app`, `:wear`, ...
- **SpatialFlow**: single `:app` module, packages `data/innertube`, `data/lyrics`, `data/db`, `player`, `service`, `ui/*`, `viewmodel`.
- **Duet takes**: Myspoti's idea of a UI-facing interface module separate from source implementations (re-implemented as `SourceProvider` in `:shared/commonMain`, per SPEC §3b). Not SpatialFlow's single module.

### Auth
- Myspoti: Spotify login happens inside the hidden Web Player (WebView on open.spotify.com); `app/.../connect/CodeExchange.kt` has an auth-code exchange used for Connect. Not the PKCE + App Remote route.
- SpatialFlow: optional YouTube cookie login (`AccountManager.kt`) + SAPISIDHASH header (`InnerTubeClient.buildHeaders`). Not needed: YT account features are out of MVP scope.
- **Duet**: Spotify via AppAuth/PKCE + App Remote (step 3, needs Client ID). YT Music without login.

### Search (YouTube Music)
- SpatialFlow `InnerTubeClient.search()`: POST `music.youtube.com/youtubei/v1/search`, WEB_REMIX context, optional `params` filter, continuation support. Headers: `X-Origin`/`Origin`/`Referer` = music.youtube.com, `X-YouTube-Client-Name: 67`, client version. Parser `InnerTubeParser.kt` (1121 lines) handles shelves, cards, continuations.
- Myspoti `InnerTube.search()`: same request shape (credits InnerTune), cookies required.
- Our own `reference/web-prototype/server.js`: same request + a generic JSON walk collecting `musicResponsiveListItemRenderer`, title from flex column 0, artist/album/duration from flex column 1 runs.
- **Duet**: re-implements the web prototype's walk in Kotlin with kotlinx.serialization (`YtMusicParser`), songs filter `EgWKAQIIAWoKEAkQBRAKEAMQBA%3D%3D`, highest-resolution thumbnail. Simpler than SpatialFlow's full parser; grow it when Artists/Albums chips get wired.

### Playback / stream resolution (YouTube)
- SpatialFlow `InnerTubeClient.player()`: tries ANDROID client (`clientVersion 20.10.38`, `androidSdkVersion 30`) on `www.youtube.com/youtubei/v1/player`, then IOS (`20.10.4`, iPhone16,2), and finally falls back to NewPipe `StreamInfo.getInfo` (`NewPipeStreamExtractor.kt`). It also asks NewPipe for a `signatureTimestamp`. Picks the audio stream by bitrate per a quality preference.
- **CORRECTION (later the same night):** the URLs below only serve the first ~1-2 MB; full playback gets HTTP 403 (PoToken). See DECISIONS.md D15.
- **Live probe tonight (2026-11-16, logged out, from the owner's Mac)**: ANDROID and IOS clients both returned `status OK` with plain `url` audio formats (6 and 5; no `signatureCipher`), and a 64 KB range request on each URL returned HTTP 206. ANDROID_VR returned `LOGIN_REQUIRED` ("confirm you're not a bot").
- **Duet**: `YtMusicSource.streamUrl()` = InnerTube `player` with ANDROID then IOS, choose highest-bitrate audio-only format, no NewPipe. If YouTube starts ciphering/throttling these clients, the fallback is NewPipe Extractor behind the same interface (owner must accept GPL then) or the official embedded player.
- SpatialFlow plays through Media3 `MediaSessionService` (`AudioPlaybackService.kt`, 2018 lines) with `WAKE_MODE_NETWORK`, audio attributes, `CacheDataSource`. **Duet step 2** uses a plain ExoPlayer in the Android `SourcePlayer`; MediaSession/notification is step 6.

### Queue
- Myspoti `Player` interface (`api/Api.kt`): `play(context, track)`, `addToQueue`, `playNext`, `moveInQueue`, `skipTo`, `state: StateFlow<PlayerState?>`, `queue: StateFlow<Queue?>`. Good shape for the step-4 `PlaybackCoordinator` (re-implement, no code).
- SpatialFlow: one queue for local and online tracks inside the service. Read only in outline.

### Lyrics (later step)
- SpatialFlow: `data/lyrics/LrcLibResponse.kt`, `engine/LyricsFetchManager.kt`, `LyricsDecisionEngine.kt` (multi-provider ranking), plus a vendored `org/akanework/gramophone/.../SemanticLyrics.kt` (from Gramophone, **GPL-3.0**, not Apache: do not copy that file). Myspoti also does LRCLIB + Genius. To read properly at step 7.

### Extensions
- Myspoti `extensions/Manifest.kt`: `id` (reverse domain), `name`, `version`, `versionCode`, `apiVersion`, `actions` each with a human `why` and `hosts`, `media.hosts`, `settings`, `minAppVersion`, `replaces`, `conflictsWith`. Extensions are sandboxed JS (`main.js`) with an install prompt built from the manifest.
- **Duet**: `ExtensionManifest(id, name, version, capabilities, permissions)` per SPEC §3b, compiled-in for Phase 1. Myspoti's "every permission carries a why" is a good idea to adopt for the Phase 2 prompt.

### Now Playing UI, theming
- SpatialFlow: `ui/player/FullPlayer.kt`, Palette for dynamic color, Material 3. Myspoti: Immersive look credited to BitChord. Not read in detail yet (step 4/6). Duet uses its own design tokens, not either app's look.

## Things explicitly not taken
- Myspoti ad blocking (`AdBlock.kt`, `RemoteAdMute.kt`, related engine code): never.
- Myspoti hidden Web Player host / DRM path: not official Spotify API, so not used.
- Downloads/offline from either app: out of scope (owner's instruction).
- NewPipe Extractor and Gramophone's `SemanticLyrics.kt`: GPL-3.0; not linked/copied while Duet's license stays open.
