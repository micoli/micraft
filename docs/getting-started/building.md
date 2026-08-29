---
title: Building the client
---

# Building the client

The web client is Kotlin compiled to WebAssembly, plus a TypeScript/React UI
layer bundled with esbuild. Rebuild outputs land directly in
`app/webApp/build/web/` (the only directory Ktor serves).

| Command | Effect |
|---------|--------|
| `make build` | detect changes, rebuild JS → WASM → server, restart |
| `make build-all` | force a full rebuild |
| `make build-wasm` | WASM only |
| `make build-admin` / `make build-map` | rebuild a single TS bundle |

!!! warning
    Always use `make build-wasm`, never `./gradlew compile*` directly — a
    compile-only run skips `copyResourcesToWebDist` and the browser gets stale
    WASM. Touching a `.kt` file is not enough either: its **content** must
    actually change to trigger a WASM recompile.

## Generated artifacts (never hand-edit)

| File / dir | Regenerate with |
|------------|-----------------|
| `app/webApp/build/web/mc_bindings.js` | edit the Kotlin source that emits it |
| `app/webApp/ts-src/generated/api/**` | `make gen-api` (after changing a server route) |
| `server/openapi/openapi.yaml` + README API table | `make dc CMD="./gradlew :server:exportOpenApi"` |
| README slash-command table | `make docs` |
| `docs/reference/_generated/*.md` | `make docs` |
| `server/src/main/resources/schemas/*.schema.json` | `make gen-schemas` |
