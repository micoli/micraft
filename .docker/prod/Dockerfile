# syntax=docker/dockerfile:1

# ── Stage 1: build ────────────────────────────────────────────────────────────
FROM eclipse-temurin:21-jdk-jammy@sha256:ce5767b7222312d42395f5bab033cd91f09e44032a2f21bdfd7b5b912dbe1e77 AS builder

RUN apt-get update && \
    apt-get install -y curl git bash ca-certificates && \
    curl -fsSL https://deb.nodesource.com/setup_26.x | bash - && \
    apt-get install -y nodejs && \
    rm -rf /var/lib/apt/lists/*

WORKDIR /workspace
COPY . .

RUN --mount=type=cache,target=/root/.gradle \
    --mount=type=cache,target=/workspace/.gradle \
    ./gradlew :server:installDist :app:webApp:wasmJsBrowserDistribution --no-daemon

# Server bundles its own web client — assemble the minified JS/CSS/map/admin bundles the
# same way `./gradlew prod` does (build.gradle.kts startDevMode, watch=false), straight into
# the productionExecutable dir the Ktor server will serve via MICRAFT_WEB_DIST.
ENV WEB_DIST=/workspace/app/webApp/build/dist/wasmJs/productionExecutable
RUN --mount=type=cache,target=/root/.npm \
    npm ci --prefix app/webApp/ts-src && \
    MC_OUT_JS="${WEB_DIST}/mc_bindings.js" MC_ESBUILD_FLAGS=--minify \
      npm run build --prefix app/webApp/ts-src && \
    MC_OUT_CSS="${WEB_DIST}/main.css" \
      npm run build:css --prefix app/webApp/ts-src && \
    MC_OUT_MAP_JS="${WEB_DIST}/map.js" \
      npm run build:map --prefix app/webApp/ts-src && \
    MC_OUT_MAP_CSS="${WEB_DIST}/map.css" \
      npm run build:map:css --prefix app/webApp/ts-src && \
    MC_OUT_ADMIN_JS="${WEB_DIST}/admin.js" \
      npm run build:admin --prefix app/webApp/ts-src && \
    MC_OUT_ADMIN_CSS="${WEB_DIST}/admin.css" \
      npm run build:admin:css --prefix app/webApp/ts-src

# ── Stage 2: server runtime (serves both the game and the web client) ──────────
FROM eclipse-temurin:21-jre-jammy@sha256:eebd356ad7358b7094758e5787a6726f332917cfd56feab6457c56dab895cdbf AS server

RUN apt-get update && \
    apt-get install -y blender && \
    rm -rf /var/lib/apt/lists/*

WORKDIR /app
COPY --from=builder /workspace/server/build/install/server ./server
COPY --from=builder /workspace/app/webApp/build/dist/wasmJs/productionExecutable ./web

ENV MICRAFT_WEB_DIST=/app/web

EXPOSE 8080
CMD ["./server/bin/server"]
