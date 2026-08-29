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
```

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
- New user-visible strings go in **both** `data/config/i18n/en.yaml` and `fr.yaml`.
- Update the JSON Schema in the same commit as the data-class change.
