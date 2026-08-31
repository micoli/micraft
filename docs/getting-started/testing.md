---
title: Running tests
---

# Running tests

```bash
make test                                      # everything (Kotlin + web + TS + Storybook)
make dc CMD="./gradlew :server:test"            # server (Kotlin/JVM)
make dc CMD="./gradlew :core:jvmTest"           # shared domain (JVM)
make dc CMD="./gradlew :app:shared:wasmJsTest"  # shared module (Wasm)
make dc CMD="cd app/webApp/ts-src && npm run test"   # TypeScript unit tests
make e2e                                        # browser end-to-end (Playwright)
```

## Browser end-to-end

`make e2e` builds the web client, then runs the Playwright suite in
`app/webApp/ts-src/e2e/`. Each spec drives the real wasm client in headless
Chromium and asserts on the state the client exposes on `window.mcE2E` — never
on screenshots.

The suite runs against **one** Ktor server booted with `MICRAFT_E2E=1`
(`:server:runE2eServer`, port 8091, bounded deterministic generator). Each spec
gets an isolated in-memory `GameWorld` keyed by its Playwright `parallelIndex`
via a `?gameSession=<id>` query param on the game/chunk sockets, so workers run
in parallel without sharing terrain or a player set. Dynamic E2E worlds spawn
players in creative mode and skip NPC/weather/liquid simulation.

- `make e2e-server` — run just the server (or `pitchfork start e2e-server`);
  the Playwright config reuses it when it is already up.
- The client's e2e hooks (`window.mcE2E` snapshot, `window.mcE2E.actions`,
  `?gameSession=`) are inert unless the page sets `window.__mcE2E = true`.

## Before committing

```bash
make quick-code-standard   # format + lint modified files only
make code-standard         # full: spotless + TS + check-docs + check-openapi + check-schemas + check-configuration
```

`check-docs` runs `:server:checkCommandsDocs` **and** `:server:checkReferenceDocs`
— it fails if the README command table or any `docs/reference/_generated/*.md`
fragment drifts from the code. Run `make docs` to regenerate.

## Rules of thumb

- Every change under `server/src/main/` needs a new or updated test in `server/src/test/`.
- Server behaviour reachable by a browser client is also covered by the `make e2e` suite.
- New user-visible strings go in **both** `data/config/i18n/en.yaml` and `fr.yaml`.
- Update the JSON Schema in the same commit as the data-class change.
