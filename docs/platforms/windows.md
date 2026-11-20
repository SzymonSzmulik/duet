# Windows

Folder: `desktopApp/` (the same code as macOS) plus `shared/`. The owner has a Windows PC; builds for Windows can only be made there (or on a Windows machine).

## Where it is now (2026-11-20)
- **Never built or run on Windows yet.** The owner started a build on the PC and had it fail once because the script was run from inside the zip (fixed: the script now says what to do).
- Written but untested on Windows: the update swap script (`Updates.kt`, `WINDOWS_SCRIPT`), finding Chrome or Edge for sign-in (`BrowserSignIn.findBrowser`; Edge is on every Windows 10 and 11), the taskbar icon (`desktopApp/icons/Duet.ico`).
- The owner must download a fresh copy of the repo from GitHub (Code, Download ZIP) to get the latest code before building.

## Build (on the Windows PC)
1. Extract the whole zip (right-click, Extract All). Do not run files from inside the zip.
2. Open the extracted folder, then `scripts`, and double-click `build-windows.bat`. If Java is missing it installs Java 17 with winget and asks to run again.
3. Output: `dist\Duet-<version>-windows.zip`. Unzip and run `Duet.exe`; at the SmartScreen box choose More info, then Run anyway (unsigned).
4. `-PdesktopOnly` is used so the PC does not need the Android SDK.
5. An installer `.exe` is also made when WiX Toolset 3 is installed.

## What to check first when it runs
The app opens, search plays a song (JavaFX audio), Settings > Music services > Sign in opens Chrome or Edge, the update check does not crash. Send any error text from the black window.

## Release
Copy the zip to the Mac `dist/` folder, then `./scripts/release-desktop.sh` publishes all three computer files and the update feeds (needs the public `duet-releases` repo). See [../DESKTOP.md](../DESKTOP.md).
