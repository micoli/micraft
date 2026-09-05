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

# ── Stage 2: server runtime ────────────────────────────────────────────────────
FROM eclipse-temurin:21-jre-jammy@sha256:eebd356ad7358b7094758e5787a6726f332917cfd56feab6457c56dab895cdbf AS server

RUN apt-get update && \
    apt-get install -y blender && \
    rm -rf /var/lib/apt/lists/*

WORKDIR /app
COPY --from=builder /workspace/server/build/install/server ./server

EXPOSE 8080
CMD ["./server/bin/server"]

# ── Stage 3: web frontend (nginx) ─────────────────────────────────────────────
FROM nginx:alpine@sha256:72ba65eb42c10344912a84ff42408db7d34f2feb642204570ab8fc5ffd29f1d3 AS web

COPY --from=builder /workspace/app/webApp/build/dist/wasmJs/productionExecutable /usr/share/nginx/html
COPY nginx.prod.conf /etc/nginx/conf.d/default.conf

EXPOSE 8080
