# syntax=docker/dockerfile:1

# ── Stage 1: build ────────────────────────────────────────────────────────────
FROM eclipse-temurin:21-jdk-jammy AS builder

RUN apt-get update && \
    apt-get install -y curl git bash && \
    curl -fsSL https://deb.nodesource.com/setup_26.x | bash - && \
    apt-get install -y nodejs && \
    rm -rf /var/lib/apt/lists/*

WORKDIR /workspace
COPY . .

RUN --mount=type=cache,target=/root/.gradle \
    --mount=type=cache,target=/workspace/.gradle \
    ./gradlew :server:installDist :app:webApp:wasmJsBrowserDistribution --no-daemon

# ── Stage 2: server runtime ────────────────────────────────────────────────────
FROM eclipse-temurin:21-jre-jammy AS server

WORKDIR /app
COPY --from=builder /workspace/server/build/install/server ./server

EXPOSE 8080
CMD ["./server/bin/server"]

# ── Stage 3: web frontend (nginx) ─────────────────────────────────────────────
FROM nginx:alpine AS web

COPY --from=builder /workspace/app/webApp/build/dist/wasmJs/productionExecutable /usr/share/nginx/html
COPY nginx.prod.conf /etc/nginx/conf.d/default.conf

EXPOSE 8080
