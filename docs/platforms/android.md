# Android (the phone app)

Folder: `androidApp/` (plus `shared/`). Package `app.duet.music`. Three apps from one code (flavors): Duet, Duet Tester (`.tester`), Duet Owner (`.owner`).

## Where it is now (2026-11-20)
- Released: **0.25.1** (owner feed, installed on the owner's phone). The editor on one screen with the real-screen preview, 25 style settings, CSS copy/paste/AI prompt.
- **Not released yet (next "update owner" = 0.25.2):** Duet's own bass boost (`shared/.../audio/LowShelf.kt`, used by `androidApp/.../audio/BalanceProcessor.kt`, D69), covered pages hidden from screen readers, and the shared changes made since for the computer apps (they do nothing on the phone).
- Spotify on the phone is the Web Player in a WebView (D24 to D52). Not tested signed in with a real account.

## Build and install
- Env: `JAVA_HOME=/opt/homebrew/opt/openjdk@17/libexec/openjdk.jdk/Contents/Home ANDROID_HOME=/opt/homebrew/share/android-commandlinetools` (Homebrew's JDK is fine for Android; it is NOT fine for packaging the computer apps).
- Check it compiles: `./gradlew :androidApp:compileOwnerDebugKotlin`. Release APKs: `./gradlew :androidApp:assembleRelease` (all three flavors).
- Install on the phone (serial `<serial>`, over USB): `adb -s <serial> install -r androidApp/build/outputs/apk/owner/release/androidApp-owner-release.apk`. Works with the phone locked. The phone PIN is in memory; use it only when the owner says so.
- Emulator: AVD `duet` (`/opt/homebrew/share/android-commandlinetools/emulator/emulator -avd duet`), serial `emulator-5554`. Launch the owner app with `adb shell am start -n app.duet.music.owner/app.duet.music.MainActivity` (the plain Duet app is also installed there; do not mix them up).

## Release ("update owner")
Full steps in [../RELEASING.md](../RELEASING.md): bump version, build, install Owner on the phone, upload the three APKs to their Drive folders (the Owner APK folder is private: it carries the release key), then rewrite the owner feed. Never put the Owner APK or `local.properties` on GitHub.

## Known issues
- Spotify signed in, Android Auto and the widget are untested with a real account or a car.
- The phone locks within seconds; adb installs still work.
