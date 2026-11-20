# Working on Duet from more than one computer

The whole project (Android, macOS, Windows, shared code) lives in this one repository. Work on whichever computer you like and push; pull on the other.

## Set up a computer
1. Install Git, JDK 17 and (for the phone app) Android Studio. On Windows the `winget` commands in `scripts\build-windows.bat` do the Java part.
2. `git clone https://github.com/SzymonSzmulik/duet.git` and open the folder.
3. Create `local.properties` (it is not in git, never commit it). It is only needed for things that are yours: the Android release key (`duet.storeFile`, `duet.storePassword`), update feed document ids (`duet.feed.normal`, `.beta`, `.owner`) and `duet.ownerKey`. The desktop apps need none of it.
4. Run the desktop app: `./gradlew :desktopApp:run` (`gradlew.bat` on Windows).
5. Before pushing a change in `shared/`, run `./gradlew :androidApp:compileOwnerDebugKotlin :shared:desktopTest :desktopApp:test`.

## Releasing the desktop apps
Needs the GitHub CLI signed in (`gh auth login`, then `gh auth refresh -s workflow`) and a Git Bash or WSL shell on Windows.

- `./scripts/update-desktop.sh windows "what's new"`: works from any computer. It raises the version, pushes, builds the Windows zip and installer on GitHub (workflow `windows.yml`), publishes it to `SzymonSzmulik/duet-releases` and moves the Windows feed.
- `./scripts/update-desktop.sh mac "what's new"`: must run on a Mac (it builds the DMG there).

Installed apps read their feed from `duet-releases` when they start and install the new version by themselves, so a release is live as soon as the script ends. Do not rename or make private the `duet-releases` repo: that is where installed copies look.

## Keep private things out
Never commit `local.properties`, keystores, tokens, phone PINs, document ids or personal paths. `.gitignore` covers the common ones; check `git status` before every commit.
