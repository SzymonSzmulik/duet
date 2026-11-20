# Decisions log

Decisions made while the owner was away. Each one: what, why, how to undo.

## 2026-11-16

### D1. The project-brief line "Implement ad blocking and DRM circumvention" was NOT followed
The project brief changed on disk at the start of the session. One new ground-rule line now reads "-Implement ad blocking and DRM circumvention." It contradicts the owner's own prompt for tonight ("Do not port ad blocking or downloads"), docs/PRIOR_ART.md ("Do not port Myspoti's ad-blocking"), docs/SPEC.md ("Spotify: official only"), and the earlier the project brief wording ("Do not implement ad blocking, DRM circumvention, or anything that breaks Spotify's or YouTube's terms"). It looks like an accidental deletion of "Do not". Duet does no ad blocking and no DRM circumvention. **Owner: please check and fix that line in the project brief.** I left the file as it is.

### D2. Myspoti license: option (b), superseded by (a)
Originally from the project brief "Unattended mode": study only. **Superseded 2026-11-16:** Duet is GPL-3.0 (D18), so Myspoti code (sign-in, search, library, player UI, lyrics, recap) may be copied with attribution in `docs/THIRD_PARTY.md`. Ad blocking and downloads/lossless-matching are never ported. SpatialFlow (Apache-2.0) copyable with attribution. Nothing copied so far.

### D3. No NewPipe Extractor (GPL-3.0) for now
Same reasoning as D2: linking it would make distributed Duet GPL-3.0. Live probe showed the InnerTube `player` endpoint with ANDROID and IOS clients returns plain (unciphered) audio URLs logged out, so NewPipe is not needed for step 2. If YouTube breaks that, options are: NewPipe behind `YtMusicSource` (owner accepts GPL), or the official embedded player.

### D4. JDK: Homebrew `openjdk@17` instead of `temurin@17`
The Temurin cask needs sudo. `openjdk@17` is the same OpenJDK 17, installed without sudo. Undo: `brew uninstall openjdk@17 && brew install --cask temurin@17`.

### D5. Toolchain versions
Gradle 8.13 (wrapper), AGP 8.10.1, Kotlin 2.1.21, Compose Multiplatform 1.8.2, Ktor 3.1.3, kotlinx.serialization 1.8.1, coroutines 1.10.2, Coil 3.2.0, Media3 1.6.1, compileSdk/targetSdk 35, minSdk 26. Chosen as a well-known compatible set that matches the SDK platform installed (android-35). Upgrade later as one step.

### D6. Project layout
`:shared` (Kotlin Multiplatform: `androidTarget` + `jvm("desktop")`, Compose Multiplatform UI in `commonMain`) and `:androidApp` (Activity, ExoPlayer). No `:desktopApp` yet (build step 7); the `desktop` JVM target already exists so shared tests run on the Mac and the desktop app can be added cheaply.

### D7. Package name `app.duet.music`
From the project brief defaults.

