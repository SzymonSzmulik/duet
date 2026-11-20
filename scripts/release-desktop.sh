#!/usr/bin/env bash
# Publishes the desktop files in dist/ and points the update feeds at them:   ./scripts/release-desktop.sh "What's new"
# Needs `gh` signed in. Files go to a GitHub release of the releases repo (public, so the apps can download updates without
# signing in), and feeds/<platform>.json in that repo says what the newest version is. See docs/DESKTOP.md.
set -euo pipefail
cd "$(dirname "$0")/.."

REPO="${DUET_RELEASES_REPO:-SzymonSzmulik/duet-releases}"
VERSION="$(sed -n 's/^val desktopVersion = "\(.*\)"/\1/p' desktopApp/build.gradle.kts)"
IFS=. read -r MAJOR MINOR PATCH <<<"$VERSION"
CODE=$((MAJOR * 10000 + MINOR * 100 + PATCH))   # the same number as Updates.VERSION_CODE
NOTES="${1:-Duet $VERSION}"
TAG="desktop-v$VERSION"

FILES=()
for f in "dist/Duet-$VERSION-mac-arm64.dmg" "dist/Duet-$VERSION-mac-x64.dmg" "dist/Duet-$VERSION-windows.zip"; do
  [ -f "$f" ] && FILES+=("$f")
done
[ ${#FILES[@]} -gt 0 ] || { echo "Nothing to publish: build into dist/ first (scripts/build-mac.sh, scripts/build-windows.bat)."; exit 1; }

gh release create "$TAG" "${FILES[@]}" --repo "$REPO" --title "Duet $VERSION" --notes "$NOTES" \
  || gh release upload "$TAG" "${FILES[@]}" --repo "$REPO" --clobber

# The same files on the main repo's Releases page, where people look first. Best effort: the feeds above are what updates use.
MIRROR="${DUET_MIRROR_REPO:-SzymonSzmulik/duet}"
# Each release page carries the newest build of every system (a Windows build is not rebuilt when only the Mac is), the Windows installer and the newest phone APK.
latest() { ls dist/Duet-*"$1" 2>/dev/null | sort -V | tail -1; }
APK="androidApp/build/outputs/apk/normal/release/androidApp-normal-release.apk"
APKV="$(sed -n 's/^ *versionName = "\(.*\)"/\1/p' androidApp/build.gradle.kts | head -1)"
[ -f "$APK" ] && cp "$APK" "dist/Duet-$APKV.apk"
MFILES=($(printf '%s\n' "${FILES[@]}" "$(latest -windows.zip)" "$(latest -windows-setup.exe)" "$(latest -mac-arm64.dmg)" "$(latest -mac-x64.dmg)" "dist/Duet-$APKV.apk" | awk 'NF && !seen[$0]++ && system("test -f " $0)==0'))
gh release create "$TAG" "${MFILES[@]}" --repo "$MIRROR" --title "Duet $VERSION" --notes "$NOTES" \
  || gh release upload "$TAG" "${MFILES[@]}" --repo "$MIRROR" --clobber || echo "Could not mirror to $MIRROR"

feed() {  # platform, file
  local json sha
  json=$(python3 -c 'import json,sys; print(json.dumps({"versionCode": int(sys.argv[1]), "versionName": sys.argv[2], "apkUrl": sys.argv[3], "notes": sys.argv[4]}))' \
    "$CODE" "$VERSION" "https://github.com/$REPO/releases/download/$TAG/$(basename "$2")" "$NOTES")
  sha=$(gh api "repos/$REPO/contents/feeds/$1.json" --jq .sha 2>/dev/null || true)
  gh api -X PUT "repos/$REPO/contents/feeds/$1.json" -f message="Duet $VERSION for $1" \
    -f content="$(printf '%s' "$json" | base64 | tr -d '\n')" ${sha:+-f sha="$sha"} >/dev/null
  echo "Feed $1 -> $VERSION"
}
for f in "${FILES[@]}"; do
  case "$f" in
    *mac-arm64*) feed mac-arm64 "$f" ;;
    *mac-x64*) feed mac-x64 "$f" ;;
    *windows*) feed windows "$f" ;;
  esac
done
