# key-source-files

Key source files by layer.

## Core (shared)

| File | Purpose |
|------|---------|
| `core/.../protocol/ClientMessage.kt` | All client→server messages (sealed class) |
| `core/.../protocol/ServerMessage.kt` | All server→client messages (sealed class) |
| `core/.../world/Block.kt` | `BlockType` enum + hardness + `BlockPos` |
| `core/.../world/WorldConstants.kt` | `WorldConstants`, `PlayerConstants` |
| `core/.../player/Player.kt` | `Vec3`, `Orientation`, `PlayerState` |
| `core/.../world/ItemType.kt` | `ItemType` enum |
| `core/.../world/WorldItem.kt` | `WorldItem(id, pos, type, count)` |
| `core/.../world/BlockRegistry.kt` | Singleton holding `BlockDefinition` per `BlockType` |
| `core/.../world/ItemRegistry.kt` | Singleton holding `ItemDefinition` per `ItemType` |

## Server

| File | Purpose |
|------|---------|
| `server/.../GameLoop.kt` | Tick driver, coordinates all tick processors |
| `server/.../session/PlayerSession.kt` | Per-player WebSocket session |
| `server/.../tick/BlockBreaker.kt` | Block-break progress + drop spawning |
| `server/.../world/DropConfig.kt` | YAML-driven drop table loader |
| `server/.../world/BlockRegistryLoader.kt` | Loads `data/config/blocks.yaml` → `BlockRegistry` |
| `server/.../world/ItemRegistryLoader.kt` | Loads `data/config/items.yaml` → `ItemRegistry` |
| `server/.../world/WorldItemManager.kt` | Tracks live world items |
| `server/.../auth/AuthProvider.kt` | `AuthProvider` interface + `AuthResult(playerId, displayName, token)` |
| `server/.../auth/TokenStore.kt` | UUID→AuthResult in-memory store, TTL 10 min |
| `server/.../auth/LocalAuthProvider.kt` | bcrypt login; re-reads `data/config/auth/users.yaml` on every call |
| `server/.../auth/OAuthProvider.kt` | Google Authorization Code flow |
| `server/.../auth/AuthRoutes.kt` | HTTP auth routes |
| `server/.../auth/AddUserCommand.kt` | In-game `/adduser <email> <password> [displayName]` |
| `server/.../auth/AddUserCli.kt` | CLI entry point — run via `./gradlew :server:addUser` |

## Web client (Kotlin/Wasm + BabylonJS)

| File | Purpose |
|------|---------|
| `app/webApp/.../GameClient.kt` | Client-side prediction + server reconciliation |
| `app/webApp/.../babylon/BabylonBindingsScene.kt` | engine, scene, camera, lights |
| `app/webApp/.../babylon/BabylonBindingsWorld.kt` | meshes, materials, chunk geometry, block defs, fog/sky |
| `app/webApp/.../babylon/BabylonBindingsInput.kt` | keyboard, mouse, camera controls, target/break overlays, event queue |
| `app/webApp/.../babylon/BabylonBindingsUI.kt` | overlays, console, hotbar, HUD, layout, minimap, autocomplete |
| `app/webApp/.../babylon/BabylonBindingsModels.kt` | player model, NPC models, FP arms |
| `app/webApp/.../babylon/BabylonBindingsUtil.kt` | logging, URL/page utils, i18n, biome colors, block registry, debug camera |
| `app/webApp/.../resources/mc_bindings.js` | JS-side BabylonJS binding glue (generated — do not edit directly) |
