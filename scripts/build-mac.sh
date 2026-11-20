#!/usr/bin/env bash
# Builds Duet's macOS DMG: ./scripts/build-mac.sh [arm64|x64]   (default: this Mac's chip)
# Output: dist/Duet-<version>-mac-<arch>.dmg. An Intel (x64) build on an Apple Silicon Mac runs under Rosetta.
set -euo pipefail
cd "$(dirname "$0")/.."

ARCH="${1:-$(uname -m | sed 's/x86_64/x64/')}"
case "$ARCH" in
  arm64) JDK_ARCH=aarch64 ;;
  x64) JDK_ARCH=x64 ;;
  *) echo "Use: $0 [arm64|x64]"; exit 1 ;;
esac

# Packaging needs a full JDK (Homebrew's is not enough), so fetch Temurin 17 for this chip once.
JDK_DIR="$HOME/.jdks/temurin17-$ARCH"
if [ ! -x "$JDK_DIR/Contents/Home/bin/java" ]; then
  echo "Downloading Temurin 17 ($ARCH)..."
  mkdir -p "$JDK_DIR"
  curl -fL "https://api.adoptium.net/v3/binary/latest/17/ga/mac/$JDK_ARCH/jdk/hotspot/normal/eclipse" | tar xz -C "$JDK_DIR" --strip-components=1
fi
export JAVA_HOME="$JDK_DIR/Contents/Home"

RUN=()
if [ "$ARCH" = x64 ] && [ "$(uname -m)" = arm64 ]; then RUN=(arch -x86_64); fi

# ${RUN[@]+...} because macOS's old bash treats an empty array as unset.
"${RUN[@]+"${RUN[@]}"}" ./gradlew -PdesktopOnly :desktopApp:clean :desktopApp:packageDmg

VERSION="$(sed -n 's/^val desktopVersion = "\(.*\)"/\1/p' desktopApp/build.gradle.kts)"
mkdir -p dist
cp desktopApp/build/compose/binaries/main/dmg/Duet-*.dmg "dist/Duet-$VERSION-mac-$ARCH.dmg"
echo "Done: dist/Duet-$VERSION-mac-$ARCH.dmg"
