# Single player-kinematics module in core

Status: needs-triage
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
