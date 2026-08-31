-include .env
RUN_MODE ?= DOCKER

DC_DEV  = docker compose -f docker-compose.dev.yml
DC_PROD = docker compose -f docker-compose.prod.yml

ifeq ($(RUN_MODE),DOCKER)
  EXEC         = $(DC_DEV) exec micraft bash -c
  PITCHFORK    = $(DC_DEV) exec micraft pitchfork
  PITCHFORK_IT = $(DC_DEV) exec -it micraft pitchfork
else
  EXEC         = bash -c
  PITCHFORK    = pitchfork
  PITCHFORK_IT = pitchfork
endif

.ONESHELL:
.DEFAULT_GOAL := help

.PHONY: dev-up dev-down dev-restart dev-logs dc shell npm-format quick-code-standard \
        dev-restart-server dev-restart-clean-server dev-patch-resource-defaults dev-shell \
        dev-task-stop dev-task-start dev-task-restart dev-tui dev-extract-kay-animations \
        dev-nuke-wasm dev-reset dev-reset-wasm dev-nuke wasm-watch \
        prod-up prod-down prod-restart prod-logs prod-build \
        build build-all build-client build-wasm build-js build-map build-admin build-docs \
        build-plugin-examples-hello-world trigger-wasm storybook gen-api \
        code-standard check-docs check-openapi check-schemas ts-code-standard \
        check-configuration spotless-apply ts-typecheck ts-lint ts-lint-fix \
        ts-test-setup ts-test ts-test-storybook test kt-test kt-test-info kt-web-test \
        e2e e2e-server \
        docs gen-schemas docs-screenshots check-docs-screenshots \
        docs-site-build docs-site-serve docs-site-stop help \
        security security-locks security-relock security-verify security-audit \
        security-osv security-sbom

##@ Dev — daemons (port 8080 game-server)

dev-up: ## Build + start container (pitchfork starts all daemons)
ifeq ($(RUN_MODE),DOCKER)
	$(DC_DEV) up -d
else
	$(PITCHFORK) start -l
endif

dev-down: ## Stop
ifeq ($(RUN_MODE),DOCKER)
	$(DC_DEV) down
else
	$(PITCHFORK) stop -l
endif

dev-restart: ## Full restart (down + up)
ifeq ($(RUN_MODE),DOCKER)
	make dev-down
	make dev-up
else
	$(PITCHFORK) restart -l
endif

dev-restart-server: ## Rebuild + restart Ktor server only — run after every server-side code change
	$(PITCHFORK) restart server

dev-restart-clean-server: ## Wipe world/config state then restart server
	$(EXEC) "rm data/world/default_world/*.json data/world/default_world/chunks/* data/config/*/* || true"
	$(PITCHFORK) restart server

dev-task-stop: ## Stop all pitchfork daemons (keeps container alive)
	$(PITCHFORK) stop -l

dev-task-start: ## Start all pitchfork daemons
	$(PITCHFORK) start -l

dev-task-restart: ## Restart all pitchfork daemons
	$(PITCHFORK) restart -l

dev-tui: ## Live pitchfork dashboard (interactive)
	$(PITCHFORK_IT) tui

dev-logs: ## Tail container logs (server, wasm, mc_bindings, css, map, admin)
	@while true; do $(PITCHFORK_IT) logs server wasm mc_bindings css css\-sync map admin --tail 2>&1 | scripts/colorlog.pl; sleep 2; echo "===================="; done

dev-shell: ## Open bash inside dev container (DOCKER mode only)
ifeq ($(RUN_MODE),DOCKER)
	$(DC_DEV) exec -it micraft bash
else
	@echo "HOST mode: no container shell"
endif

shell: dev-shell ## Alias for dev-shell

dc: ## Run a command inside the dev container: make dc CMD="./gradlew :server:test"
ifeq ($(RUN_MODE),DOCKER)
	$(DC_DEV) exec -it micraft $(CMD)
