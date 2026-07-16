# force-update-wasm

Force a full recompile of the Kotlin/WASM client when the browser still receives
"Protobuf decode error … unknown key 'X'" after a change to a `core` data class.

## Symptoms

- Hard refresh + service-worker unregistration don't help.
- `build-wasm` (which only appends a comment to `BabylonBindingsWorld.kt`) triggered
  the wasm watcher but Gradle reused a cached `core` klib → new fields missing from binary.
- Verify: `grep -c "myNewField" app/webApp/build/web/webApp.js` returns 0 (fields live in
  the .wasm binary, so also check):
  `strings app/webApp/build/compileSync/wasmJs/main/developmentExecutable/kotlin/MiCraft-app-webApp.wasm | grep myNewField`

## Fix

```bash
docker compose -f docker-compose.dev.yml exec micraft \
  ./gradlew :core:compileKotlinWasmJs :app:webApp:copyResourcesToWebDist --rerun-tasks
```

`--rerun-tasks` bypasses Gradle's up-to-date cache and forces recompilation of both
`core` (for wasmJs) and the final webpack bundle + copy to `build/web/`.

After the build completes, hard-refresh the browser (the .wasm filename hash changes,
so a normal refresh is enough once the new `index.html` is loaded).

## Why `make build-wasm` isn't enough

`make build-wasm` detects the pitchfork wasm watcher running and triggers it via a
source-file timestamp change. The `--continuous` watcher recompiles the `app:webApp`
module but may reuse a cached klib for `core` if none of core's wasmJs outputs are
marked dirty — even though `core/src/commonMain` files changed.

The explicit `--rerun-tasks` on `:core:compileKotlinWasmJs` forces the klib to rebuild,
then `copyResourcesToWebDist` bundles and deploys it.
