# Rails and vehicles — analysis

## Context

Adding orientable/stateful `rail` blocks, forming `segment`s (open line) or `loop`s (closed loop), and a placeable `vehicle` item that moves along them at a constant SPEED, changing direction at the end of a segment, or turning indefinitely on a loop.

## Current state of the code (recap)

- **BlockDefinition** (`core/.../game/world/BlockDefinition.kt`): static per-`BlockType` properties — `hardness`, `solid`, `isCubic`, `brickSize`, `rotatable`, `modelElement`. Loaded from `resources/blocks/<NAME>/<NAME>.yaml` (+ override `data/resources/blocks/<name>/<name>.yaml`).
- **BlockState** (`core/.../game/world/BlockState.kt`): **1 byte per block** — bits 0-1 = cardinal rotation (0..3), bits 2-7 = palette color index (0-63). No arbitrary multi-value state today.
- **Existing rotation**: only 4 orientations at 90°, handled at placement time (`LocalPlayerController.kt`, `BlockPlacer.kt`), rendered via `SceneMesher.kt` which indexes meshes as `(ord*4 + rotation)*6` — so up to 4 mesh variants per shape. Existing non-cubic oriented example: `LEGO_SLOPE`, `LEGO_CORNER`, `LEGO_STEP_*`.
- **No stateful block** (e.g. open/closed door type) exists in the repo — the `BlockState` byte is already saturated by rotation+color.
- **ItemDefinition** (`resources/config/items.yaml`): `placesBlock: BlockType?` — an item places a block, period. No precedent for an item that spawns a mobile entity.
- **NPC** (`server/.../game/npc/`): mature server-authoritative pipeline — `NpcBehavior.tick(instance, world, ctx)`, `NpcPhysics`, `AabbCollider`, `NpcTickPipeline`, `NpcSpawner`. `RandomMovableNpcBehavior` wanders via a state machine (Pausing/Moving/Decel). A good reusable skeleton for constrained movement along a trajectory, but nothing existing for following a rail.

## 1. Rail blocks

### 1.1 Block types to create

| BlockType | Geometry | Rotatable | Notes |
|---|---|---|---|
| `RAIL_STRAIGHT` | straight, horizontal | yes (4×90°) | base segment |
| `RAIL_CURVE_90` | 90° horizontal curve | yes (4×90°) | |
| `RAIL_CURVE_60` | 60° horizontal curve | yes (4×90°) | |
| `RAIL_SLOPE_45` | straight, 45° incline | yes (4×90°) | slope — non-cubic geometry like `LEGO_SLOPE`, up/down direction carried by the model's angle, not by a state bit |
| `RAIL_SLOPE_22` | straight, 22.5° incline | yes (4×90°) | same, up/down direction carried by the model's angle |
| `RAIL_Y_SPLIT_90` | Y-junction: 1 entry, 2 exits at 90° from each other | yes (4×90°) | **stateful**: active branch selected by 1 state bit, toggled by player right-click via the generic `BlockInteract` mechanism (§1.5) |

`RAIL_CURVE_45` was considered but dropped from scope — `RAIL_CURVE_90` and `RAIL_CURVE_60` are sufficient for v1.

### 1.2 Rotation/angle impact

`BlockState` codes rotation on 2 bits (4 values) — **sufficient as-is, no extension needed for rotation**. All rails (including the 60°/22.5° curves/slopes) stay on the existing 4×90° rotation model: the fine angle (60° vs 22.5°, etc.) is carried by the `BlockType` itself (i.e. by the 3D model/`modelElement`), not by the state. `RAIL_CURVE_90` and `RAIL_CURVE_60` are two distinct block types, each rotatable across the 4 standard cardinal orientations — same as `LEGO_SLOPE`/`LEGO_CORNER` already. Same for slopes: the up/down direction is a property of the model (the inclined `modelElement` already points a given way), not an extra state bit.

### 1.3 Stateful block (`RAIL_Y_SPLIT_90`)

First block in the game to have functional state beyond rotation+color. The current `BlockState` byte (2 bits rotation + 6 bits color) has no free bit left to carry the active-branch flag.