else
	$(CMD)
endif

dev-patch-resource-defaults: ## Regenerate data/resources/ defaults from resources/
	$(EXEC) "./gradlew :server:patchResourceDefaults"

dev-extract-kay-animations: ## Extract KayKit animation assets
	$(EXEC) "./gradlew :extractKayKitAnimations"

##@ Dev — build & format

npm-format: ## Run prettier on TypeScript sources (ts-src)
	$(EXEC) "cd app/webApp/ts-src && npm run format"

quick-code-standard: ## spotless + prettier + eslint, only on files modified since HEAD
	@files=$$({ git diff --name-only HEAD; git ls-files --others --exclude-standard; } | sort -u | tr '\n' ' '); \
	$(EXEC) "bash scripts/quick-code-standard.sh $$files"

build: ## Detect changes, rebuild in order (JS/CSS → WASM → server), restart, browser auto-reloads
	$(EXEC) "bash ./scripts/dev-build.sh"

build-all: ## Force full rebuild: WASM + JS/CSS + server (ignores change detection)
	$(EXEC) "bash ./scripts/dev-build.sh --force"

build-client: build-js build-wasm ## build-wasm + build-js

build-wasm: ## One-shot WASM recompile (use after any Kotlin/WASM change) — never call gradlew compile* directly
	@if $(PITCHFORK) status wasm 2>/dev/null | grep -qi "running"; then \
		echo "[wasm] watcher running — triggering rebuild via source change…"; \
		$(EXEC) "f=app/webApp/src/wasmJsMain/kotlin/org/micoli/micraft/WasmBuildTrigger.kt; echo 'package org.micoli.micraft' > \$$f; echo \"// wasm-trigger $$(date +%s)\" >> \$$f"; \
	else \
		$(EXEC) "./gradlew :app:webApp:copyResourcesToWebDist --rerun-tasks"; \
	fi

wasm-watch: ## Start WASM continuous watcher (opt-in; use when iterating heavily on Kotlin/WASM code)
	$(PITCHFORK) start wasm

trigger-wasm: ## Force wasm rebuild via source touch (use when wasm-watch is running)
	$(EXEC) "f=app/webApp/src/wasmJsMain/kotlin/org/micoli/micraft/WasmBuildTrigger.kt; echo 'package org.micoli.micraft' > \$$f; echo \"// wasm-trigger $$(date +%s)\" >> \$$f"

build-js: ## Rebuild mc_bindings bundle (also copies babylon.js)
	$(EXEC) "cd app/webApp/ts-src && npm run build"

build-map: ## Rebuild map.js + map.css
	$(EXEC) "cd app/webApp/ts-src && npm run build:map && npm run build:map:css"

build-admin: ## Rebuild admin.js + admin.css
	$(EXEC) "cd app/webApp/ts-src && npm run build:admin && npm run build:admin:css"

build-docs: ## Build ts-src docs bundle
	$(EXEC) "cd app/webApp/ts-src && npm run build:docs"

build-plugin-examples-hello-world: ## Build the hello-world plugin example shadow jar
	$(EXEC) "./gradlew :plugin-examples:hello-world:shadowJa"

gen-api: ## Regenerate TanStack Query hooks/types from server/openapi/openapi.yaml
	$(EXEC) "./gradlew :server:exportOpenApi"
	$(EXEC) "cd app/webApp/ts-src && npm run gen:api"

storybook: ## Launch Storybook dev server
	$(EXEC) "cd app/webApp/ts-src && npm run storybook"

##@ Dev — cache recovery

dev-nuke-wasm: ## Nuke WASM + core klib caches in-container (follow with make build-wasm)
	$(EXEC) "rm -rf \
	  ./core/build/classes/kotlin/wasmJs \
	  ./core/build/kotlin/wasmJs \
	  ./core/build/kotlin/compileKotlinWasmJs \
	  ./core/build/klib \
	  ./app/webApp/build/klib \
	  ./app/webApp/build/compileSync \
	  ./app/shared/build/klib \
	  ./app/shared/build/compileSync"

