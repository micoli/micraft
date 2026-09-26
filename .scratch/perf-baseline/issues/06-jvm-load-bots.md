# JVM load bots: scenario D

Status: needs-triage
Type: task
Blocked by: 02, 03

## Goal

`make perf-load N=25` connects N headless Characters that move like players, and records the server snapshot.

## Scope

- New `perf/bots` JVM module depending on `core` (protocol codec, `ClientMessage`/`ServerMessage`) and a Ktor WebSocket client.
- Per bot: create a Character through `POST /api/admin/players`, log in (`POST /auth/login`), `Connect`, then loop
  `MoveIntent` at the real client's send rate. The pattern is a seeded random walk with occasional jumps and stance
  changes, spread in a ring around spawn so the bots load distinct chunks.
- Bots consume chunk messages without decoding the payload, so the load generator's own CPU does not skew the result.
- Ramp-up of 1 bot/s, then 5 min steady; server snapshot at the end; runs for N = 10, 25, 50.
- The bots report their own send/receive rate, so a saturated load generator is detectable.

## Acceptance

- 50 bots sustained 5 min on the reference Mac with the bots' CPU < 1 core.
- The result JSON has the same shape as the Playwright runner's server part.
