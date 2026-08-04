#!/usr/bin/env bash
# Sequential dev build: detects what changed, rebuilds in order, restarts server.
# Usage: dev-build.sh [--force]   (--force rebuilds everything regardless of changes)
set -euo pipefail
cd "$(dirname "$0")/.."

LAST_BUILD=.last-build
FORCE=false
[ "${1:-}" = "--force" ] && FORCE=true

BUILD_JS=false
BUILD_WASM=false
BUILD_SERVER=false

if $FORCE || [ ! -f "$LAST_BUILD" ]; then
    echo "[build] $([ $FORCE = true ] && echo 'Force' || echo 'First') build — rebuilding everything"
    BUILD_JS=true; BUILD_WASM=true; BUILD_SERVER=true
else
    JS_CHANGES=$(find app/webApp/ts-src -newer "$LAST_BUILD" \
        \( -name "*.ts" -o -name "*.tsx" -o -name "*.css" \) \
        -not -path "*/node_modules/*" -not -path "*storybook*" -not -path "*__tests__*" \
        -print -quit 2>/dev/null)
    WASM_CHANGES=$(find app/webApp/src/wasmJsMain core/src/commonMain -newer "$LAST_BUILD" \
        -name "*.kt" -print -quit 2>/dev/null)
    SERVER_CHANGES=$(find server/src/main core/src/commonMain -newer "$LAST_BUILD" \
        \( -name "*.kt" -o -name "*.yaml" -o -name "*.json" \) -print -quit 2>/dev/null)

    [ -n "$JS_CHANGES" ]     && BUILD_JS=true
    [ -n "$WASM_CHANGES" ]   && BUILD_WASM=true
    [ -n "$SERVER_CHANGES" ] && BUILD_SERVER=true
fi

echo "[build] JS=$BUILD_JS  WASM=$BUILD_WASM  SERVER=$BUILD_SERVER"

if ! $BUILD_JS && ! $BUILD_WASM && ! $BUILD_SERVER; then
    echo "[build] Nothing changed. Use --force to rebuild everything."
    exit 0
fi

BUILD_WEB=app/webApp/build/web

# ── 1. JS / CSS ────────────────────────────────────────────────────────────────
if $BUILD_JS; then
    echo "[build] Building JS/CSS..."
    cd app/webApp/ts-src

    MC_OUT_JS="$BUILD_WEB/mc_bindings.js" npm run build
    MC_OUT_CSS="$BUILD_WEB/main.css" npm run build:css
    # Single Tailwind compile then copy — same result as css-sync watcher
    cp "$BUILD_WEB/main.css" "$BUILD_WEB/map.css"
    cp "$BUILD_WEB/main.css" "$BUILD_WEB/admin.css"
    MC_OUT_MAP_JS="$BUILD_WEB/map.js" npm run build:map
    MC_OUT_ADMIN_JS="$BUILD_WEB/admin.js" npm run build:admin

    # The admin page is served from server/src/main/resources/admin.{js,css} (AdminController reads
    # those paths directly), not from the web dist above — so the bundles have to be emitted there as
    # well, or `make build` leaves /admin on a stale bundle. Default script outputs point there.
    echo "[build] Building admin bundle into server resources..."
    npm run build:admin
    npm run build:admin:css

    cd ../../..
    echo "[build] JS/CSS done"
fi

# ── 2. WASM ────────────────────────────────────────────────────────────────────
if $BUILD_WASM; then
    echo "[build] Building WASM (slow)..."
    ./gradlew :app:webApp:copyResourcesToWebDist --rerun-tasks --console=plain
    echo "[build] WASM done"
fi

# ── 3. Server ──────────────────────────────────────────────────────────────────
# pitchfork server daemon runs :server:installDist + binary on its own — no need to pre-build here
if $BUILD_SERVER || $BUILD_WASM; then
    echo "[build] Restarting server (pitchfork will compile + start)..."
    pitchfork restart server

    echo -n "[build] Waiting for server"
    for i in $(seq 1 120); do
        if curl -sf http://localhost:8080/api/server/info >/dev/null 2>&1; then
            echo " ready (${i}s)"
            break
        fi
        sleep 1
        printf "."
    done
fi

touch "$LAST_BUILD"
echo "[build] Done — browser will auto-reload within 5s via /ws"
