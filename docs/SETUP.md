# Setup: what to install

Install everything below that is missing, using the commands here. Check first, install only what's absent. The reference machine is a Mac (macOS, zsh); `brew install` is the usual way.

## 0. Homebrew (the owner installs this once, before sleeping, if missing)
Check: `brew --version`. If missing, you cannot install it (it needs the owner's password). Stop installing, write the exact line below in SETUP_LOG.md, and do all work that needs no installs (reading both reference repos, writing PRIOR_ART_NOTES.md).
`/bin/bash -c "$(curl -fsSL https://raw.githubusercontent.com/Homebrew/install/HEAD/install.sh)"`

## 1. JDK 17
Check: `java -version` (need 17 or newer). Install: `brew install --cask temurin@17`

## 2. Android SDK (command-line tools, no Android Studio needed for builds)
Check: `echo $ANDROID_HOME` and `ls $(brew --prefix)/share/android-commandlinetools`
Install: `brew install --cask android-commandlinetools`
Then set `ANDROID_HOME` for this session: `export ANDROID_HOME=$(brew --prefix)/share/android-commandlinetools`
Accept licenses and install packages:
`yes | sdkmanager --licenses`
`sdkmanager "platform-tools" "platforms;android-35" "build-tools;35.0.0"`
Create `local.properties` in the project root with `sdk.dir=<the ANDROID_HOME path>` (do not commit it).
Add the export line to `~/.zshrc` only if the owner's file does not already set ANDROID_HOME (append, never overwrite).

## 3. adb (talking to the phone)
`brew install --cask android-platform-tools` (skip if `adb --version` works)
Phone: USB debugging must be turned on by the owner. If `adb devices` shows nothing, note it in SETUP_LOG.md and still build the APK with `./gradlew assembleDebug`; the owner installs it later.

## 4. Gradle
Do not install Gradle. Generate the project with the Gradle wrapper (`gradle wrapper`, or write the wrapper files yourself) and use `./gradlew`. If no `gradle` binary exists, `brew install gradle` once to generate the wrapper.

## 5. Optional later
Android Studio (`brew install --cask android-studio`) only if the owner wants the IDE or an emulator. Not needed for the first night.

## Verification (record results in SETUP_LOG.md)
`java -version` | `sdkmanager --list_installed` | `adb --version` | `./gradlew --version`
