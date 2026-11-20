#!/usr/bin/env bash
# "Update windows" / "update mac": bump the version, build, publish, and move the update feed so installed apps update themselves.
#   ./scripts/update-desktop.sh windows "What's new"     (the Windows build runs at GitHub; needs `gh` signed in with the workflow scope)
#   ./scripts/update-desktop.sh mac "What's new"         (builds the Apple Silicon DMG on this Mac)
# Installed copies check the feed when they start and install the new version by themselves. See docs/DESKTOP.md.
set -euo pipefail
cd "$(dirname "$0")/.."
WHAT="${1:?usage: update-desktop.sh windows|mac \"notes\"}"
NOTES="${2:-Update}"

# One new version number per update (1.0.0 -> 1.0.1); the app's own number (Updates.kt) moves with it.
IFS=. read -r MA MI PA <<<"$(sed -n 's/^val desktopVersion = "\(.*\)"/\1/p' desktopApp/build.gradle.kts)"
VERSION="$MA.$MI.$((PA + 1))"
CODE=$((MA * 10000 + MI * 100 + PA + 1))
sed -i.bak "s/^val desktopVersion = \".*\"/val desktopVersion = \"$VERSION\"/" desktopApp/build.gradle.kts && rm desktopApp/build.gradle.kts.bak
sed -i.bak "s/const val VERSION_CODE = [0-9]*/const val VERSION_CODE = $CODE/" desktopApp/src/main/kotlin/app/duet/desktop/Updates.kt && rm desktopApp/src/main/kotlin/app/duet/desktop/Updates.kt.bak
git add -A && git commit -q -m "Desktop $VERSION: $NOTES" && git push -q origin main
mkdir -p dist

case "$WHAT" in
  windows)
    gh workflow run windows.yml
    sleep 10
    RUN=$(gh run list --workflow=windows.yml --limit 1 --json databaseId -q '.[0].databaseId')
    gh run watch "$RUN" --exit-status >/dev/null
    TMP=$(mktemp -d)
    gh run download "$RUN" -D "$TMP"
    cp "$TMP"/Duet-windows/Duet-"$VERSION"-windows.zip "$TMP"/Duet-windows/Duet-"$VERSION"-windows-setup.exe dist/
    ;;
  mac)
    ./scripts/build-mac.sh arm64
    ;;
  *) echo "windows or mac"; exit 1 ;;
esac
./scripts/release-desktop.sh "$NOTES"
echo "Published Duet $VERSION ($WHAT). Installed copies update the next time they start."
