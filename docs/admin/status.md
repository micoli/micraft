---
title: Status
---

# Status

Server health snapshot: TPS, connected players, loaded chunks, in-game time
control, network counters, JVM heap and CPU. Includes a **server restart** button
(pitchfork restart).

| Route | Purpose |
|-------|---------|
| `GET /api/admin/status` | snapshot (TPS, players, chunks, heap, CPU) |
| `PUT /api/admin/gametime` | set the in-game time of day |
| `POST /api/admin/restart` | trigger a pitchfork restart |
| `POST /api/admin/reload` | reload config files (same as `/reload`) |

In-game equivalents: `/time [0-23]`, `/save`. Metrics are also exposed by
`MetricsController` (heap, non-heap, tick counters, liquid/vegetation estimates).
