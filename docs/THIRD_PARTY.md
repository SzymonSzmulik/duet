# Third-party code and assets ledger

## Duet's license: GPL-3.0
Owner's decision, 2026-11-16 (morning): use NewPipe Extractor for YouTube stream playback (see `docs/DECISIONS.md` D18). NewPipe Extractor is GPL-3.0, so **Duet is licensed GPL-3.0** (`LICENSE`).

This also changes the Myspoti answer from option (b) to **option (a)**: Myspoti (GPL-3.0) code may now be copied, with attribution, exactly like SpatialFlow. Myspoti's ad blocking is still never ported.
Earlier (night of 2026-11-16) option (b) applied; nothing was copied under it.

## Libraries with notable licenses
| Library | License | Used for |
|---|---|---|
| NewPipe Extractor (`com.github.TeamNewPipe:NewPipeExtractor` v0.27.1) | GPL-3.0 | Resolving YouTube audio stream URLs (`shared/src/jvmShared/.../NewPipeStreamResolver.kt`) |
| Multiplatform Settings (`com.russhwolf:multiplatform-settings`) | Apache-2.0 | Persisted settings (`AppSettings`) |

## Copied code and assets
| What | From | License | Where in Duet | Changes |
|---|---|---|---|---|
| Instrument Sans (variable TTF, weights 400-700) | google/fonts on GitHub, by the Instrument Sans Project Authors | SIL OFL 1.1 | `shared/src/commonMain/composeResources/font/instrumentsans.ttf` | none |
| Space Mono (regular, bold TTF) | google/fonts on GitHub, by the Space Mono Project Authors | SIL OFL 1.1 | `shared/src/commonMain/composeResources/font/spacemono_*.ttf` | none |
| Doto (variable TTF) | google/fonts on GitHub, by the Doto Project Authors | SIL OFL 1.1 | `shared/src/commonMain/composeResources/font/doto.ttf` | none |
| Figtree (variable TTF) | google/fonts on GitHub, by the Figtree Project Authors | SIL OFL 1.1 | `shared/src/commonMain/composeResources/font/figtree.ttf` | none |
| Manrope (variable TTF) | google/fonts on GitHub, by the Manrope Project Authors | SIL OFL 1.1 | `shared/src/commonMain/composeResources/font/manrope.ttf` | none |
| Archivo (variable TTF) | google/fonts on GitHub, by the Archivo Project Authors | SIL OFL 1.1 | `shared/src/commonMain/composeResources/font/archivo.ttf` | none |
| Space Grotesk (variable TTF) | google/fonts on GitHub, by the Space Grotesk Project Authors | SIL OFL 1.1 | `shared/src/commonMain/composeResources/font/spacegrotesk.ttf` | none |

| Spotify sign-in approach: WebView login on `accounts.spotify.com/login?continue=…open.spotify.com` with a desktop Chrome user agent, session from the `sp_dc` cookie, token taken from the Web Player page's own `/api/token` | Myspoti `host-android/.../WebPlayerHost.kt`, `LoginLoad.kt`, `api-spotify/.../player-bridge.js` (https://github.com/kota-zo/myspoti) | GPL-3.0 | `androidApp/src/main/kotlin/app/duet/music/spotify/` | re-written smaller: Duet hooks `fetch`/XHR for the token instead of Myspoti's page bridge; the login URL constant is copied verbatim |

| YouTube Music sign-in: Google login URL in a WebView, signed-in check on `SAPISID`/`HSID`, `SAPISIDHASH` header (`<ts>_sha1("<ts> <SAPISID> <origin>")`), account name from `account/account_menu` → `activeAccountHeaderRenderer.accountName` | SpatialFlow `ui/explore/GoogleSignInScreen.kt`, `data/innertube/InnerTubeClient.kt`, `InnerTubeParser.kt` (https://github.com/MythicalSHUB/SpatialFlow) | Apache-2.0 | `androidApp/.../ytmusic/AndroidYtMusicLogin.kt`, `shared/.../ytmusic/InnerTubeYtMusicSource.kt` (`accountName`, `sapisidHash`), `YtMusicParser.parseAccountName` | re-written in Kotlin Multiplatform; login URL copied verbatim; desktop user agent instead of SpatialFlow's mobile one |

| Lyrics: LRCLIB `/api/get` then `/api/search`, artist/title/±3 s match, synced preferred; LRC parser (several times per line, ♪ for empty lines) | Myspoti `app/.../data/api/LyricsApi.kt` (GPL-3.0); same LRCLIB approach in SpatialFlow `data/lyrics/engine/LyricsFetchManager.kt` (Apache-2.0) | GPL-3.0 / Apache-2.0 | `shared/.../lyrics/Lyrics.kt` | rewritten for Kotlin Multiplatform with ktor; fewer providers |
| Settings choices (skip silence, keep playing after closing, sleep timer, player toggles, Wi-Fi-only downloads) | Myspoti `SettingsScreen.kt` strings; SpatialFlow `SettingsFragment.kt` | GPL-3.0 / Apache-2.0 | `AppSettings.Flag`, `SettingsScreens.kt` | ideas and wording only |

| `AwakeWebView` (always visible to Chromium, detach held back) and attaching the hidden Web Player to the activity window | Myspoti `host-android/.../WebPlayerHost.kt` (`AwakeWebView`, `attach`) | GPL-3.0 | `androidApp/.../spotify/AwakeWebView.kt`, `AndroidSpotifyWebSession.attachHidden` | simplified: always awake; full-size INVISIBLE instead of 1 px |

| Crossfade with two ExoPlayers (start the next song at volume 0, cross volumes over the crossfade length, move the session to the new player) | SpatialFlow `service/AudioPlaybackService.kt` (`startCrossfadeOut`, `completeCrossfadeHandoff`) | Apache-2.0 | `androidApp/.../ExoStreamPlayer.kt` (`crossfadeTo`), `PlaybackCoordinator` (early advance) | rewritten; outgoing player drops audio-focus handling |

Other than the rows above, no source code has been copied from Myspoti or SpatialFlow so far. Re-implemented from understanding (no code text copied):
- InnerTube WEB_REMIX search request, as seen in SpatialFlow `data/innertube/InnerTubeClient.kt` (Apache-2.0) and in Duet's own `reference/web-prototype/server.js`.
- Using NewPipe `StreamInfo.getInfo` for audio streams, as SpatialFlow's `NewPipeStreamExtractor.kt` does.
