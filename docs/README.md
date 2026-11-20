# Duet docs: where everything is

Duet is one app on three platforms. Find your platform first, then the details.

| I am working on | Read | Code |
|---|---|---|
| The phone app | [platforms/android.md](platforms/android.md) | `androidApp/` |
| The Mac app | [platforms/macos.md](platforms/macos.md) | `desktopApp/` |
| The Windows app | [platforms/windows.md](platforms/windows.md) | `desktopApp/` (the same code as the Mac app) |
| Anything on screen, Spotify, YouTube Music, styles | [platforms/shared.md](platforms/shared.md) | `shared/` (used by all three) |

Other documents:

- [DESKTOP.md](DESKTOP.md): the computer apps (Mac and Windows) in detail: layout, audio, updates, what is left.
- [RELEASING.md](RELEASING.md): how to release (phone feeds on Drive; computer apps on GitHub).
- [DECISIONS.md](DECISIONS.md): why things are the way they are (D1 to D74).
- [SPEC.md](SPEC.md): the original product spec. [HISTORY.md](HISTORY.md): the old per-version log.
- [DEVELOPING.md](DEVELOPING.md): work from several computers, releases.
- [SETUP.md](SETUP.md): tools and how this Mac was set up. [THIRD_PARTY.md](THIRD_PARTY.md), [PRIOR_ART.md](PRIOR_ART.md): credits and licences.

Rule that avoids mixing platforms up: a change in `shared/` reaches all three. Build and test the phone (`./gradlew :androidApp:compileOwnerDebugKotlin`) and the computer (`./gradlew :shared:desktopTest :desktopApp:test`) before calling it done.
