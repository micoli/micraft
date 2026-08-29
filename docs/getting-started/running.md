---
title: Running the apps
---

# Running the apps

```bash
make dev-up                              # server + asset watchers auto-start
```

Open <http://localhost:8080/>. The web client reconnects automatically after a
server restart; a browser hard-refresh is always enough after a rebuild.

## Debug texture mode

A single `GRASS` block at `(8, 2, 8)`, player spawned in fly mode at `(8, 1, 14)`.
Keys `1`–`6` snap the camera onto each face.

```bash
make dc CMD="./gradlew devDebug"
# open: http://localhost:8081/?debug&bx=8&by=2&bz=8
```

## Cache recovery (escalating)

| Command | When |
|---------|------|
| `make dev-reset-wasm` | proto decode errors / stale output after core type changes |
| `make dev-reset` | stop daemons, clear build caches, restart (~1 min) |
| `make dev-nuke` | destroy named build volumes + full restart (nuclear, ~2 min) |

## Desktop client

```bash
make dc CMD="./gradlew :app:desktopApp:run"
```
