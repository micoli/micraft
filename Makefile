-include .env
RUN_MODE ?= DOCKER

DC_DEV  = docker compose -f docker-compose.dev.yml
DC_PROD = docker compose -f docker-compose.prod.yml

ifeq ($(RUN_MODE),DOCKER)
  EXEC = $(DC_DEV) exec micraft bash -c
else
  EXEC = bash -c
endif

.PHONY: dev-up dev-down dev-restart dev-clean-wasm dev-logs dc shell npm-format \
        dev-restart-server dev-restart-clean-server \
        dev-task-stop dev-task-start dev-task-restart \
        prod-up prod-down prod-restart prod-logs prod-build \
        build-client build-wasm build-js trigger-wasm force-update-wasm \
        docs help

# ── Dev ───────────────────────────────────────────────────────────────────────

dev-patch-resource-defaults:
	$(EXEC) "./gradlew :server:patchResourceDefaults"

dev-up:
	$(DC_DEV) up --build -d

dev-down:
	$(DC_DEV) down

dev-restart-server:
	$(DC_DEV) exec micraft pitchfork restart server

dev-restart-clean-server:
	$(EXEC) "rm data/world/default_world/*.json data/world/default_world/chunks/* data/config/*/*"
	$(DC_DEV) exec micraft pitchfork restart server

dev-restart:
	make dev-down
	make dev-up

dev-shell:
	$(DC_DEV) exec -it micraft bash

dev-task-stop:
	$(DC_DEV) exec micraft pitchfork stop -l

dev-task-start:
	$(DC_DEV) exec micraft pitchfork start -l

dev-task-restart:
	$(DC_DEV) exec micraft pitchfork restart -l

dev-clean-wasm:
	$(EXEC) "rm -rf /workspace/app/webApp/build/klib/cache /workspace/app/webApp/build/compileSync /workspace/app/shared/build/klib/cache /workspace/app/shared/build/compileSync"

# Force full recompile of core + wasm when Gradle reuses a stale klib (e.g. after core data class changes)
force-update-wasm:
	-$(DC_DEV) exec micraft pitchfork stop wasm
	$(EXEC) "rm -rf /workspace/core/build/classes/kotlin/wasmJs /workspace/core/build/kotlin/wasmJs /workspace/core/build/klib"
	$(EXEC) "./gradlew :core:compileKotlinWasmJs :app:webApp:copyResourcesToWebDist --rerun-tasks"
	-$(DC_DEV) exec micraft pitchfork start wasm

dev-tui:
	$(DC_DEV) exec -it micraft pitchfork tui

dev-logs:
	@while true; do $(DC_DEV) exec -it micraft pitchfork logs server wasm mc_bindings css map map-css admin admin-css --tail 2>&1 | scripts/colorlog.pl; sleep 2; echo "===================="; done

# Run any command inside the dev container: make dc CMD="./gradlew :server:test"
ifeq ($(RUN_MODE),DOCKER)
dc:
	$(DC_DEV) exec -it micraft $(CMD)
else
dc:
	$(CMD)
endif

# Open a bash shell in the dev container
ifeq ($(RUN_MODE),DOCKER)
shell:
	$(DC_DEV) exec -it micraft bash
else
shell:
	@echo "HOST mode: no container shell"
endif

# Format TypeScript sources (runs npm run format in ts-src)
npm-format:
	$(EXEC) "cd app/webApp/ts-src && npm run format"

build-client: build-js build-wasm

# When the pitchfork wasm watcher is running, trigger via source file touch to avoid
# Gradle project lock contention. Otherwise run a one-shot build.
build-wasm:
	@if $(DC_DEV) exec micraft pitchfork status wasm 2>/dev/null | grep -qi "running"; then \
		echo "[wasm] watcher running — triggering rebuild via source change…"; \
		$(EXEC) "f=app/webApp/src/wasmJsMain/kotlin/org/micoli/micraft/babylon/BabylonBindingsWorld.kt; sed -i '/^\\/\\/ wasm-trigger/d' \$$f; echo \"// wasm-trigger $$(date +%s)\" >> \$$f"; \
	else \
		$(EXEC) "./gradlew :app:webApp:copyResourcesToWebDist --rerun-tasks"; \
	fi
