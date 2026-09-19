#!/usr/bin/env bash
# Builds every independent mini-game project under app/minigames/<name>/ and publishes its
# dist/ output to app/webApp/build/web/minigames/<name>/ — the same static dir Ktor already
# serves for the main client (see CLAUDE.md). Each mini-game is its own npm project, entirely
# decoupled from the app/webApp build graph (see app/minigames/README.md).
set -euo pipefail
cd "$(dirname "$0")/.."

BUILD_WEB="$(pwd)/app/webApp/build/web/minigames"
mkdir -p "$BUILD_WEB"

shopt -s nullglob
for dir in app/minigames/*/; do
    name="$(basename "$dir")"
    [ -f "$dir/package.json" ] || continue
    echo "[build-minigames] $name"
    (cd "$dir" && [ -d node_modules ] || npm install)
    (cd "$dir" && npm run build)
    mkdir -p "$BUILD_WEB/$name"
    cp -r "$dir/dist/." "$BUILD_WEB/$name/"
done
echo "[build-minigames] Done"
