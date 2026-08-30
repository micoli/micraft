---
title: Running the apps
---

# Running the apps

```bash
make dev-up                              # server + asset watchers auto-start
```

Open <http://localhost:8080/>. The web client reconnects automatically after a
server restart; a browser hard-refresh is always enough after a rebuild.

If the server is fully unreachable, a running client keeps its state and shows a
reconnect overlay. Reloading the page during an outage serves a small offline
maintenance page (`sw.js` navigation fallback) that polls the server and reloads
once it is back.

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