dev-reset-wasm: dev-nuke-wasm build-wasm ## Nuke WASM caches + full recompile in one shot (proto decode errors / stale output after core type changes)

dev-reset: ## Stop daemons, clear all build caches, restart (faster than dev-nuke; keeps build/web/ and gradle-home/node_modules)
	$(PITCHFORK) stop -l
	$(EXEC) "rm -rf \
	  ./core/build \
	  ./server/build \
	  ./app/shared/build \
	  ./app/webApp/build/klib \
	  ./app/webApp/build/compileSync"
	$(PITCHFORK) start -l

dev-nuke: ## Destroy all named build volumes + full restart (nuclear option, ~2 min)
	$(DC_DEV) down -v
	$(DC_DEV) up --build -d

##@ Standard & code analysis

code-standard: spotless-apply ts-code-standard check-docs check-configuration check-openapi check-schemas ## Full lint on Kotlin + TypeScript

check-docs: ## Verify generated docs are up to date (commands + reference tables)
	$(EXEC) "./gradlew :server:checkCommandsDocs :server:checkReferenceDocs"

check-openapi: ## Fail if server/openapi/openapi.yaml drifts from annotated routes
	$(EXEC) "./gradlew :server:checkOpenApi"

check-schemas: ## Fail if data/config/schemas/ drifts from data classes
	$(EXEC) "./gradlew :server:checkJsonSchemas"

check-configuration: ## Validate server config against schema
	$(EXEC) "./gradlew :server:validateConfig"

ts-code-standard: npm-format ts-typecheck ts-lint ## Format + typecheck + lint TypeScript

spotless-apply: ## Apply Kotlin formatting (spotless)
	$(EXEC) "./gradlew :spotlessApply"

ts-typecheck: ## TypeScript typecheck (ts-src)
	$(EXEC) "cd app/webApp/ts-src/; npm run typecheck"

ts-lint: ## ESLint (ts-src)
	$(EXEC) "cd app/webApp/ts-src/; npm run lint"

ts-lint-fix: ## ESLint --fix (ts-src)
	$(EXEC) "cd app/webApp/ts-src/; npm run lint:fix"

##@ Test

test: kt-test kt-web-test ts-test ts-test-storybook ## Run all test suites (Kotlin + web + TS + Storybook)

kt-test: ## Kotlin core + server tests
	$(EXEC) "./gradlew :core:jvmTest :server:test --rerun-tasks --parallel"

kt-test-info: ## Kotlin core + server tests with --info logging
	$(EXEC) "./gradlew :core:jvmTest --rerun-tasks --info"
	$(EXEC) "./gradlew :server:test --rerun-tasks --info"

kt-web-test: ## Kotlin/Wasm + Kotlin/JS shared module tests
	$(EXEC) "./gradlew :app:shared:wasmJsTest"
	$(EXEC) "./gradlew :app:shared:jsTest"

ts-test-setup: ## Install Playwright chromium (one-time, for ts-test)
	$(EXEC) "cd app/webApp/ts-src/; npx playwright install chromium --with-deps"

ts-test: ## TypeScript unit/integration tests
	$(EXEC) "cd app/webApp/ts-src/; npm run test"

ts-test-storybook: ## Storybook test-runner (CI mode)
	$(EXEC) "cd app/webApp/ts-src && npm run test-storybook:ci"

e2e-server: ## Run the bounded E2E Ktor server standalone (port 8091)
	$(EXEC) "./gradlew :server:runE2eServer --console=plain"

e2e: ## Build the client (wasm + mc_bindings + chunk worker + css), then run the Playwright browser E2E suite
	$(MAKE) build-wasm
	$(EXEC) "cd app/webApp/ts-src && npm run build:game"
	$(EXEC) "cd app/webApp/ts-src && npx playwright install chromium --with-deps && npm run test:e2e"

