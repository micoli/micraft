# Single player-kinematics module in core

Status: resolved
Strength: Strong

## Files
- `server/.../game/tick/MovementProcessor.kt` (`process` L28-176, `applyGravity` L206)
- `app/webApp/src/wasmJsMain/.../LocalPlayerController.kt` (`updateMovementAndPhysics` L769-~1030)
- `core/.../physics/AabbCollider.kt` (only shared piece)
- `server/.../game/GameConstants.kt:9-11`, `config/ConfigRegistry.kt:56-68`, `GameConfigLoader.kt:10-12`

## Problem
The rules that combine the collision primitives are copied by hand, and the two copies have already drifted:
- `GRAVITY` / `JUMP_SPEED` / fly speed are tunable server-side `var`s, but client constants (`CLIENT_GRAVITY`, `CLIENT_JUMP_SPEED`, `FLY_VERTICAL_SPEED`, LPC L58, L75-76).
- Solidity: the server uses `isSolidOrOccupied`, the client uses `isSolid` (LPC L878, L943).
- Submerged: the server checks feet OR eye (MP L79), the client checks eye only (LPC L843-850).
- Stance: the server gates changes with `canAdoptStance` (MP L81-108), the client applies them directly.
- Speed multiplier: stepped ±0.5 on the server vs `localSpeedMult` on the client. The jump branch is structured differently.

## Solution
`core` module `PlayerKinematics.step(state, intent, dt, blockQuery, tuning): KinematicResult`. It hides stance, submerge,
gravity, jump, fly, collision and liquid slowdown. The server pushes the tuning. `MovementProcessor` and the client
become thin adapters over a `BlockQuery` seam.

## Tests
`MovementProcessorTest` has 23 tests. There are 0 client prediction tests. After the move, a parity test can run in `commonTest`.

## Decisions (2026-09-27)

1. **Four slices, one commit each**: (1) `PlayerKinematics` in `core`, extracted from `MovementProcessor` with no
   behaviour change; (2) gravity / jump / fly tuning pushed to the client through the config message, client constants
   removed; (3) client prediction through `PlayerKinematics`; (4) `commonTest` parity test.
2. **Server semantics win** on every divergence (jump then gravity in the same step, feet-or-eye submerge,
   `canAdoptStance`, Y clamp): the server is authoritative (ADR-0001).
3. **`step` covers** position, vertical velocity, stance, flying, speed multiplier, submerge, head in liquid, stuck
   ejection, liquid slowdown and collision. Breath, biome, zone level, mounted riding and logging stay in the server
   adapter.
4. **`BlockQuery` seam**: the server counts occupied entities as solid; the client keeps blocks only (entity
   collision on the client is a separate ticket). Reconciliation absorbs the gap.
5. **Tests**: `PlayerKinematicsTest` in `core/commonTest` with a fake block grid; `MovementProcessorTest` green with
   unchanged assertions; parity of 1 s at 16 ms vs 50 ms steps within reconcile tolerances; `make build` plus the
   movement E2E specs for the client (feel checked by the user in the browser).

## Answer

Done in 4 slices: `710c11ea` (core `PlayerKinematics`, `MovementProcessor` adapter, `MovementProcessorTest` unchanged),
`31093c88` (`KinematicTuning` in `Welcome` / `GameConfigSync`), `e1c6ab17` (client Prediction through `step`), and the
parity commit after it (16 ms vs 50 ms, fly vy, `predStance`). Core + server suites green (2276). Movement E2E specs
match the base commit: `movement-reconciliation` passes; `movement-stances` fails there too (pre-existing);
`login-spawn` fails under parallel load at the base as well and passes alone. Feel in the browser still to be checked.

Left open:
- Client entity collision (the client `BlockQuery` is blocks-only).
- The client never predicts fly toggles or speed-multiplier steps: it learns both from the server state.
- Reconcile tolerance defaults are still defined on the server, the client and in the parity test.
- `physics.MoveIntent` and `ClientMessage.MoveIntent` share a name and are mapped field by field on both sides.