**Decision: extend `BlockState` from 1 to 2 bytes.** Byte 0 keeps its current meaning unchanged (rotation + color). Byte 1 is a generic "extra state" byte — `BlockState` itself stays agnostic about what byte 1 means (same as it's agnostic about what the color palette means today); interpretation of byte-1 bits lives in per-block-type logic. For `RAIL_Y_SPLIT_90`, bit 0 of byte 1 = active branch (0 = branch A, 1 = branch B). Bits 1-7 of byte 1 are reserved for future stateful blocks (doors, levers, etc.) — this is the extensibility payoff of choosing a generic byte over stealing a color bit.

Since the project is not yet published, **no save-format migration is needed**: the format changes directly, and existing local test-world saves (`data/world/default_world/{terrain_cache,chunks,scenes}/*`) are simply deleted rather than migrated. This removes what would otherwise be the highest-risk part of the change (legacy-format detection/versioning).

Semantics of `RAIL_Y_SPLIT_90`: the state controls which output branch is active for vehicle passage — toggled via player interaction (right-click) or command, like a switch. Only 1 bit is needed (2 mutually exclusive values), even though the original request phrased it as "state1/state2".

### 1.4 Segment vs loop — a server-side notion, not a block property

`segment` and `loop` are **not** individual block properties but a property **derived** from the topology of the rail network, computed server-side:

- A `segment` = a chain of connected rail blocks whose two ends do not reconnect to each other (either "open" ends, or termination on a non-rail block).
- A `loop` = a chain of connected rail blocks that closes back on itself (the "exit" neighbor of the last block is the first block).

Detection: graph traversal (BFS/DFS) starting from each modified rail block (placed/broken), following "entry/exit" connections derived from rotation + block type (e.g. `RAIL_STRAIGHT` has 2 opposite connections depending on its rotation; `RAIL_Y_SPLIT_90` has 1 entry + 2 possible exits depending on its state). Both branches of a `RAIL_Y_SPLIT_90` always exist as graph edges for topology purposes; only the currently-active one is used by vehicle traversal — this avoids re-running a full network BFS on every switch toggle. Result cached per region/chunk and invalidated on `BlockBreaker`/`BlockPlacer` touching a rail block — new server component, e.g. `RailNetworkRegistry` (mirrors the `SceneRegistry`/`BlockRegistry` pattern).

### 1.5 Block interaction mechanism

No existing mechanism lets a player interact with a placed block beyond breaking/placing (`BlockBreaker`/`BlockPlacer` cover left-click-break and place only). A new, **generic** interaction mechanism is introduced: `ClientMessage.BlockInteract(pos)` sent on right-click against a targeted block, handled server-side by a new `BlockInteractor` (mirrors `BlockPlacer`/`BlockBreaker`'s shape: protected-zone + max-distance validation), dispatching by block type. For v1 this only handles `RAIL_Y_SPLIT_90` (flips the active-branch bit), but the mechanism itself is block-type-agnostic and reusable for future stateful blocks (doors, levers) without protocol changes.

## 2. Vehicle item

### 2.1 Placement

No precedent for an item spawning an entity (`ItemDefinition.placesBlock` only places a block). Approach: **pure entity** — new field `ItemDefinition.spawnsEntity: EntityType?` (alongside `placesBlock`), where `EntityType` is a new enum (one entry per spawnable vehicle type, e.g. `CART`) — following the same pattern as `BlockType`/`ItemType`: enum for compile-time identity, properties loaded from YAML (`VehicleDefinition`). The vehicle is a server-authoritative entity distinct from the voxel world, like an NPC — consistent with the existing NPC pipeline.

Placement is **exclusively** via a dedicated slash command: `/vehicule:add <vehiculeName>` — resolves the block targeted by the player (raycast, like block placement), validates it's a `RAIL_*` block, spawns the vehicle entity on it. Item-consumption-based placement (right-click with the item in hand, like `placesBlock`) was considered and explicitly **rejected for v1** — it would add inventory-management surface not required by the feature, and the command-only path matches simpler, more testable server-side validation. The initial direction of travel is determined by the angle between the player's facing direction and the targeted rail's orientation (the player looking "with" or "against" the rail's direction determines the starting direction) — this is a new computation; no existing auto-orient-at-placement mechanism exists to reuse.

### 2.2 Server behavior (`VehicleBehavior`)

New `VehicleBehavior` — a *sibling* interface to `NpcBehavior`, not a subtype of it (there is no shared Entity/Movable abstraction between Player and NPC in the current codebase, so this is necessarily net-new, not a plug-in to something generic):

- State: position on the rail network = (current rail block, progress 0..1 along the block, direction of travel).
- `tick()`: advances by `SPEED * deltaTime` along the current rail.
  - On a `segment`: at the end of the segment (last block, no outgoing connection in the current direction), reverses the direction of travel (U-turn) — per the "changes direction" requirement.
  - On a `loop`: at the end of a block, always moves to the next one in the loop, never stopping — advances "indefinitely".
  - On `RAIL_Y_SPLIT_90`: the exit taken depends on the block's current state (switch) at the moment of passage.
- Reuses `AabbCollider`/`NpcPhysics` for Y height (slopes `RAIL_SLOPE_*`) only — **not** `NpcPhysics.applyGravity` and **not** `RandomMovableNpcBehavior`'s free XZ movement/waypoint logic, since XZ displacement is **constrained to the rail** (no free physics like a wandering NPC) — so no lateral collision to compute, just following the current block's geometric curve (interpolation along the shape of the `modelElement`: straight, 60°/90° arc, or slope).
- Position/orientation replicated to the client via a dedicated network message (new `ServerMessage.VehicleSpawned`/`VehicleUpdate`/`VehicleDespawned`, with its own `VehicleState` — not reusing `NpcState`, since hp/aggro/xp/animalData fields don't apply), at server tick frequency, with client-side interpolation like other entities.

### 2.3 Segment/loop detection for behavior

`VehicleBehavior` queries `RailNetworkRegistry` (§1.4) on each rail-block change to know whether its current block belongs to a `segment` (→ handle U-turns at the ends) or a `loop` (→ never U-turn). The vehicle does not need to recompute the topology itself.

## 3. Client rendering

- Non-stateful rails (`RAIL_STRAIGHT`, `RAIL_CURVE_*`, `RAIL_SLOPE_*`): static mesh per `(BlockType, rotation)`, identical pattern to `LEGO_SLOPE`/`LEGO_CORNER` in `SceneMesher.kt` — no architecture change, just adding models + reusing the existing `(ord*4 + rotation)*6` indexing, unextended.
- `RAIL_Y_SPLIT_90`: mesh additionally depends on `state` (visual left/right switch) — `SceneMesher` must read byte 1 of state (§1.3) to pick the variant; the `(ord*4+rotation)*6` indexing formula grows a per-block-type model-state axis, defaulting to a single state for every other block type so existing mesh ids are unaffected.
- Vehicle: entity rendered like an NPC/player (bbmodel or primitive model), continuously interpolated between server updates — not a chunk mesh, rendered separately from the voxel world (like `AdminScenePreview`/current entities).

## 4. Summary of impacts / risks

| Area | Impact | Risk |
|---|---|---|
| `BlockState` (1→2 bytes) | Persistent chunk format + network protocol + mesher/vertex-packing plumbing | Medium — touches the whole block pipeline, but no migration needed (pre-publish project, old saves simply deleted) |
| `RailNetworkRegistry` (new) | Segment/loop detection, invalidation on place/break | Medium — new component, graph logic |
| `BlockInteractor` + `BlockInteract` message (new) | Generic right-click interaction mechanism | Low-medium — new protocol message + handler, but small/self-contained |
| `VehicleBehavior` + vehicle entity (new) | New network protocol (`VehicleSpawned`/`Update`/`Despawned`), server tick, client interpolation | High — new entity kind end-to-end |
| `ItemDefinition.spawnsEntity: EntityType?` (new) | Item → entity instead of item → block | Low-medium — localized extension, plus a new `EntityType` enum |
| Rail 3D models (bbmodel/gltf) | Assets to produce for each shape × rotation | Out of code scope, but non-trivial volume of work (6 types × up to 4 rotations) |

## 5. v1 decisions

1. **SPEED**: per-vehicle-item — a field on `VehicleDefinition`, not a global constant. Allows multiple vehicle types at different speeds without rework.
2. **Player riding**: no — v1 is purely automatic/decorative. The player places the vehicle, places/orients rails, toggles switches, but does not ride it. No camera/input control to handle. Extensible later (out of scope for v1).
3. **Vehicle-vehicle collision**: ignored in v1 — vehicles pass through each other, no proximity detection between vehicles each tick.
4. **`RAIL_Y_SPLIT_90`**: manual toggle only — the state only changes on player interaction (right-click via the generic `BlockInteract` mechanism, §1.5) or slash command, like a switch. No automatic alternation in v1.
5. **`RAIL_CURVE_45`**: dropped from scope — only `RAIL_CURVE_90` and `RAIL_CURVE_60` are implemented.
6. **Save migration**: none — the project is not yet published, so `BlockState`'s 1→2 byte format change is applied directly and existing local test-world saves are deleted rather than migrated.
7. **Vehicle placement entry point**: `/vehicule:add <name>` slash command only — no item-consumption-by-click path, to avoid inventory-management scope creep in v1.