##@ Docs

docs: ## Regenerate all generated docs (README commands + docs/reference/_generated tables)
	$(EXEC) "./gradlew :server:generateCommandsDocs :server:generateReferenceDocs"

gen-schemas: ## Regenerate data/config/schemas/ JSON Schemas from data classes
	$(EXEC) "./gradlew :server:generateJsonSchemas"

docs-screenshots: ## Regenerate docs/assets/stories/*.png from tagged Storybook stories
	$(EXEC) "cd app/webApp/ts-src && npm run screenshot-stories"

check-docs-screenshots: ## Fail if committed story screenshots / manifest are stale
	$(EXEC) "cd app/webApp/ts-src && npm run screenshot-stories -- --check"

docs-site-build: ## Build the static documentation site into ./site
	$(EXEC) "DISABLE_MKDOCS_2_WARNING=true bash scripts/docs/mkdocs.sh build --strict -f mkdocs.yml"

docs-site-serve: ## Serve the docs site with live-reload (http://localhost:8000)
	$(PITCHFORK) start docs

docs-site-stop: ## Stop the docs daemon
	$(PITCHFORK) stop docs

##@ Prod (port 8080 via nginx)

prod-build: ## Build prod images
	DOCKER_BUILDKIT=1 $(DC_PROD) build

prod-up: ## Start prod stack (detached)
	DOCKER_BUILDKIT=1 $(DC_PROD) up -d

prod-down: ## Stop prod stack
	$(DC_PROD) down

prod-restart: ## Restart prod stack
	$(DC_PROD) restart

prod-logs: ## Tail prod logs
	$(DC_PROD) logs -f

##@ Security — supply-chain

security: security-locks security-verify security-audit security-sbom security-osv ## Run the full supply-chain check chain

security-locks: ## Regenerate Gradle + npm lockfiles (gradle.lockfile, package-lock.json)
	$(EXEC) "./gradlew dependencies --write-locks --no-configuration-cache"
	$(EXEC) "cd app/webApp/ts-src && npm ci"

security-relock: ## Bump one locked dep: make security-relock ARGS='group:module'
	$(EXEC) "./gradlew dependencies --update-locks $(ARGS) --no-configuration-cache"

security-verify: ## Refresh gradle/verification-metadata.xml (sha256 checksums) — review the diff before commit
	$(EXEC) "./gradlew --write-verification-metadata sha256 build :server:test :app:webApp:wasmJsBrowserDistribution --dry-run --no-configuration-cache"

security-audit: ## npm audit (high+) + Gradle dependency report
	$(EXEC) "cd app/webApp/ts-src && npm audit --audit-level=high"

security-osv: ## Scan all lockfiles with OSV-Scanner
	$(EXEC) "osv-scanner scan --lockfile=app/webApp/ts-src/package-lock.json --lockfile=kotlin-js-store/yarn.lock --lockfile=gradle.lockfile || true"

security-sbom: ## Generate CycloneDX SBOMs (Gradle + npm) into build/reports/
	$(EXEC) "./gradlew cyclonedxBom"
	$(EXEC) "cd app/webApp/ts-src && npm sbom --sbom-format=cyclonedx --sbom-type=application > ../../../build/reports/sbom-npm.json"

##@ Help

help: ## Show this help
	@echo "RUN_MODE=$(RUN_MODE)  (DOCKER|HOST, default DOCKER — override via .env or env var)"
	@awk 'BEGIN {FS = ":.*##"; printf "\n"} \
		/^[a-zA-Z0-9_-]+:.*?##/ { printf "  \033[36m%-32s\033[0m %s\n", $$1, $$2 } \
		/^##@/ { printf "\n\033[1m%s\033[0m\n", substr($$0, 5) }' $(MAKEFILE_LIST)
