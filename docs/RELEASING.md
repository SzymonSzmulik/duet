# Releasing Duet

Three apps, built from one codebase as Gradle product flavors (`androidApp/build.gradle.kts`):

| App | Flavor | Package | Update feed (public Google Doc) |
|---|---|---|---|
| Duet | `normal` | `app.duet.music` | Duet release feed `<id>` |
| Duet Tester | `beta` (Gradle forbids flavor names starting with "test") | `app.duet.music.tester` | Duet tester feed `<id>` |
| Duet Owner | `owner` | `app.duet.music.owner` | Duet owner feed `<id>` |

They install side by side. Each checks its own feed every time it opens (and in Settings > About),
reading `https://docs.google.com/document/d/<id>/export?format=txt`. A feed is one JSON line:

```json
{"versionCode":16,"versionName":"0.13.0","apkUrl":"https://drive.usercontent.google.com/download?id=<id>&export=download&confirm=t","notes":"..."}
```

The owner feed also has `testerApkUrl` and `releaseApkUrl` (the same version built as Duet Tester and
Duet) and `controlUrl` (the release script's web-app URL).

## "Update owner"
1. Bump `versionCode` and `versionName`, then `./gradlew :androidApp:assembleRelease` (builds all three).
2. Upload each APK into its own Drive folder (the files inherit the folder's sharing). All Duet files live
   in one Drive folder "Duet" `<id>`: the three APK folders below, "Feeds (do
   not delete)" (the three feed docs and the release script) and "Design" (mockups, screenshots). Never
   put Duet files loose in My Drive.
   - Duet Owner: "Owner APKs (private)" `<id>`. Never share it: the
     Owner APK carries the release key for the script below. The Owner app can't fetch a private link, so
     its updater opens the link in the browser, which is signed in as the owner; install from Downloads.
   - Duet Tester: "Tester APKs" `<id>` (shared "anyone with the link").
   - Duet: "Duet APKs" `<id>` (shared "anyone with the link").
3. Write the owner feed with all three links (keep `controlUrl`). Testers and everyone don't change.

## "Send to testers" / "Release to everyone"
Either the buttons in Duet Owner (Settings > Releases) or by hand: copy the owner feed's version into
the tester feed (with `apkUrl` = `testerApkUrl`), and for everyone also into the release feed (with
`apkUrl` = `releaseApkUrl`).

## The release script (Owner buttons)
`docs/release-script.gs` is a Google Apps Script web app in the owner's account. Its `OWNER_KEY` must
equal `duet.ownerKey` in `local.properties`, which only Duet Owner builds carry. One-time setup: open
script.new, paste the script with the key, Deploy > New deployment > Web app, Execute as: Me, Who has
access: Anyone, then put the `/exec` URL into the owner feed as `controlUrl`.

## Signing
Release builds are signed with the Duet release key `~/.android/duet-release.jks` (alias `duet`). Its
path and password are `duet.storeFile` / `duet.storePassword` in `local.properties` (not in git);
without them Gradle falls back to the debug key. Switched from the debug key in 0.13.1 because Play
Protect flags debug-signed apps; installs signed with the old debug key must be uninstalled once.
Android only installs an update signed with the same key, so back up the keystore and password:
losing them means everyone reinstalls again.

## Rule: the owner feed always matches the installed Owner
Every Owner build that gets installed (over USB too) is also built as Tester and normal, all three go into
their Drive folders (step 2 above), and the owner feed is moved to that version. Settings > Releases reads the
owner feed, and its buttons send what the feed names, so a feed left behind sends an old version.

## Desktop (Mac and Windows)
Different from the phone apps: one app "Duet", files too big for the Drive tool (10 MB), so they go to GitHub.
1. Bump `desktopVersion` in `desktopApp/build.gradle.kts` (and `Updates.VERSION_CODE`: 1.2.3 is 10203; a test checks they agree).
2. Build: `./scripts/build-mac.sh` and `./scripts/build-mac.sh x64` on the Mac, `scripts\build-windows.bat` on the Windows PC; everything lands in `dist/`.
3. `./scripts/release-desktop.sh "What's new"` makes a release of the public releases repo `SzymonSzmulik/duet-releases` (the apps download updates from a public place) and rewrites `feeds/mac-arm64.json`, `feeds/mac-x64.json` and `feeds/windows.json` there. Copy the Windows zip to the Mac first (or run the script on the PC with `gh` signed in) so one run publishes all three.
4. Running apps check the feed when they open and offer the update.