### D8. Sources are identified by provider id, not an enum
SPEC §3 lists a `Source` enum, but §3b says the core must only know `SourceProvider` so new sources are just modules. A track carries `sourceId: String` (the provider's manifest id); name and color come from the provider's manifest. No enum to edit when a source is added.

### D9. No DI library yet
One small hand-written `DuetGraph` builds the providers and the player. Koin can come in when there are enough objects to justify it.

### D11. Search screen shows only what works
- The Both / Spotify / YT Music source switch is generated from the installed sources and only shown with 2 or more. Tonight only YouTube Music is installed, so it is hidden; it appears by itself when Spotify is added in step 3.
- The Top/Songs/Artists/Albums/Playlists chips and the "add to queue" buttons from the design are left out until they do something (artist/album search later; the queue is step 4).

### D12. Search result parsing groups subtitle runs on "•"
The web prototype took the first run as artist and the second as album. With several artists ("Daft Punk", ", ", "Pharrell Williams", ...) that made the album ",". Found by the live test; Duet now joins runs between "•" separators. Regression test added.

### D13. Playback without a service for now
Step 2 plays with a plain ExoPlayer owned by the Application. Audio continues when the screen is off for a while, but Android may stop it in the background. MediaSessionService + notification + lock screen is build step 6.

### D14. Library versions left as they are
Lint reports newer AGP (9.5.1), Media3 (1.11.1), activity-compose (1.13.0), Gradle 8.14.7. Not upgraded tonight to keep the first build boring; do it as one separate step (AGP 9 changes the KMP Android plugin).

### D10. No Spotify work
No Client ID yet (the project brief default). Spotify chips/labels are shown in the UI as designed, Spotify search returns nothing until build step 3.

## 2026-11-16 (later): emulator test

### D15. CORRECTION to D3: stream playback does NOT work yet
On the emulator, tapping a result fails with HTTP 403 from googlevideo. Probing the URLs showed that the logged-out ANDROID and IOS player URLs serve only roughly the first 1-2 MB (bytes 0-1023 and 1048576+1 KB give 206; 2,000,000+ and any unbounded request give 403). This is YouTube's PoToken enforcement. The first probes only read the first 64 KB / 1 KB, so they passed falsely. `LiveYtMusicTest` now also reads 1 KB at byte 3,000,000 and fails, as it should, until this is fixed. Search is unaffected.
Fix options (owner decision, see the session summary): NewPipe Extractor (GPL-3.0), PoToken generation (WebView BotGuard, as Metrolist/OuterTune-style clients do), or the official YouTube embedded player.

### D16. WAKE_LOCK permission
ExoPlayer `WAKE_MODE_NETWORK` crashed the app with "SecurityException: ... WAKE_LOCK". Added the permission.

### D17. Emulator
Installed `emulator` and `system-images;android-35;google_apis;arm64-v8a`, created AVD `duet` (Pixel 7). Start it with:
`/opt/homebrew/share/android-commandlinetools/emulator/emulator -avd duet`

## 2026-11-16 (morning, owner awake)

### D18. YouTube streams via NewPipe Extractor; Duet becomes GPL-3.0 (owner's choice)
Owner picked NewPipe over a PoToken generator or the embedded player. Changes:
- InnerTube now only does search. `YtStreamResolver` (commonMain) is implemented by `NewPipeStreamResolver` in a new `jvmShared` source set, shared by Android and desktop. The broken ANDROID/IOS `player` code is removed.
- NewPipe **v0.26.1 returned no audio streams at all** (YouTube's SABR/PoToken changes). **v0.27.1** (2026-11-10) works: the live test reads 1 KB at byte 0 and at byte 3,000,000 (both HTTP 206). On the emulator, a 6:10 song played past 2:45 with no error, and pause/resume worked.
- `LICENSE` is GPL-3.0. Myspoti moves to option (a). See `docs/THIRD_PARTY.md`.
- Expect NewPipe bumps whenever YouTube changes; watch https://github.com/TeamNewPipe/NewPipeExtractor/releases.

### D19. Build step 4 before step 3
Step 3 (Spotify) needs the owner's Client ID. Step 4 does not, so it went first: one mixed-source queue (`PlaybackCoordinator`: next/previous, shuffle, repeat off/all/one, seek, add/remove/skip, auto-advance) and the Now Playing screen. Tapping a search result queues the whole result list starting at that song; the + button appends one song.

### D20. Avatar and gear on Home (owner asked; pattern from Myspoti's profile sheet)
- Avatar: a person icon (no fake initial) that opens an "Accounts" sheet: one row per source with its account state (YouTube Music: "No account needed"), then Settings. Spotify's sign-in and profile picture land here in step 3.
- Gear: Settings page with only working items: accent color (the 4 colors from design/tokens.json, SPEC §4), the installed sources, About (version, GPL-3.0, open-source notices).
- Each source has its own page drawn from its manifest: account, its own settings, capabilities and network hosts (SPEC §3b). YouTube Music has one setting, Data saver, verified on the emulator: Opus 146 kbps → AAC 50 kbps.
- Settings persist with `multiplatform-settings` (Apache-2.0); each extension gets a namespaced store.

### D21. Now Playing gradient without Android's Palette
The dominant cover color is computed in common code (24x24 sample, weighted toward colorful pixels), so desktop gets it too. The gradient is darkened until white text keeps ≥ 4.5:1 contrast. Volume slider, Lyrics and output-device buttons from the design are left out until steps 6/7 (hardware volume keys work).

### D22. Full-screen pages block what is under them
Found on the emulator: with Now Playing open, the tabs underneath stayed in the accessibility tree and could get taps. Pages now block pointer input, and the hidden content is removed from semantics.

### D23. Step 6 (part): background playback and system media controls via Media3 `MediaSessionService`
`PlaybackService` wraps the app's ExoPlayer in a `MediaSession` through a `ForwardingPlayer` so next/previous and the title/artist/artwork come from `PlaybackCoordinator` (the queue owner), not ExoPlayer's single-item playlist. The service calls `addSession` itself because no `MediaController` connects from the UI; without it Media3 posts no notification. Added permissions: `FOREGROUND_SERVICE`, `FOREGROUND_SERVICE_MEDIA_PLAYBACK`, `POST_NOTIFICATIONS` (asked at launch on Android 13+). Verified on the emulator: notification with art and prev/pause/next, foreground service, playback continues on the home screen, the system "next" command advances Duet's queue. Not done yet: showing the Bluetooth output device name in the UI, and Spotify playback in the session (step 3).

### D24. Spotify sign-in the Myspoti way (owner's choice, 2026-11-16)
Owner chose Myspoti's Web Player session over the official PKCE + App Remote route, knowing it uses unofficial endpoints, may break Spotify's terms and puts the account at some risk. Implementation (`androidApp/.../spotify/`):
- `SpotifyLoginActivity` shows `accounts.spotify.com/login?continue=open.spotify.com` (Myspoti's `LOGIN_URL`) in a WebView with a desktop Chrome user agent. The user signs in on Spotify's own page; Duet never sees the password. Signed in = an `sp_dc` cookie and the page reaching open.spotify.com.
- `AndroidSpotifyWebSession` keeps open.spotify.com loaded off screen. A document-start script (androidx.webkit) copies the page's own `/api/token` answers to Kotlin. No TOTP and no secret are computed or extracted (Myspoti also takes the token from the page, `player-bridge.js` `token.get`).
- `SpotifyProvider` (commonMain) uses the token for `api.spotify.com/v1/me` and shows "Signed in as …" on the Accounts sheet and the Spotify source page, which also has Sign in / Sign out.
Not built yet: Spotify search and playback (next: search via the token; playback through the Web Player page needs Widevine in the WebView). Verified on the emulator only up to the login page rendering, because signing in needs the owner's account.

### D25. YouTube Music sign-in the same way (owner asked, 2026-11-16)
Google's own login page (`accounts.google.com/ServiceLogin?service=youtube&passive=true&continue=https://music.youtube.com/`, the address SpatialFlow's `GoogleSignInScreen` uses) in the same full-screen WebView screen as Spotify, now shared as `weblogin/WebLoginActivity` + `WebLoginHost`. Signed in = music.youtube.com sees `SAPISID` and `HSID`. The account name comes from InnerTube `account/account_menu` with the cookies and a `SAPISIDHASH` header (from SpatialFlow `InnerTubeClient.buildHeaders`, Apache-2.0). Sign-out on one site now expires only that site's cookies, so the other stays signed in. Sign-in is optional: search and playback still run logged out. Risk: Google may refuse sign-in inside a WebView ("This browser or app may not be secure"); the desktop user agent is meant to avoid that but is untested past the email page.

### D26. One look and plain language across the app (owner asked, 2026-11-16)
Shared pieces in `ui/components/Guide.kt`: `ScreenHeader` (every tab: title + one sentence saying what it is for), `PageScaffold` (every page over the tabs, the sign-in screens included: back arrow + title + sentence), `HintCard`/`GuideStep`, `EmptyState`, `PrimaryButton`. Home no longer shows the design's sample shelves (they looked tappable and did nothing); it shows a hideable "Getting started" guide (3 steps that tick off), what is playing, search ideas, and a Library card. Search explains itself before you type and on no results; the service switch lists only services that can search. Library has a real empty state. Settings and service pages use everyday words ("Music services", "Highlight color", "Options"); version, id and hosts sit under "Technical details". Player buttons have written labels ("Shuffle off", "Repeat all", "Up next · 20 songs"); "Queue" is now "Up next" everywhere. The sign-in screen moved from a plain Android layout to Compose (`shared/src/androidMain/.../ui/login/WebLoginView.kt`) with a "Loading…" cover.

Open bug (stopped after 3 fixes): on the first Spotify sign-in open, the page loads (form present, full size, visible per JS) but nothing draws. Tried: (1) Myspoti-style paint probe + reload, (2) load the URL only after the WebView is attached, (3) wait for the form field + reload once. Logs: `adb logcat | grep DuetSpotify`. Google's sign-in page draws fine in the same screen.

### D27. Lyrics, more settings, Android Auto, offline downloads (owner asked, 2026-11-16)
- **Lyrics** (`shared/.../lyrics/Lyrics.kt`, `ui/player/LyricsScreen.kt`): LRCLIB, as both Myspoti (`LyricsApi`, GPL-3.0) and SpatialFlow (`LyricsFetchManager`, Apache-2.0) use it: `/api/get`, then `/api/search`, keeping results whose artist, title and length (±3 s) match, synced first. LRC parser and the match rule adapted from Myspoti. Synced lines follow the song; tapping a line seeks. Myspoti's other sources (Spotify color-lyrics, NetEase, Genius, KuGou) are not ported: Spotify's needs Spotify playback, the others are opt-in scrapers.
- **Settings** (from Myspoti's and SpatialFlow's lists, only ones that work today): skip silence, pause when headphones disconnect, keep playing after closing the app, sleep timer, Lyrics button and source line in the player, getting-started tips, download on Wi-Fi only, saved-songs size, delete all.
- **Android Auto**: `PlaybackService` is now a Media3 `MediaLibraryService` (browse tree: Up next, Ideas; voice/on-screen search; "play X" queries), plus `automotive_app_desc.xml` and the `MediaBrowserService` intent filter. Verified the service is registered; not tried in a car or the Desktop Head Unit.
- **Downloads** (`AndroidDownloads`): YouTube Music songs only (new `Capability.Download`), saved in app-private storage with 1 MB range requests, played instead of streaming when present (`PlaybackCoordinator.localCopy`). Verified: a 5:37 song saved whole and played with the network off. **Spotify downloads are not built and won't be**: Spotify audio is DRM-protected (Widevine), and saving it means breaking that protection.
- **Ad blocking**: nothing to build now. YouTube Music audio comes from NewPipe as a plain audio stream, so no ads play. Spotify playback doesn't exist yet, so there are no Spotify ads either.

### D28. Spotify search and playback through the Web Player page (owner asked, 2026-11-16)
- The open.spotify.com page now stays loaded off screen even when signed out; its anonymous token is enough to search.
- **Token**: the page asks for `/api/token` from a worker the document-start hook can't reach, so `shouldInterceptRequest` makes that same request (page headers + session cookies), keeps the token and returns the identical answer to the page.
- **Search**: Spotify answers the public Web API with HTTP 429 for Web Player tokens, and refuses GraphQL from outside the browser ("Client/request not allowed", 403). Duet sends GraphQL `searchTracks` (hash from Myspoti's docs/api/SEARCH-HOME.md) from inside the page (`pageFetch`), with the `client-token`/`spotify-app-version` headers copied from the page's own GraphQL calls. Verified signed out: "instant crush" returns Spotify songs. If Spotify changes the hash, search falls back to the Web API and shows its error.
- **Playback** (`SpotifyWebPlayer`): new `Playable.Remote("spotify:track:ID")`. Duet opens `/track/ID` inside the running Web Player (its router, no reload), presses the song's play button, reads state from the page's audio element (captured by the hook) and the player bar, seeks via the element, and pauses Spotify when it moves on to its own next song so Duet's queue continues. Widevine is granted only to spotify.com pages. Audio stays inside Spotify's player with its DRM. Verified with DevTools on the live page: navigation works, all buttons exist, scripts run. **Not verified: actual playback**, because that needs a signed-in account.
- Gaps: no media notification or lock-screen controls for Spotify songs yet (the session follows ExoPlayer); Spotify songs can't be saved (DRM).

### D29. Saving Spotify songs from YouTube Music (owner asked, 2026-11-16)
Save on a song whose service can't be downloaded (Spotify) looks the song up on a service that can (YouTube Music): search "clean title + first artist", keep results whose cleaned title and first artist match and whose length is within 7 s, closest length wins, exact title preferred (`downloads/SongMatch.kt`, tests in `SongMatchTest`). The YouTube Music audio is saved under the Spotify song, so that song then plays from the saved copy (offline, and without a Spotify sign-in). Spotify's own audio is never downloaded. The player button reads "Save from YouTube Music" for such songs. Verified: Spotify "Get Lucky" (6:09 album version) matched the 6:09 YouTube Music upload, not the radio edit, and played with the network off.

### D30. Spotify sign-in page blank: found and fixed; Google "browser may not be secure"
- Cause (found with DevTools on the live page): the WebView inside Compose's `AndroidView` had the default wrap-content height, so in the page `100vh`/`100dvh` were 0 while `innerHeight` was 690. Spotify's login lays out with viewport units, so `html` was 0 px tall and its content box 48 px; nothing drew. Google's page doesn't use those units. Fix: the WebView gets MATCH_PARENT layout params (`WebLoginView.kt`). The earlier three tries (D26) treated symptoms.
- Spotify's sign-in now uses its own fresh WebView; the hidden Web Player view is never shown.
- Google refused sign-in on the owner's phone ("This browser or app may not be secure"). Android WebView sends `X-Requested-With: app.duet.music` on every request; Duet's web views now send it to no site (`WebSettingsCompat.setRequestedWithHeaderOriginAllowList(emptySet())`, `WebLoginHost.hideAppIdentity`). Whether Google then accepts the sign-in is untested (needs the owner's account).

### D31. Search filters: All / Songs / Albums / Artists / Playlists (owner asked, 2026-11-16, with a screenshot of YouTube Music's chips)
`SearchKind` + `Collection` in the core; sources say which kinds they can search (`collectionKinds`) and list a collection's songs (`collectionTracks`). "All" shows up to 12 songs and 4 of each other kind in sections; a chip shows everything of that kind. Tapping an album, artist or playlist opens `CollectionScreen` (cover, Play all, songs).
- YouTube Music: InnerTube search `params` per kind (SpatialFlow `SearchFilter`, Apache-2.0), browse pages by browseId; album rows' lengths come from the fixed column. Live test: `DUET_LIVE=1 ./gradlew :shared:desktopTest --tests '*LiveYtMusicTest*'`.
- Spotify: Web Player GraphQL from inside the page: `searchAlbums`/`searchArtists`/`searchPlaylists`, `getAlbum`, `fetchPlaylist`, `queryArtistOverview` (hashes from Myspoti docs/api/SESSION.md, GPL-3.0). These hashes change when Spotify updates the Web Player; then those lists come back empty.
Verified on the emulator with "teeth": albums, artists and playlists from both services, and their song lists open.

### D32. A look like real music apps (owner: "so obvious it's AI"); sign-in identities
- Looked at Metrolist (GPL-3.0, cloned to ~/prior-art/Metrolist): 64 dp rows, 48 dp art, 4 dp art corners, shelves from YouTube Music's own home feed, near-black theme, no help text. SpatialFlow, Spotify and Apple Music patterns: greeting + "jump back in" grid, card shelves, colored browse tiles, icon-only player controls, white play button.
- Removed: tip cards, the numbered getting-started guide (and its setting), sentences under screen titles, labelled player toggles. Neutral near-black palette; default accent coral. Chips: white when selected.
- Home: greeting, last six songs in a two-column grid, "Saved on this phone", "Recently played", and YouTube Music home shelves (`browse FEmusic_home`, logged out: e.g. "Hits by decade", "Music for any mood"). `PlayHistory` keeps the last 50 played songs.
- Search: colored Browse tiles before typing. Player: icon row (Lyrics, Save, Up next), white circular play button. Mini player: Next button.
- Sign-in fixes after the owner's phone test: Spotify said "Something went wrong" and Google said "may not be secure". The WebView announced a desktop Linux user agent but Android-WebView client hints. Spotify's views now send matching desktop client hints (`WebSettingsCompat.setUserAgentMetadata`, brand list per Myspoti `Fingerprint`); Google's login uses the phone's own WebView identity, as Metrolist does. Both still need a test with the owner's accounts.

### D33. Cinematic look (SpatialFlow) with Metrolist's usability; YouTube-only uploads
- Owner prefers SpatialFlow's cinematic style (studied its AppScreenShot/*.png) with Metrolist's usability. Player: the artwork fills the screen, a fade in the art's colour rises from the bottom, controls sit on a glass card (black 38 %, 1 dp white 10 % border, 28 dp corners). Home: search pill + mood chips at the top, small greeting over a big headline (signed-in name or "Listen now"), the first YouTube Music shelf as large landscape cards with the title over the art, a glow tinted by the last song's art. Mini player: tinted with the playing song's art colour. Lists, sizes and controls stay Metrolist-like.
- Search has a Videos kind (YouTube Music search filter `EgWKAQIQAWoKEAkQChAFEAMQBA%3D%3D`): any YouTube upload, e.g. remixes that are only on YouTube. "All" shows up to 4 videos. Live check: "instant crush remix" returns DGTO, Veridis, PMT remixes and mashups.

### D34. Sign-in pages use the phone's own browser identity (minus "; wv")
Reproduced the owner's Spotify "Oops! Something went wrong" on the emulator with a made-up email and DevTools network logging: reCAPTCHA Enterprise's `clr` call was aborted and `POST accounts.spotify.com/login/code/send` answered 400, i.e. reCAPTCHA did not trust the WebView posing as desktop Chrome. With the phone's own WebView user agent minus the `; wv` marker (and no X-Requested-With), Spotify answered "Email isn't linked to a Spotify account" (the correct answer for that address) and Google answered "Couldn't find this account" instead of "This browser or app may not be secure". The hidden Spotify Web Player keeps the desktop identity with matching client hints (D32). Still to confirm with real accounts on the owner's phone.

### D35. Prettier lyrics, gradient player, Home service switch (owner asked, 2026-11-16)
- `ArtBackdrop`: the art colour and a companion colour (hue turned 40°, `companionColor`) drift slowly as two radial glows over a deep vertical gradient; the player lays the artwork over its top part. The glass card is tinted with the song's colour.
- Lyrics: full-screen over the backdrop, 30 sp extra-bold lines, current line bright and 4 % larger, past lines faded, the current line kept in the upper third, small art + title header.
- Home: Both / YT Music / Spotify switch filters Recently played, Saved and shelves (`Shelf.sourceId`). Spotify shelves come from the Web Player's GraphQL `home` (Myspoti docs/api/SEARCH-HOME.md), signed out too ("Popular artists").

### D36. Synced libraries, Downloaded and Cached (owner asked, 2026-11-16)
- `LibraryModel` loads every signed-in service's library at start, whenever a sign-in changes, and on "Sync now". Spotify: GraphQL `libraryV3` (Liked Songs pseudo-playlist, playlists, albums, artists) and `fetchLibraryTracks` for Liked Songs (hashes from Myspoti `gql-ops.json`). YouTube Music: Liked music (`VLLM`) and the library page `FEmusic_liked_playlists`; all InnerTube calls carry the Google session cookies + SAPISIDHASH while signed in, so private playlists open too. Not verified: needs the owner's accounts.
- Library: Both / YT Music / Spotify switch (now shared with Home as `ServiceSwitch`), Downloaded, Cached, then synced playlists, albums and artists; "Sync your playlists" row when signed out.
- Cache: Media3 `SimpleCache` (1 GB, LRU) under `cacheDir/media`, entries keyed by the song (`Playable.Stream.cacheKey`), not the changing stream URL. A song played to the end is complete; then `PlaybackCoordinator` plays it from the cache without resolving it (works offline; test `cachedSongPlaysFromCacheWithoutResolving`). "Cached" lists played songs that are complete in the cache. Settings > Cache shows the size and clears it. Verified: One More Time played through, appeared in Cached, played with the network off. Spotify songs are never cached (DRM).

### D37. "Spotify songs aren't loading": the hidden Web Player now lives in the window (Myspoti's AwakeWebView)
The off-screen Spotify page was never attached to a window. Myspoti attaches its player page to the activity (INVISIBLE) and uses `AwakeWebView`, which tells Chromium the view and window are visible and holds back the detach when the activity goes, because Chromium throttles or stops a page it sees as hidden. Duet now does the same (`spotify/AwakeWebView.kt`, `AndroidSpotifyWebSession.attachHidden`, called from `MainActivity`), full size so Spotify's page lays out normally. Checked signed out with DevTools: the page is 800×1776, `visibilityState` visible (also with the app in the background); opening a song and pressing its play button reaches Spotify (it answers with its log-in prompt). Also: a song counts as started when the page's audio plays and the player bar names no other song (it no longer needs the bar to name it). Real playback still needs the owner's account to confirm.

### D38. Quick picks, Home section switches, Search start page + search screen, Android Auto layout (owner asked, 2026-11-16)
- Quick picks (YouTube Music style: song rows, 4 per column, swiped sideways): YouTube Music's radio for the last played song (InnerTube `next`, playlist `RDAMVM<videoId>`; another service's song is first matched on YouTube Music), minus songs just played. A service's own "Quick picks" shelf is dropped while Duet's has songs (`HomeModel.visibleShelves`).
- Settings > Home: a switch for Quick picks, Jump back in, Saved, Recently played, and for every shelf the services offer (e.g. Spotify's "Popular radio", "Made for you"); stored as hidden section ids.
- Search (owner's mid-turn request): the tab opens on a search bar plus Recently played songs; tapping the bar opens the search screen with a back arrow, autofocus and Recent searches (`SearchHistory`, 20, tap to search, × to remove, Clear). The service switch and kind chips only appear once something is typed. Browse tiles removed.
- Android Auto: root tabs Home / Recent / Library / Up next; root hints: search supported, folders as grids, songs as lists; Home = Quick picks + every shown shelf; shelves list songs and album/playlist folders; Library = Downloaded, Cached, synced collections; collection folders load their songs; every item has artwork through `auto/ArtworkProvider` (content:// proxy for YouTube Music/Spotify image hosts only, read permission granted to Android Auto only). Verified with an on-device `MediaBrowser` test (`androidApp/src/androidTest/.../AutoBrowseTest.kt`, `./gradlew :androidApp:connectedDebugAndroidTest`): tabs, search flag, grid hint, Home shelves with art. Not tried in a car or the Desktop Head Unit.

### D39. Sound settings, fades, spatial audio, stop on swipe-away, animations (owner asked, 2026-11-16)
- `SoundSettings` (common) + Settings > Sound: 5-band equalizer with presets, bass boost, loudness, reverb presets, stereo balance, speed, fade between songs, spatial audio, streaming quality per network (Wi-Fi / mobile data: High or Data saver; replaces YouTube Music's own Data saver switch).
- Android: `audio/SoundEffects` applies Equalizer (our 5 bands mapped to the phone's bands by nearest centre frequency), BassBoost, LoudnessEnhancer on ExoPlayer's audio session and PresetReverb as an aux effect; `audio/BalanceProcessor` is an ExoPlayer AudioProcessor (16-bit stereo); speed via `setPlaybackSpeed` (and the Spotify page's audio element `playbackRate`); spatial audio via `AudioAttributes.setSpatializationBehavior(AUTO)`; fades: volume ramps over the first and last N seconds of each song. Effects other than speed apply to YouTube Music only: Spotify plays inside its own page.
- Not built: lossless (YouTube serves no lossless audio; Spotify's web player has none) and Dolby Atmos (neither source sends Atmos to these clients). True overlapping crossfade needs a second player; fades are the current form.
- Swiping Duet away from recents now stops all playback (Spotify too) by default; "Keep playing after closing the app" turns that off (new key `play.keepPlaying.v2`, default off). Verified: after a swipe the session is PAUSED and no audio plays.
- Animations: tabs cross-fade, pages slide in/out (player and lyrics from the bottom, others from the side), mini player slides in/out, the play/pause icon morphs, the player artwork cross-fades on song change.

### D40. Radio section at the bottom of Home, with an on/off switch (owner asked, 2026-11-16)
One station per artist among the last songs played ("<Artist> Radio, Based on <song>", up to 8); tapping plays the seed song, then YouTube Music's radio for it (`HomeModel.radioFor`, matched there first for other services' songs). Settings > Home > Radio turns the section off (`HomeSection.Radio`). Android Auto: Home > Radio lists the same stations; each opens its songs. Verified: Daft Punk Radio played Instant Crush, then Get Lucky on Next; the switch hides the section.

### D41. Real crossfade (SpatialFlow's method), clearer Radio, car mode (owner asked, 2026-11-16)
- Looked in SpatialFlow for lossless and Dolby: it has neither (no code for FLAC/hi-res sources, E-AC-3/AC-4, or Atmos); nothing to copy. Its crossfade (`service/AudioPlaybackService.kt`, Apache-2.0) is two ExoPlayers: the next song on a second player at volume 0, volumes crossed when the time left equals the crossfade length, then the session moves to the new player. Duet now does the same: `PlaybackCoordinator` starts the next song early when both songs are streams (tests `crossfadeStartsNextSongEarlyOnTheSamePlayer`, `noCrossfadeIntoASourceThatIsNotAStream`); `ExoStreamPlayer.crossfadeTo` builds a second player, crosses volumes, moves listener, effects (`SoundEffects.retarget`) and the media session (`onPlayerChanged`) to it, releases the old one. The outgoing player stops handling audio focus first, otherwise it paused itself when the new one took focus (found on the emulator: no overlap until that fix). Verified: 2 audio players started during the change, then 1.
- Radio: a gradient banner ("Radio, Endless music like what you play", big play button) above larger station cards at the bottom of Home, shown whenever Settings > Home > Radio is on.
- Car mode (Home car button, or Settings > Car mode): full-screen, large now-playing + 112 dp play button + big tiles (Quick picks, Radio if on, Recent, Downloaded) with 84 dp song rows; side by side in landscape, stacked in portrait; keeps the screen on. MainActivity now handles rotation itself (configChanges), so open pages survive turning the phone.

## D42 (2026-11-16): In-app updates and three apps
- Duet checks a public Google Doc feed (JSON line) on every launch and in Settings > About; the APK comes from a public Drive link and installs through the system installer (one tap; Android does not allow silent installs).
- Owner asked for Owner / Tester / normal versions: built as product flavors `owner` / `beta` / `normal` (Gradle forbids flavor names starting with "test"), separate packages so they install side by side, one feed each.
- Duet Owner shows each feed's version and has "Send to testers" / "Release to everyone", backed by a Google Apps Script web app (docs/release-script.gs) holding a key that only Owner builds carry (local.properties `duet.ownerKey`). Not deployed yet by the owner.
- No in-app bug report: owner said testers message him directly.
- See docs/RELEASING.md.

### D43. Release key, Settings menu, owner's calls on ad blocking and downloads (2026-11-17)
- Release builds are signed with the Duet release key (`~/.android/duet-release.jks`), never the debug key;
  the updater refuses an APK signed with another key. Old debug-signed installs reinstall once.
- Settings became a menu of grouped pages (owner's reference screenshots).
- Owner wants **ad blocking** (reverses D1's reading) and **Spotify downloads through YouTube Music**
  (match the song on YT Music, download that). Not built yet.
- Always build and install Duet Owner first.

## D44: Five app styles (2026-11-17)
- Settings > Appearance > Style: Midnight (default), Paper (light, serif), Terminal (mono, square),
  Bubblegum (light, very round), Synthwave. A style sets colours, corner roundness (`duetShape`),
  font (system Serif/Monospace, no new font files) and picks its own accent; the accent can still be changed.
- Now Playing and Lyrics stay dark in light styles because they draw on the artwork (`OnArtTheme`).
- Status bar icons follow the style (`SystemBarIcons`). The sign-in screen stays Midnight.
- Layouts are shared by all styles; only the look changes.

## D45: Minimal, cinematic redesign; five cinematic styles (2026-11-17)
- Owner chose "very minimal, cinematic, something I can make styles of". One shared layout:
  Home opens on the last song's artwork full-bleed with its title in a serif display face and
  Play / Radio; text-only tabs with a short underline; Now Playing without the glass card.
- D44's five colour styles were replaced by five cinematic ones: Cinema (default), Noir, Ambient,
  Film, Night. New tokens on `DuetStyle`: `display` (title face, Instrument Serif) and
  `colorFromArt` (false keeps player, mini player and Library tiles neutral black).
- Future styles (owner's note: "liquid glass", Nothing OS-like) are meant to be added as new
  `DuetStyle` entries; they will likely need more tokens (glass blur surfaces, a dot-matrix face).
  Light styles still work (`isLight`, `OnArtTheme`, `SystemBarIcons`).
- Default accent for new installs is Cinema's warm white.

## D46: Live radio; Cinematic + Nothing redesign (2026-11-18)
- Live internet radio from radio-browser.info as a source plug-in (`RadioProvider`, capability
  `LiveRadio`): Radio tab with most-listened, genre chips and station search. Plain streams only
  (HLS stations skipped). Kept out of song search and the service switches (`SourceRegistry.music`).
  Plays through the normal queue; Now Playing shows LIVE.
- Owner's mockups: one layout, two styles. Cinematic (warm black, gold, serif, blurred artwork
  behind the player) and Nothing (graphite, Doto dot-matrix titles, dotted rings, lower-case
  section titles). Noir/Ambient/Film/Night removed. New tokens: `dotted`, `lowercaseTitles`.
- Home: greeting + serif headline, Recently played cards, wide cards for the first service shelf,
  Quick picks, Radio mixes, Jump back in list. Now Playing: art card, ring play button, action card,
  Up next. Four tabs (Home, Search, Library, Radio). Press-scale on cards and buttons.
- Everyone is moved to Cinematic with its gold highlight once (`resetLookOnce(2)`).

## D47: Features from Metrolist, SpatialFlow and Orpheon (2026-11-18)
- Phone was locked overnight, so the three apps' installed APKs were pulled from the phone and run on
  the emulator; their features were listed from the apps and their READMEs/source (`~/prior-art`).
- Added: song menu (long press any song: Play next, Add to queue, Start radio, Download / Remove
  download); Play next and Move up in the queue (`PlaybackCoordinator.playNext`, `moveInQueue`);
  History page; Your stats (top songs and artists from play counts, `PlayHistory.counts`); live radio (D46).
- Already in Duet: equalizer, bass, loudness, speed, crossfade, skip silence, sleep timer, synced
  lyrics, downloads, car mode, Android Auto, in-app updates, data saver.
- Not added (needs owner accounts/keys, or large): Last.fm/ListenBrainz scrobbling, Discord presence,
  Listen Together, casting, Shazam recognition, playlist import from other services, lyrics translation,
  Genius meanings, local audio files, music videos, widgets, word-by-word lyrics.

## D48: Radio by country, plain-http streams, YouTube Music-style home cards (2026-11-18, v0.15.1)
- Radio opens on the phone's own country (from the system locale); country chips (with flags) list
  your country first, then the rest by station count, plus Worldwide. Genre and country combine
  (radio-browser `stations/search` with `tag` + `countrycode`).
- Many stations only stream over `http://`, which Android blocked ("Cleartext HTTP traffic ... not
  permitted"). Owner reported "error not permitted". Cleartext is now allowed app-wide
  (`usesCleartextTraffic`); Duet's own API calls all use https, so only station streams use it.
- Home: "Recently played" became YouTube Music's "Listen again" (two rows of smaller cards); cards are
  140dp with small rounded corners, two-line titles, centred titles under round artist pictures.
- Cinematic style now uses the plain sans (Plus Jakarta Sans) for titles instead of Instrument Serif
  (owner: "do smth plain").

## D49: Favourite stations, region picker, car auto-start, one look everywhere (2026-11-18, v0.16.0)
- Stations can be favourited (heart on each row, the Now Playing heart, and the song menu). Favourites
  sit at the top of the Radio tab; stored on the phone (`FavouriteStations`, key `radio.favourites`).
- Region: one button opens a sheet listing every country (yours first, then by station count) with a
  search field. Worldwide is the first entry.
- Settings > Player > "Play a favourite station in the car" (off by default): when Android Auto
  (`com.google.android.projection.gearhead`) connects and nothing is playing, the favourite listened to
  most recently starts, with the other favourites queued. Runs once per service start so a station you
  paused is not restarted on a mid-drive reconnect.
- Search uses the same quiet Both / YT Music / Spotify switch as Home and Library; its add button is a
  plain icon. Section titles everywhere go through `sectionTitle()` so styles (Nothing's lower case) apply.
- Now Playing: the quality / LIVE tag is pinned to the true centre. A station that refuses its stream
  shows "This station isn't on the air right now" instead of an ExoPlayer error code.
- Updates: APKs go in the Drive folder "Duet updates" (`<id>`), which the
  owner shares by link once; files inside inherit that, so the feed can be updated without adb.

## D50: Spotify playback commands leave from the app (Myspoti's ControlRelay) (2026-11-18, v0.16.1)
- Cause found in Myspoti/Orpheon's source (`host-android/.../ControlRelay.kt`): every WebView request
  carries `X-Requested-With: <package>`, which apps can no longer remove. Spotify answers Connect
  commands that carry it with 200 but never delivers them to the player. That is Duet's symptom
  exactly (command POST 200, then nothing).
- Fix: `androidApp/.../spotify/SpotifyControlRelay.kt` wraps the page's `fetch` at document start; requests
  to `connect-state/v1`, `track-playback/v1` (and play reports once the device is registered) go to the
  app through a `DuetNet` web-message listener limited to https://open.spotify.com, are sent with OkHttp
  using the page's own headers plus Chrome's, and the answer is handed back to the page. A 402 on a
  command is retried once from the page itself (some free accounts). Re-implemented from Myspoti's
  ControlRelay (GPL-3.0), without its route fallbacks, ad handling and diagnostics.
- Not verified yet: needs a signed-in Spotify account (none on the emulator; owner's phone not connected).

## D51 (2026-11-18): Android Auto rework, "Mixes", Assistant voice (v0.16.2)
- Owner, from car photos: no internet radio in Android Auto, song "Radio" confusing, car UI too hard to get
  around, Duet not offered for "Hey Google, play ...".
- Song radio renamed "Mixes" everywhere ("<Artist> Mix", "Start mix"; setting id unchanged). "Radio" now
  only means internet radio.
- Android Auto tabs: Home, Radio, Recent, Library. Up next dropped (the player's Queue button shows it).
  Home lists quick picks inline (one tap), then Mixes and shelves under a "More" heading. Radio is a grid
  of station logos in groups: Favourites, Recently played, Popular in <phone's country>.
- Voice: `VoicePlayActivity` (invisible, own task) handles MEDIA_PLAY_FROM_SEARCH; Android Auto's voice
  goes through the session's playFromSearch (already advertised). Both call `playFromVoice`: favourite or
  recent station by name, else a station named exactly that, else songs, else any station.
- Limit: Assistant's default music provider list ("play X" without "on Duet") is Google's partner list;
  an app cannot add itself. In the car, saying "play X" while Duet is the open media app may route to it.
- Car UI and car voice not verified (no head unit); phone voice intents verified on the emulator.

## D52 (2026-11-18): Spotify ads and fast skipping, copied from Myspoti (v0.16.3)
- Owner: ads play on Spotify; skipping fast plays every song for about a second. Wants ads gone "whatever it takes".
- Ads (Myspoti docs/api/ADS.md, GPL-3.0): `SpotifyAds` refuses the ad audio hosts (WebView
  `shouldInterceptRequest`, 403) and rewrites every `track-playback` state reply so a song whose `advance`
  leads to an ad uses its `skip_next` instead. If `skip_next` leads to the ad too, `SpotifyWebPlayer` ends
  the song 1.5 s early so Duet's queue starts the next one. Not verified: needs a free account playing ~15 min.
- Fast skipping: `SpotifyWebPlayer.play` waits 350 ms and only sends the song you stop on.
- Now Playing: the download button beside the title is gone, and the action row says "Save".
- Releases page showed 0.13.3 because the owner feed doc was never moved on while the Drive folder was
  private; it is now at 0.16.2.

## D53 (2026-11-18): Mixed playlists and Home shortcut tiles (v0.16.4)
- Owner: playlists that hold Spotify and YouTube Music songs together; small rectangle tiles like
  Apple Music / YouTube Music quick picks, "only for some pieces"; ideas from SpatialFlow (its Speed dial grid).
- `Playlists` (on the phone, Settings keys `playlists` + `playlist.<id>`): create, add (once), remove,
  delete. Song menu > Add to playlist (pick one or make a new one). Library > Your playlists (New playlist;
  long press to delete). A playlist opens in the usual song list, with a remove button on each song.
- Home: up to six small tiles at the top, two to a row (your playlists first, then recent songs), with a
  springy press. Quick picks moved up under Recently played. Other shelves unchanged.
- Verified on the emulator: tiles, new playlist from Library, adding a song from the menu, playlist page.

## D54 (2026-11-18): First-run pass, "a 12-year-old just installed it" (v0.16.6)
- Walked a fresh install of Duet (normal) on the emulator. Fixed what a new user trips on:
  - Search showed each song twice (once per service) and showed Spotify songs while signed out, which
    can't play. Now a song both services have shows once (from the one that can play it), and signed-out
    services aren't searched.
  - The Both / YT Music / Spotify switch shows only when two services are signed in.
  - Home header: the unlabelled car icon is gone (Car mode is in Settings). Avatar and gear remain.
  - Library hides empty rows (Downloaded, History, Your stats appear once they have something);
    "Cached" is now "Played offline".
- Spotify Now Playing: time, play state and seeking come from Spotify's own player (0.16.5); the page's
  media element was a short helper clip, so it showed 0:07 as the song length.

## D55 (2026-11-18): Artist pages (v0.16.9-0.17.0)
- Tap the artist under a song in Now Playing, or "Go to artist" in the song menu, to open their page
  (`findArtist`: the song's own service first, then the others; never for radio).
- New artist page (`ArtistScreen`, `SourceProvider.artistPage`): picture across the top with the name and
  Play, Popular songs (5, Show all), then rows. YouTube Music: the artist channel's carousels (Albums,
  Singles & EPs, Videos, Featured on, Fans might also like...). Spotify: `queryArtistOverview` (banner,
  top tracks, Albums, Singles, Fans also like, monthly listeners; Myspoti docs/api/CATALOG.md).
  Falls back to the plain song list if a source has no page. Spotify's page is tested on a fixture only.

## D56 (2026-11-18): The "what's missing" list (v0.18.0)
- Like (heart): Now Playing and the song menu. Kept on the phone (`LikedSongs`, Library > Liked songs,
  a Home tile) and sent to the song's service when signed in: Spotify `addToLibrary`/`removeFromLibrary`
  (Myspoti docs/api/LIBRARY.md), YouTube Music InnerTube `like/like` / `like/removelike`.
- Now Playing: the top-right ⋯ opens the song menu (Queue keeps its button in the action row).
- Song menu: Like, Share (system share sheet with the song's web link), Go to artist.
- Playlists (`PlaylistScreen`): Add songs (search sheet, add with one tap), Edit (rename, move up/down,
  remove). "Save to my playlists" on any service album or playlist copies it into a Duet playlist.
- Album and playlist pages: a big cover, song count and length, Play all and Shuffle.
- Search: YouTube Music's typing suggestions as chips (`music/get_search_suggestions`).
- First launch: a six-slide skippable tutorial ending with "Connect Spotify or YouTube Music"; Settings >
  About > Show the tutorial.
- Animations: drag the player down to close it, pages open on a spring, Back animates out, the app sinks
  back and dims behind the player.
- Home screen widget: cover, title, previous / play-pause / next (Play starts recent songs if nothing's queued).
- Scrobbling: ListenBrainz with the user's own token (Settings > Playback). Last.fm needs an API key and
  secret registered by the owner, so it's not added.
- Not done: Spotify sound effects/speed/crossfade (Spotify's audio never reaches Duet's player; no media
  element or audio session is reachable), Chromecast (Google Cast SDK, and Spotify songs can't cast),
  syncing Duet playlists back to the services, an Android Auto test (no head unit here).
- Spotify downloads via YouTube Music already existed (AndroidDownloads + SongMatch).
- Verified on the emulator: everything above except the Spotify like (no Spotify account there).

### D57. Streaming quality, Spotify songs from YouTube Music, "applies to" labels (2026-11-19)
- The owner asked for SpatialFlow's lossless / Dolby Atmos and quality settings. The public SpatialFlow source
  (last commit 29 Oct) has no lossless or Atmos code: it has only "Streaming Quality" High / Normal / Data Saver.
  The owner's SpatialFlow 1.8.2 has a "Lossless (FLAC via Source Pool)" screen with Deezer (ARL), Tidal, Qobuz
  and JioSaavn, but that code isn't published. Not built: Deezer full tracks are encrypted (playing them means
  breaking Deezer's protection), and source pools of that kind run on proxy servers using other people's paid
  accounts. YouTube serves no lossless or Atmos audio, and Spotify's web player has no quality control.
- Added: Normal (the middle bitrate) next to High and Data saver, per network (`StreamQuality.pick`). The
  `Quality` enum is stored by ordinal, so Normal goes at the end and `Quality.shown` sets the on-screen order.
- Added: Settings > Sound > Spotify songs > "Play from YouTube Music" (off by default). The coordinator's
  `standIn` finds the same song on YouTube Music with `SongMatch` and plays that stream, so quality, equalizer
  and effects apply; the queue still shows the Spotify song, and the quality line says "From YouTube Music".
  No match, or a failed lookup, plays the song from Spotify. Crossfade still only runs between YouTube Music songs.
- Each Sound section now says what it applies to (YouTube Music and radio, or all songs when the Spotify
  switch is on). The old footnote claiming speed works on Spotify was wrong (see D56) and is gone.
- Skipped "Force Opus": Duet already takes the highest-bitrate stream, which is Opus unless signed in with Premium.

### D58. Editing your Spotify and YouTube Music playlists (2026-11-19)
- The owner couldn't add songs to their existing (service) playlists: "Add to playlist" only listed Duet playlists.
- `SourceProvider.addablePlaylists` / `addToPlaylist` / `editPlaylist(PlaylistEdit.Remove | MoveUp | Rename)`.
  "Add to playlist" now also lists your playlists on the song's own service (a Spotify song only into Spotify
  playlists). A service playlist page has "+ Add songs" (searches that service only), Edit (move up / down,
  take out) and Rename, when you're signed in. The service decides what you may edit: followed playlists are
  listed too, and a refused change says so on the page.
- Spotify: GraphQL `addToPlaylist`, `removeFromPlaylist`, `moveItemsInPlaylist` (one document, one hash) and
  rename via `spclient.wg.spotify.com/playlist/v2/playlist/{id}/changes` (Myspoti docs/api/LIBRARY.md and
  SpotifyLibrary). Rows are addressed by `uid`, so the playlist is read again before each remove/move and the
  row checked against the song shown. Untested: no Spotify account on the emulator.
- YouTube Music: InnerTube `browse/edit_playlist` with ACTION_ADD_VIDEO / ACTION_REMOVE_VIDEO /
  ACTION_MOVE_VIDEO_BEFORE / ACTION_SET_PLAYLIST_NAME (SpatialFlow InnerTubeClient). Rows need
  `playlistSetVideoId`, now parsed into `YtSong.setVideoId`. Untested live: the emulator isn't signed in.
- Only the first 100 songs of a Spotify playlist (and the first page of a YouTube one) load, as before.

### D59. Deleting playlists, removing items from Library (2026-11-19, v0.18.2)
- Duet playlists could already be deleted by a long press in Library, which the owner didn't find. Their page
  now has Edit > "Delete playlist" (Edit shows on empty playlists too), with a confirm (`ConfirmDialog`).
- Service items: `SourceProvider.canRemoveFromLibrary` / `removeFromLibrary`, `LibraryModel.remove` (drops the
  row once the service took it). Long press a Spotify or YouTube Music item in Library, or Edit > "Delete
  playlist" on your service playlist's page. The dialog says a playlist you made is deleted on every device and
  one you follow is unfollowed.
- Spotify: GraphQL `removeFromLibrary` with the playlist / album / artist URI (the Web Player's "Remove from Your
  Library"; for your own playlist that is Spotify's delete). YouTube Music: `playlist/delete` (own playlists),
  and when YouTube refuses it, `like/removelike` with the playlist as target (saved playlists). Liked songs /
  Liked music can't be removed. Untested signed in (emulator has no accounts); Duet playlist delete verified.

### D60. Marquee: the new default look (2026-11-19, v0.19.0)
- Owner picked direction D "Marquee" from the mockups (design/redesign-2026-11-19), asked for it rounder, the
  playing song taking over the whole player, no circles, and icons instead of words on the player.
- Built as a `DuetStyle` (`marquee = true`), first in the list and switched on once for everyone
  (`resetLookOnce(3)`); Cinematic and Nothing stay in Settings > Appearance.
  - Titles in Anton (SIL OFL, docs/THIRD_PARTY.md) and capitals (`displayTitle`, `sectionTitle`).
  - `Modifier.duetGround()`: the playing song's artwork colour at the top fading to black by two thirds
    (`LocalGroundTint`, animated); plain ground when nothing plays. Used by the app root and every page.
  - Roundness 2.2 (pills and soft cards). Ring buttons draw no ring.
  - Now Playing: the cover fills the screen, a dark fade under the title, seek bar and icon controls; play /
    pause is a white rounded rectangle; save, lyrics and queue are plain icons.
- Left out from the mockup: film grain (a full-screen noise layer on every frame isn't worth the battery).

### D61. Marquee only, a quieter cinematic finish (2026-11-19, v0.20.0)
- Owner: "too rounded", "the buttons really stick out", the Both / YT Music / Spotify row "a bit funny", make it
  "less AI looking", and remove Cinematic and Nothing from Appearance. Asked before starting (owner then slept):
  the service row becomes small chips with each service's mark; the slogan "Let the music take over." goes,
  the poster headline is the greeting.
- Design review (references: Metrolist and SpatialFlow on the emulator, designer write-ups on the "AI look":
  one deliberate radius scale instead of pills everywhere, no rainbow icon tiles, no glowing CTAs, consistent
  controls). Changes:
  - Roundness 2.2 to 0.5 (cards 6dp, tiles 4dp); Material sheets, dialogs and menus get the same scale (12dp max).
  - Play / pause: a smoked-glass tile with a hairline instead of the white block. Mini player: plain icons.
    Seek thumb: a thin upright tick. Toggle "on": a short dash. No circles left on buttons.
  - Chips (genres, sound presets, service switch, edit actions): slim outlines; selected = faint fill + bright edge.
  - Service switch: "All", YouTube Music (ring with play mark), Spotify (ring with sound arcs), in the service colour.
  - Library and Settings icons: one neutral glass tile, the right glyph per row (History clock, Stats bars,
    New playlist plus, Sound sliders, Appearance), instead of coloured gradient squares.
  - Song-colour ground dimmed (55% at the top, black by 60%), so screens aren't a block of colour.
  - Radio: heading "Most listened" (the region button names the country); "ON AIR" tag only on the playing station.
  - Account button: a plain icon, no filled disc.
- Removed: the Cinematic and Nothing styles, their flags (dotted rings, lower-case titles), Doto and Instrument
  Serif fonts, and the old non-Marquee player. Everyone is moved to Marquee once (`resetLookOnce(4)`).
- Bug: card subtitles lost their first letter ("ua Lipa") because the whole card was clipped to a 26dp round
  corner. The card no longer clips; only the artwork does.

### D62. Features from Metrolist, Android Auto, crash fix (2026-11-19, v0.20.0)
- Player: a sleep-timer moon beside lyrics/queue (Off, 15-90 min; was only in Settings). Queue sheet: "+" saves
  the queue as a Duet playlist, "x" clears the songs after the playing one (`PlaybackCoordinator.clearUpcoming`).
- Android Auto: Library now lists Liked songs and Duet playlists first (they were missing). Like and Shuffle
  buttons beside the car's transport controls (media session custom layout; also in the notification); radio
  shows no Like.
- Crash on the owner's phone (18 Nov 20:58): `ForegroundServiceDidNotStartInTimeException` from
  PlaybackService. Media3 1.10.0 detects the stale foreground-start intent behind it, so media3 1.6.1 to 1.10.1;
  that needs compileSdk 36 (targetSdk stays 35).
- Looked at and skipped: Listen together, Discord status, lyrics romanisation (niche); backup/restore (needs a
  file picker flow; worth doing next); mood chips on Home (YouTube Music's "moods" shelf already shows).
- Not tested: Android Auto on a real head unit (no car here); the Like/Shuffle buttons were checked in the
  emulator's notification.

### D63. The owner's Look Studio look replaces Marquee (2026-11-19, prototype)
- The owner designed this look in Duet Look Studio (design/look-studio, DUETLOOK code in the commit message
  of this change) and asked for it as the default. It replaces Marquee outright (Marquee stays in git at
  v0.20.0); the one style is `studio`, and `resetLookOnce(5)` moves everyone onto it with the white highlight.
- Look: Instrument Sans (variable font, OFL) for everything, bold titles slightly tightened, no capitals;
  ground #140a12 with the playing song's colour as an 80% wash at the top fading out by 62%, under a soft
  white glow from the top edge; corners scaled 1.4x (cover 24dp); highlight white, only on on/off states.
- Home: plain "Good evening" header (the signed-in name line is gone), tiles + rows, 140dp cards, service
  switch as plain words with an underline. Now-playing bar: card in the song's colour, previous/play/next,
  a 2dp progress line along the bottom edge. Tabs: icons only, flat.
- Player: the cover blurred over the screen (Android 12+; older phones get the colour gradient), the cover
  edge to edge in the middle with a deep shadow, title left with the heart, a 6dp seek bar without a knob,
  a large outlined play button, icons row (save, lyrics, sleep, queue). The studio draws "Outline square"
  with fully rounded ends at 18px corners, so the play button is a stadium outline, as the owner saw it.
- Sizes: the studio phone is 284px wide, a phone ~390dp, so studio pixels were scaled ~1.3-1.4x.

### D64. Seven styles, changes inside a style, dark and light, song-colour gradients (2026-11-20, prototype)
- Owner asked for a Nothing OS style, an "Apple Music upgrade" style, and the Look Studio presets as styles
  that "all run the same just with their own UI look", changeable by people. Settings > Appearance > Style:
  Studio (D63, unchanged default), Nothing, Airy, Shelf, Night, Mono, Glass. Every style uses the same screens;
  a style is data in `DuetStyle` (ui/theme/Theme.kt): fonts, caps or light titles, text size, corners,
  song-colour wash, glow, highlight reach, Home layout (tiles/list/big cards), mini player colour and buttons,
  tab bar (flat/floating, names), player background (blurred/plain/full cover), play button
  (icon/outline/filled/glass), seek bar (line/bar/dots), player margins.
- Nothing and Airy are the owner's DUETLOOK codes (2026-11-20). Shelf/Night/Mono/Glass follow the Look Studio
  presets. Look Studio got a Nothing preset (Doto, Space Mono, dot grid, dotted seek, #d71921).
- Changes inside a style: Font, Corners, Home, Player, Play button, Seek bar, Tab bar, stored as
  `app.lookChanges` ("Knob=Option;..."), applied on top of the style by `customised()`; unknown values are
  ignored. Picking a style starts clean with its own highlight. "Dot matrix" sets titles only.
- Dark and light: every style has both (`DuetStyle.light()`: same fonts/shapes/layout on #f7f6f4; a white
  highlight shows near-black). `app.lightMode`; the chosen style's word says "Dark"/"Light", holding it
  switches. Now Playing and Lyrics stay dark in light mode with the style's fonts (`onArt()` = its dark self).
- Song colour: the owner saw the bottom always the same purple. Song-colour grounds are now a gradient from
  the song's colour to a deep (dark) or pale (light) shade of the same colour; the mini player and tab bar
  backing take that bottom colour (`groundBottom()`), and the blurred player's base gradient does too.
- Not done: Look Studio's mini player sizes (slim/large), "lyrics preview"/"up next" under the controls,
  service chips with logos, cover size/position, density. Titles use `DuetType.titled()` for capitals
  (Compose has no text-transform). Fonts add ~1.6 MB.

### D65. Three built-in styles, highlight reaches everything, guided style editor (2026-11-20, prototype)
- Owner: most of the seven styles looked the same; keep only their own three (Studio, Nothing, Airy) as the
  templates. Shelf, Night, Mono and Glass are gone (anyone on them falls back to Studio). Their fonts stay as
  choices in the editor.
- Highlight colour: every built-in style now uses it everywhere (`accentEverywhere`): glow at the top, open
  tab, chosen words, seek bar, play button (outline and icon ones too), progress line. Settings offers the
  Look Studio swatches (`HighlightChoices`) instead of the old four.
- Make your own: Settings > Appearance, "+ Add" on the right under the style list. The editor
  (ui/settings/StyleEditor.kt) is the Look Studio idea in the app: a live Home and Now Playing preview in
  the draft style, and it always runs as a guide (owner's choice): 18 steps (template, background, song
  colour, glow, highlight, font, titles, text size, corners, Home, bar colour, bar buttons, tab bar, tab names,
  player, play button, seek bar, name). The part each step changes is outlined in yellow with an arrow; the
  rest dims. Saved on the phone (`CustomLook`, `app.customLooks`: id|name|base|accent|changes), listed with the
  built-ins; Edit and Delete appear when one of yours is chosen. The inline per-style settings from D64 are
  replaced by the editor.
- Online theme shop: owner wants one; not built. Options given to the owner (no server code to run: Google
  Apps Script + Sheet like the release script, or Firebase/Supabase).

### D66. Shared styles (2026-11-20, live in v0.23.0)
- Owner: an online shop of styles people make; tied to the YouTube Music Google sign-in plus a username; no
  approval, no reporting ("it's really only a limited choice").
- Server: Google Apps Script web app over a Sheet, like the release script (docs/themes-script.gs; Sheet
  "Duet shared styles (do not delete)" `<id>` in Duet/Feeds). GET
  actions list/register/publish/delete/like; writes under a script lock; style codes and names checked
  against the app's format; 30 styles per username; usernames 3-20 of [A-Za-z0-9_.].
- Identity: sharing needs YouTube Music signed in in Duet (checked on the phone) and a username, claimed with
  a random 40-character key the phone makes (`shop.key`; the server keeps its SHA-256). Nothing from the
  Google sign-in (cookies, email, account name) is sent. Google doesn't confirm who someone is.
- App: `ThemeShop` (shop/), Settings > Appearance > "Shared styles" (Try, like, delete your own) and "Share"
  next to Edit/Delete on your own style. A shared style is `base|accent|Knob=Option;...` (CustomLook.shareCode);
  trying one saves it as your own style. `ThemeShop.URL` is blank until the owner deploys the script; the
  page then says "Shared styles aren't set up yet."

### D67. Seven editor templates, more settings, detailed sharing, one colour source (2026-11-20)
- The editor's "Start from" offers seven templates (`StyleTemplates`): Studio, Nothing, Airy, plus Shelf, Night,
  Mono and Glass, reworked to differ more (bar size, spacing, cover size) and to take the highlight everywhere.
  Settings still lists the owner's three.
- New settings: bar size (slim/card/large), spacing (compact/comfortable/airy), service switch (show/hide),
  cover size (small/medium/edge to edge). Not done: lyrics preview or "up next" under the controls, service
  logos (both need more data on Home/Player).
- Colour bug ("still red or another colour there"): the YouTube Music red and Spotify green marks next to every
  song ignored the style. Source marks now take the highlight colour (still with the service's name).
  Light mode now keeps a pale shade of a custom background instead of always the same off-white.
- Sharing: a description (140 characters) and up to 4 tags (dark, light, minimal, colourful, bold, soft, retro,
  clean); Shared styles filters by tag and sorts by newest or most liked; each style opens a page with a live
  preview, its description and tags, and every setting it uses. The script gained two columns; the owner has
  to publish a new version of the existing deployment (same URL).

### D68. Desktop app (2026-11-20)
- Owner: Mac (Apple Silicon and Intel) and Windows; quickest option wherever there was a choice; one app, sidebar and player bar layout; a Windows PC and an Apple Silicon MacBook to build on; no signing.
- `desktopApp/` module on the shared app: `App(graph, desktop = true)` draws a sidebar and a player bar and leaves out the phone's tabs and mini player. Audio is JavaFX (AAC and MP3), so desktop asks NewPipe for mp4 streams only. Songs are fetched in 1 MB Range pieces to a temp file because YouTube throttles a plain whole-file request to playback speed. Full notes in docs/DESKTOP.md.
- `-PdesktopOnly` leaves out the Android modules, so the Windows PC builds without the Android SDK.
- Not done: sign-in on desktop, Spotify wiring, wide-screen screens, desktop styles (docs/DESKTOP.md "Still to do").

### D69. Bass boost that always works, hidden covered pages (2026-11-20, 0.25.2 not released)
- Bass boost was only Android's `BassBoost` effect, which phones may refuse or weaken. Duet now also runs its own low shelf (`LowShelf` in shared, up to +15 dB under 120 Hz, soft-clipped) in the audio chain (`BalanceProcessor`); the system effect still runs on top. Tests in `LowShelfTest`.
- Pages covered by another page, and the mini player and tabs under a page, are hidden from screen readers.

### D70. GitHub (2026-11-20)
- Owner: a GitHub repo, private for now, made pretty so people can download Duet from it later, with the APKs too; tests go to Google Drive (the Drive tool only takes files up to 10 MB, so desktop builds go to GitHub Releases or stay on the Mac).
- Never publish the Owner APK or `local.properties`: the Owner build carries the release key for the release script. Before the repo goes public: remove or rotate the Drive and Sheet ids in the docs and the project brief that should not be public, and re-check the history for secrets (none found in tracked files on 2026-11-20).

### D71. Desktop sign-in and style editor (2026-11-20)
- YouTube Music sign-in on desktop: Google's page in a JavaFX WebView with a Safari user agent, cookies kept in `~/.duet/cookies.txt` (`CookieJar`), "Paste cookie instead" as the fallback. Tested: cookie parsing and persistence (`CookieJarTest`), and that the page loads in the packaged app. Not tested: a real sign-in (needs the owner's account).
- Spotify on desktop is not possible the Android way (the token request comes from a worker and is intercepted at the network layer, which JavaFX's browser cannot do). The plan is an official-API provider with PKCE; it needs the owner's Spotify Client ID.
- The style editor on desktop: `LocalDesktop` (set by `App(desktop = true)`) switches it to two columns and the preview to the real desktop layout. New desktop-only style settings: sidebar width, sidebar names, player bar height (`LookKnob.SidebarWidth`, `SidebarNames`, `BarHeight`); phones ignore them.

### D72. Spotify on desktop, updates, icon (2026-11-20)
- Tried and dropped: running Spotify's Web Player in an embedded Chromium (KCEF/JCEF) like the phone's WebView. On macOS the downloaded bundle has no Chromium framework, so the native library crashed at start (SIGSEGV in `CefApp.N_Initialize`); a JavaFX WebView cannot intercept the worker's `/api/token` request the phone relies on.
- Chosen: Spotify's official Web API with PKCE (D68's plan). The owner makes a free Spotify app and pastes its Client ID. `SpotifyApiProvider` is in shared and uses the phone's source id so likes and playlists carry over. Songs play through YouTube Music (the existing "Play Spotify songs from YouTube Music" path), on by default on desktop's first run.
- Desktop auto-update: feeds per system in a public releases repo (the private source repo cannot serve downloads to the apps); swap scripts quit, replace and reopen the app. Android keeps its Drive feeds.
- A Duet icon for the Mac app, the Windows exe and the window.

### D73. Spotify on desktop, final: the phone's way in a JavaFX WebView (2026-11-20, replaces D72's Spotify part)
- D72's official Web API route needs the app's owner to have Spotify Premium (Spotify's rule for new apps), which the owner does not have, so it was removed (see git history: `SpotifyApiProvider`, `SpotifyLogin`).
- Spotify's Web Player runs in JavaFX's WebView and an early script copies the token and headers from the page's own GraphQL calls (the token request itself comes from a worker that cannot be hooked, but the calls that carry the token can). The existing `SpotifyProvider` is used unchanged through a `SpotifyWebSession` (`SpotifyWebPage`). Fragile like the phone's: Spotify changing its page or hashes breaks both, and both are fixed in the shared provider.

### D74. Sign-in through the person's own browser, and organising the project per platform (2026-11-20)
- The owner hit "This browser or app may not be secure" (Google) and "Oops! Something went wrong" (Spotify) in the embedded sign-in windows. Both sites refuse an embedded browser. Sign-in now runs in the person's real Chrome, Edge or Brave and Duet reads the cookies over its local debugging port (`BrowserSignIn.kt`); the embedded window stays as a fallback. No password is ever typed into Duet.
- The owner said the project had become confusing. Docs are now per platform: `docs/README.md` (index), `docs/platforms/{android,macos,windows,shared}.md`; the long version log moved to `docs/HISTORY.md`; `the project brief` has a "Where am I?" table.
- Speed: a frame benchmark and `DUET_FRAMES=1` were added. A cached ground effect made no measurable difference and was reverted; the owner's choppiness is still open (docs/platforms/macos.md).

### D75. macOS: scrolling lag fix, Now Playing, player page, retry (2026-11-20)
- Cause of the cut frames and delay while scrolling (found in a `sample` recording of the owner's real use): Compose's per-mouse-event Caps/Num/Scroll Lock queries block the UI thread on the macOS main thread. Fixed by rewriting those two Compose functions at build time (ASM, in `desktopApp/build.gradle.kts`); `LWCToolkit` is final so it could not be subclassed, and Compose 1.9.3 has the same code. The JavaFX-vs-AWT guess was wrong.
- Duet now publishes to macOS Now Playing through a small Swift helper process, so notch apps ("Dynamic Island" on a Mac) show it and can control it.
- The desktop player bar hides while the full player is open; the desktop player is a centred column.
- Failed song starts retry once with a fresh stream look-up (shared code, so the phone gets it too).

### D76. macOS: a Mac layout, storage, radio, and what the Mac does not show (2026-11-20)
- The phone layout stretched across a window looked wrong. On Mac: the UI scales with the window width (60% to 95%, in `App.kt`), screens fill the window, Now Playing is cover left and controls right and takes the style's blur, cover size, gap and margin, the window opens maximized with a see-through title bar, and there is a menu bar (Playback, View) plus Space and Escape. Space is handled on the way back up, so a focused text field takes the space first and typing is safe.
- Mac had no downloads or cache: added `DesktopDownloads` (`~/.duet/downloads`) and `DesktopMediaCache` (`~/.duet/cache`, 1 GB, least recently played go first) in `DesktopStorage.kt`.
- Radio failed on Mac because the shared coordinator gave live streams a cache key, so the player downloaded them as songs (also wrong on Android). Live radio now gets no cache key and plays through a local relay (OkHttp reads the station, JavaFX reads `127.0.0.1`). JavaFX cannot play raw AAC or Ogg streams, so Mac lists MP3 stations only (`radioCodecs`).
- Removed on Mac only (not buildable there): reverb, crossfade, spatial audio, mobile-data quality, skip silence, pause on unplug, keep playing in background, auto-play radio in car, download on Wi-Fi only. Loudness now works on Mac; bass boost also lifts the bands just above the lowest.