copy-wasm:
	$(EXEC) "./gradlew :app:webApp:copyResourcesToWebDist --rerun-tasks"; \

# Explicitly trigger the --continuous wasm watcher (use when ./gradlew :dev is running).
trigger-wasm:
	$(EXEC) "f=app/webApp/src/wasmJsMain/kotlin/org/micoli/micraft/babylon/BabylonBindingsWorld.kt; sed -i '/^\\/\\/ wasm-trigger/d' \$$f; echo \"// wasm-trigger $$(date +%s)\" >> \$$f"

build-js:
	$(EXEC) "cd app/webApp/ts-src && npm run build"

storybook:
	$(EXEC) "cd app/webApp/ts-src && npm run storybook"

# ── Prod ──────────────────────────────────────────────────────────────────────

prod-build:
	DOCKER_BUILDKIT=1 $(DC_PROD) build

prod-up:
	DOCKER_BUILDKIT=1 $(DC_PROD) up -d

prod-down:
	$(DC_PROD) down

prod-restart:
	$(DC_PROD) restart

prod-logs:
	$(DC_PROD) logs -f

# ── Standard and code analysis ────────────────────────────────────────────────

code-standard: spotless-apply ts-typecheck

spotless-apply:
	$(EXEC) "./gradlew :spotlessApply"

ts-typecheck:
	$(EXEC) "cd app/webApp/ts-src/; npm run typecheck"

test: kt-test web-test

kt-test:
	$(EXEC) "./gradlew :core:jvmTest --rerun-tasks"
	$(EXEC) "./gradlew :server:test --rerun-tasks"

web-test:
	$(EXEC) "./gradlew :app:shared:wasmJsTest"
	$(EXEC) "./gradlew :app:shared:jsTest"

# ── Docs ──────────────────────────────────────────────────────────────────────

docs:
	$(EXEC) "node scripts/generate_commands_docs.mjs"

# ── Help ──────────────────────────────────────────────────────────────────────

help:
	@echo "RUN_MODE=$(RUN_MODE)  (DOCKER|HOST, default DOCKER — override via .env or env var)"
	@echo ""
	@echo "Dev  (port 8080 game-server):"
	@echo "  make dev-up               build + start container (pitchfork starts all daemons)"
	@echo "  make dev-down             stop"
	@echo "  make dev-restart          full restart (down + up)"
	@echo "  make dev-restart-server   rebuild + restart Ktor only (replaces: touch run.lock)"
	@echo "  make dev-task-stop        stop all pitchfork daemons (keeps container alive)"
	@echo "  make dev-task-start       start all pitchfork daemons"
	@echo "  make dev-task-restart     restart all pitchfork daemons"
	@echo "  make dev-logs             tail container logs"
	@echo "  make shell                open bash inside container (DOCKER mode only)"
	@echo "  make dc CMD=\"<cmd>\"       run command inside container / directly in HOST mode"
	@echo "  make dc CMD=\"pitchfork list\"         check daemon status"
	@echo "  make dc CMD=\"pitchfork logs server\"  tail server log"
	@echo "  make dc CMD=\"pitchfork tui\"          live dashboard (interactive)"
	@echo "  make npm-format           run prettier in ts-src"
	@echo "  make build-client         recompile wasm + js bundle (build-wasm + build-js)"
	@echo "  make build-wasm           recompile kotlin/wasm (triggers pitchfork watcher if running)"
	@echo "  make trigger-wasm         force wasm rebuild via source touch"
	@echo "  make force-update-wasm    nuke stale core klib + full recompile (fixes proto decode errors after core changes)"
	@echo ""
	@echo "Prod (port 8080 via nginx):"
	@echo "  make prod-build           build images"
	@echo "  make prod-up              start prod stack (detached)"
	@echo "  make prod-down            stop"
	@echo "  make prod-restart         restart"
	@echo "  make prod-logs            tail logs"
