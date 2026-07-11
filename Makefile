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
        prod-up prod-down prod-restart prod-logs prod-build \
        build-client build-wasm build-js \
        docs help

# ── Dev ───────────────────────────────────────────────────────────────────────

dev-patch-resource-defaults:
	$(EXEC) "./gradlew :server:patchResourceDefaults"

dev-up:
	$(DC_DEV) up --build -d

dev-down:
	$(DC_DEV) down

dev-restart-server:
	$(EXEC) "touch run.lock"

dev-restart-clean-server:
	$(EXEC) "touch run.lock; rm data/world/default_world/*.json data/world/default_world/chunks/* data/config/*/*"

dev-restart:
	make dev-down
	make dev-up

dev-shell:
	$(DC_DEV) exec -it micraft bash

dev-clean-wasm:
	$(EXEC) "rm -rf /workspace/app/webApp/build/klib/cache /workspace/app/webApp/build/compileSync /workspace/app/shared/build/klib/cache /workspace/app/shared/build/compileSync"

dev-logs:
	@while true; do $(DC_DEV) logs -f 2>&1 | scripts/colorlog.pl; sleep 2; echo "===================="; done

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

build-client: build-wasm build-js

build-wasm:
	$(EXEC) "./gradlew :app:webApp:wasmJsDevelopmentExecutableCompileSync"

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
	@echo "Dev  (ports 8080 game-server / 8081 webpack):"
	@echo "  make dev-up               start dev container (source mounted, hot-reload)"
	@echo "  make dev-down             stop"
	@echo "  make dev-restart          restart"
	@echo "  make dev-logs             tail logs"
	@echo "  make shell                open bash inside container (DOCKER mode only)"
	@echo "  make dc CMD=\"<cmd>\"       run command inside container / directly in HOST mode"
	@echo "  make npm-format           run prettier in ts-src"
	@echo "  make build-client         recompile wasm + js bundle (build-wasm + build-js)"
	@echo "  make build-wasm           recompile kotlin/wasm only"
	@echo ""
	@echo "Prod (port 8080 via nginx):"
	@echo "  make prod-build           build images"
	@echo "  make prod-up              start prod stack (detached)"
	@echo "  make prod-down            stop"
	@echo "  make prod-restart         restart"
	@echo "  make prod-logs            tail logs"
