# dev-reset-wasm

Force a full recompile of the Kotlin/WASM client when the browser still shows
"Protobuf decode error … unknown key 'X'" after a change to a `core` data class.

## Symptoms

- Hard refresh + service-worker unregistration don't help.
- `build-wasm` ran but Gradle reused a cached `core` klib → new fields missing from the binary.
- Verify: `strings app/webApp/build/compileSync/wasmJs/main/developmentExecutable/kotlin/MiCraft-app-webApp.wasm | grep myNewField`

## Fix

```bash
make dev-reset-wasm
```

This nukes all WASM + core klib caches in-container, then runs a full one-shot recompile
(`./gradlew :app:webApp:copyResourcesToWebDist --rerun-tasks`). No container restart needed.

After the build, hard-refresh the browser (the `.wasm` filename hash changes so a normal
refresh is enough once the new `index.html` loads).

## Why `make build-wasm` isn't enough

`make build-wasm` runs a one-shot Gradle build but may reuse the cached `core` klib if none
of core's wasmJs outputs are marked dirty — even if `core/src/commonMain` files changed.

`dev-reset-wasm` → `dev-nuke-wasm` removes the stale klib first, so `--rerun-tasks` forces
a clean recompile of both `core` (wasmJs) and the final bundle.
