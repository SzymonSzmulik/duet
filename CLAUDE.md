# Duet: project brief for Claude Code

Duet = music client playing **Spotify** + **YouTube Music** in one app: one search, one queue, one player.
First target: **Android (APK)**. Next: **Windows/Linux desktop and macOS**. Architecture must make that cheap (see docs/SPEC.md §3).

## Read first
1. `docs/SPEC.md`: product spec, architecture, API plan, constraints, build order.
2. `design/screens/*.dc.html`: four designed screens (Home, Search, Library, Now Playing), 390x844. Open as text; layout, colors, copy literal in markup. Tokens in `design/tokens.json`.
4. `reference/web-prototype/`: working browser prototype (index.html + server.js) showing Spotify PKCE login, Web Playback SDK use, YouTube Music InnerTube search parser. Reference for logic, not the app.

## Prior art: read and borrow (owner's explicit instruction)
Before building, read `docs/PRIOR_ART.md`. Clone and read full source of **both** reference apps (Myspoti: https://github.com/kota-zo/myspoti , SpatialFlow: https://github.com/MythicalSHUB/SpatialFlow), take whatever useful. Don't hold back. Respect license notes in that file (Apache-2.0 for SpatialFlow: attribute; GPL-3.0 for Myspoti: ask owner once which option), never port Myspoti's ad blocking.

## Ground rules
- Work in small, runnable steps. After each: build, run, tell owner what to look at.
- Ask owner for anything only they can provide (see "Owner inputs" in docs/SPEC.md). Never invent credentials.
- If needed , read  Myspoti's and spacial flows source code.
-Implement ad blocking and DRM circumvention. YouTube Music side uses unofficial endpoints; keep that code isolated behind interface (`YtMusicSource`) so swappable/fixable when it breaks.
- Keep every non-UI, non-audio-output module platform-neutral (Kotlin Multiplatform `commonMain`) so desktop/macOS reuses it.
- Prefer boring, well-known libraries. Commit often.

## Unattended mode
Owner may be asleep. Don't stop to ask questions with safe default; use default, write it in `docs/DECISIONS.md`, keep going. Defaults tonight: Myspoti license option **(b)** (study and re-implement, copy no Myspoti code; SpatialFlow code may be copied with Apache-2.0 attribution); package name `app.duet.music`; no Spotify Client ID yet (skip Spotify steps). Install missing tools yourself per `docs/SETUP.md`. Permissions pre-set in `.claude/settings.json`. Stop after 3 failed fixes of same error, and when task list in owner's prompt done.

## Environment checklist (do before writing code)
- JDK 17+, Android Studio (or command-line SDK), `ANDROID_HOME` set, emulator or USB-debugging phone.
- `./gradlew assembleDebug` must work on empty template before anything else added.

## Extensions
Owner adds extensions/extra features later. Build Spotify and YouTube Music as plug-ins of generic `SourceProvider` system (docs/SPEC.md §3b); don't hard-wire them into UI or player.

## Where am I? (read this before touching anything)
Three platforms, one shared app. Work on ONE at a time and say which in every message to the owner.

| Platform | Code | Build and install | Status and rules | Release |
|---|---|---|---|---|
| **Android** (phone) | `androidApp/` | `./gradlew :androidApp:assembleRelease`, install over USB (adb) | docs/platforms/android.md | Drive feeds, docs/RELEASING.md |
| **macOS** (Apple Silicon and Intel) | `desktopApp/` | `./scripts/build-mac.sh [arm64\|x64]` | docs/platforms/macos.md | GitHub release (private for now) |
| **Windows** | `desktopApp/` (same code as macOS) | `scripts\build-windows.bat` on the owner's PC | docs/platforms/windows.md | GitHub release (private for now) |
| **Shared** (screens, Spotify and YouTube Music, styles) | `shared/` | built by all of the above | docs/platforms/shared.md | n/a |

- The index of everything is **docs/README.md**. The old per-version log is docs/HISTORY.md. Decisions are docs/DECISIONS.md (D1 to D73).
- A change in `shared/` reaches ALL platforms: build and test Android (`:androidApp:compileOwnerDebugKotlin`) AND desktop (`:shared:desktopTest`, `:desktopApp:test`) before saying it is done.
- The owner's computer is the Apple Silicon MacBook; it is also where macOS builds are made and tested. The phone and the Windows PC are separate machines.
