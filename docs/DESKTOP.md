# Duet for computers (macOS and Windows)

This file is the detail for both computer apps. Per platform: [platforms/macos.md](platforms/macos.md), [platforms/windows.md](platforms/windows.md). The shared code: [platforms/shared.md](platforms/shared.md).

One desktop app, "Duet", for Mac (Apple Silicon and Intel) and Windows. It is the same shared app as Android in a desktop window with a computer layout. Decisions D68 to D70 in docs/DECISIONS.md.

## What exists (2026-11-20)

- `desktopApp/`: the shell. `Main.kt` builds the app's parts for a computer and opens a window; `DesktopStreamPlayer.kt` plays audio with JavaFX.
- **Layout** (`shared/.../ui/desktop/DesktopChrome.kt`, switched on by `App(graph, desktop = true)`): a left sidebar (Home, Search, Library, Radio, then Accounts and Settings), the same screens and pages in the middle, and a player bar along the bottom with song, controls, seek bar, queue, lyrics and the full player. The phone's bottom tabs and mini player are not drawn on desktop.
- **Audio:** JavaFX MediaPlayer. YouTube Music songs come from NewPipe as AAC (mp4), fetched in 1 MB Range pieces to a temp file (a plain whole-file request is throttled to playback speed by YouTube), then played. Radio plays straight from its URL. Equalizer, bass boost (up to +12 dB, JavaFX's limit), speed and balance work; reverb, loudness and crossfade do not (JavaFX has none).
- **Sources:** YouTube Music and internet radio work. **YouTube Music sign-in** (`YtMusicLogin.kt`): in the person's own Chrome, Edge or Brave (`BrowserSignIn.kt`, see below), because Google refuses an embedded browser ("This browser or app may not be secure", seen on the owner's Mac); the old JavaFX window with "Paste cookie instead" is only the fallback when there is no such browser. Cookies are kept in `~/.duet/cookies.txt`. **Spotify** is wired through Spotify's official Web API (below).
- **Style editor on desktop:** two columns (live preview left, settings right). The preview draws the real desktop layout (Home and the player, each with sidebar and player bar), and a "Desktop" section adds sidebar width, sidebar names (icons only) and player bar height. Car mode is hidden in Settings.
- **Builds:** `scripts/build-mac.sh [arm64|x64]` and `scripts/build-windows.bat`. `-PdesktopOnly` leaves the Android modules out, so the Windows PC needs no Android SDK.
- **Verified:** the packaged macOS app (Apple Silicon) searches, downloads, plays and reports the right length. Intel and Windows builds are built the same way and not yet run on their hardware.
- Smoke test: `DUET_SMOKE="song name" ./gradlew -PdesktopOnly :desktopApp:run` plays the first result and prints the player's state for 20 seconds.

- **Spotify** (`SpotifyWebPage.kt`): the phone's Spotify provider and its Web Player approach, with JavaFX's WebView as the browser (never shown). A script that runs before the page's own scripts (`documentProperty` listener) copies the bearer token and the `client-token` headers off the page's own calls to `api-partner.spotify.com`, and `pageFetch` sends Duet's GraphQL requests from inside the page. Sign-in is Spotify's own login page in the person's own browser (`BrowserSignIn.kt`; the embedded window shows "Oops! Something went wrong"), or a pasted `sp_dc` cookie in the fallback window; the session is the cookies, kept in `~/.duet/cookies.txt`. No Spotify developer app and no Premium needed. Verified on this Mac: the page loads signed out, the anonymous token is captured, and a Spotify search through the provider returns songs; the login page loads (email, Google, Facebook, Apple). Not verified: a real signed-in session (needs an account), and playback of Spotify songs (they play through YouTube Music's copy, on by default on the first run; Settings > Sound).
- **Real-browser sign-in** (`BrowserSignIn.kt`): finds Chrome, Edge or Brave, starts it on the login page with a throwaway profile (`--user-data-dir`) and `--remote-debugging-port` on this computer only, polls the Chrome DevTools Protocol (`Storage.getCookies`) until the site's session cookies appear, copies them into Duet's cookie file, and closes the browser. The person's own browser profile is never read. Tested: launch, connect, read cookies, clean up (`DUET_SMOKE_BROWSER=1`), and the parsing and storing (`BrowserSignInTest`).
- **Updates** (`Updates.kt`): the same feed check as Android, one feed per system (`mac-arm64`, `mac-x64`, `windows`) at `https://raw.githubusercontent.com/SzymonSzmulik/duet-releases/main/feeds/<platform>.json`. Installing downloads the new DMG (Mac) or zip (Windows) next to a small script that waits for Duet to quit, swaps the app in (the old one is kept if the copy fails) and opens it again. The Mac swap is tested against a real DMG; the Windows script has not been run. A run from the build folder is never offered updates. Publish with `scripts/release-desktop.sh "notes"` (needs the public releases repo; see docs/RELEASING.md).
- **Icon:** `desktopApp/icons/` (a D on the song-colour glow), used for the Mac app, the Windows exe and the window.

