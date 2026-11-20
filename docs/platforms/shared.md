# Shared (used by the phone, the Mac app and the Windows app)

Folder: `shared/` (Kotlin Multiplatform). A change here changes every platform.

## Where things are (`shared/src/commonMain/kotlin/app/duet/music/`)
- `ui/`: every screen. `App.kt` is the whole app; `App(graph, desktop = true)` draws the computer layout (sidebar and player bar from `ui/desktop/DesktopChrome.kt`) instead of the phone's bottom tabs. `ui/settings/` has Settings, the style editor and its preview. `ui/player/` is Now Playing. `ui/home/`, `ui/library/`, `ui/search/`, `ui/radio/` are the tabs.
- `ui/theme/`: styles (`Theme.kt`: `DuetStyle`, the `LookKnob` settings, `duetGround`), `LookCss.kt` (a style as CSS text). Computer-only style settings are `SidebarWidth`, `SidebarNames`, `BarHeight`.
- `sources/spotify/`: `SpotifyProvider` (Spotify through the Web Player page's own GraphQL; needs a `SpotifyWebSession` from the platform). `sources/ytmusic/`: YouTube Music (InnerTube and NewPipe). `sources/radio/`: internet radio.
- `player/`: the queue and playback (`PlaybackCoordinator`); each platform supplies a `SourcePlayer`. `settings/`: `AppSettings`, `SoundSettings`. `library/`: likes, playlists. `update/`: the update check (`AppUpdates`). `audio/LowShelf.kt`: Duet's own bass boost filter.
- `DuetGraph.kt`: wires it together; each platform builds one in its own start-up code.

## Tests
- `shared/src/commonTest/` (runs on the computer target): logic.
- `shared/src/desktopTest/`: `ScreenshotTest` renders screens to `shared/build/screenshots/` (look at the PNGs).
- Run: `./gradlew :shared:desktopTest`.

## Rules
- Do not put phone-only or computer-only code in `commonMain`; platform things go in `androidApp/` or `desktopApp/`.
- The phone's Spotify session is `androidApp/.../spotify/AndroidSpotifyWebSession.kt`; the computer's is `desktopApp/.../SpotifyWebPage.kt`. Same job, different browser engine.
