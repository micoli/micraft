# syntax=docker/dockerfile:1

# ── Stage 1: build ────────────────────────────────────────────────────────────
FROM eclipse-temurin:25-jdk-jammy@sha256:89565961a318534f01c971c7b1d030e60713c66995b887c94010cef938dbc53e AS builder

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

# ── Stage 2: server runtime ────────────────────────────────────────────────────
FROM eclipse-temurin:25-jre-jammy@sha256:10c251954d0bfe1a59ba93505f8c628d755919412400aa98685764c9353605d6 AS server

RUN apt-get update && \
    apt-get install -y blender && \
    rm -rf /var/lib/apt/lists/*

WORKDIR /app
COPY --from=builder /workspace/server/build/install/server ./server

EXPOSE 8080
CMD ["./server/bin/server"]

# ── Stage 3: web frontend (nginx) ─────────────────────────────────────────────
FROM nginx:alpine@sha256:db35bfc6b2951e7f8a72db5db120288c127ffaeeb4a6d4b95a26fead017d5913 AS web

COPY --from=builder /workspace/app/webApp/build/dist/wasmJs/productionExecutable /usr/share/nginx/html
COPY nginx.prod.conf /etc/nginx/conf.d/default.conf

EXPOSE 8080