## Decisions

- One desktop app (no Owner/Tester split yet). Layout: sidebar plus player bar.
- JavaFX for audio: quickest, no extra install, AAC and MP3 only. VLC would play every format but adds about 100 MB per system and a native library to bundle.
- Spotify on a computer: library, search and playlists through Spotify's own account, songs played through YouTube Music's matching song (the "Play Spotify songs from YouTube Music" setting, on by default on desktop). Spotify's own player needs DRM that the desktop web view does not have.
- No paid signing. The Apple Silicon build is ad-hoc signed and the Intel build is not signed at all. macOS 15 and newer refuses both until the quarantine flag is removed (`xattr -dr com.apple.quarantine /Applications/Duet.app`, or System Settings > Privacy & Security > Open Anyway); right-click > Open no longer works. Windows shows SmartScreen once.
- Desktop styles and phone styles are separate: a shared style carries its platform, and each app lists only its own. A style cannot be used on the other platform because the layouts differ.

## Still to do

1. **Windows build run** on the PC (`scripts\build-windows.bat`) and fixing what it finds.
2. ~~Spotify on desktop~~ (done, above).
3. **Desktop-wide screens:** Home as a grid, Library and Search with more columns, a right-hand queue and lyrics panel instead of sheets.
4. **Desktop styles:** more PC-only settings (sidebar side, player bar position, density, queue panel on or off); CSS theme files in a themes folder; a desktop-only shared styles list (the shop's script needs a `platform` column).
5. **Parity:** downloads for offline play, settings that are phone-only hidden, sleep timer, tray icon and media keys, update check against the feed (open the download link).
6. **Release flow:** attach the DMGs and the zip to GitHub Releases; add the desktop files to the update feed.

## Building

- Mac: `./scripts/build-mac.sh` (Apple Silicon) or `./scripts/build-mac.sh x64` (Intel, run through Rosetta). The script fetches Temurin 17 for that chip the first time. Homebrew's JDK cannot package.
- Windows: `scripts\build-windows.bat`. It installs Java 17 with winget if missing and makes `dist\Duet-<version>-windows.zip`. With WiX Toolset 3 installed it also makes the `.exe` installer.
- The installer version is `desktopVersion` in `desktopApp/build.gradle.kts` (macOS requires 1.0.0 or higher).

## "Update windows" / "update mac" (the owner's phrase)
`./scripts/update-desktop.sh windows "notes"` or `mac "notes"`: raises the version by one (`desktopVersion` and `Updates.VERSION_CODE`), commits and pushes, builds (Windows: the `windows.yml` workflow at GitHub, then the zip is downloaded; Mac: `build-mac.sh arm64`), publishes with `release-desktop.sh` and moves that system's feed. Installed copies check the feed when Duet starts and, on a computer, install the new version by themselves (`UpdatePrompt` in `App.kt`; the swap script restarts Duet). Needs the public `SzymonSzmulik/duet-releases` repo (the apps read the feed from it without signing in) and `gh` signed in with the `workflow` scope.

## Phone remote
The desktop app runs a small web server (`PhoneRemote.kt`, port 8765) so a phone on the same Wi-Fi can play, pause, skip, seek, shuffle and repeat. Press Ctrl+Shift+R (Cmd+Shift+R on a Mac): a box shows the link and copies it. Open it once on the phone and bookmark it. The link carries a secret key kept in `~/.duet/remote-key`; every request without it gets 403. Delete that file to make a new key (old links stop working). It only listens on the local network. If a firewall asks (Windows), allow private networks.
