# key-source-files

Key source files by layer.

## Core (shared)

| File | Purpose |
|------|---------|
| `core/.../protocol/ClientMessage.kt` | All client→server messages (sealed class) |
| `core/.../protocol/ServerMessage.kt` | All server→client messages (sealed class) |
| `core/.../protocol/ProtobufCodec.kt` | Protobuf encode/decode |
| `core/.../world/Block.kt` | `BlockType` value class + `BlockPos` |
| `core/.../world/BlockDefinition.kt` | Per-block properties (hardness, solid, model…) |
| `core/.../world/BlockRegistry.kt` | Singleton `BlockDefinition` per `BlockType` |
| `core/.../world/WorldConstants.kt` | `WorldConstants`, `PlayerConstants` |
| `core/.../world/Chunk.kt` | Chunk data structure |
| `core/.../world/WorldItem.kt` | `WorldItem(id, pos, type, count)` |
| `core/.../world/ItemDefinition.kt` | Per-item properties (buildable, placesBlock…) |
| `core/.../world/ItemRegistry.kt` | Singleton `ItemDefinition` per `ItemType` |
| `core/.../world/BiomeDefinition.kt` | Biome properties |
| `core/.../world/BiomeRegistry.kt` | Singleton biome definitions |
| `core/.../world/VegetationType.kt` | `VegetationType` enum |
| `core/.../player/Player.kt` | `Vec3`, `Orientation`, `PlayerState` |
| `core/.../player/PlayerStance.kt` | Standing/sneaking/crawling stance |
| `core/.../physics/AabbCollider.kt` | AABB collision (shared client+server) |
| `core/.../npc/Npc.kt` | `Npc` domain type |
| `core/.../npc/NpcConstants.kt` | NPC dimension/speed constants |
| `core/.../ui/GameLayout.kt` | `GameLayout` shared UI layout type |
| `core/.../ui/McUiState.kt` | `McUiState` shared UI state |
| `core/.../world/proceduralGenerator/ProceduralChunkGenerator.kt` | Main procedural world generator |
| `core/.../world/proceduralGenerator/chunkGenerator/ChunkGenerator.kt` | `ChunkGenerator` interface |
| `core/.../world/proceduralGenerator/VoronoiBiomeZones.kt` | Voronoi biome zone distribution |
| `core/.../world/proceduralGenerator/PerlinNoise.kt` | Perlin noise impl |

## Server

| File | Purpose |
|------|---------|
| `server/.../Application.kt` | Ktor app entry point, module wiring |
| `server/.../GameLoop.kt` | Tick driver, coordinates all tick processors |
| `server/.../Plugin.kt` | `Plugin` interface |
| `server/.../PluginCommand.kt` | `PluginCommand` base |
| `server/.../CommandHandler.kt` | Command dispatch, registration, autocomplete |
| `server/.../CommandContext.kt` | Per-command execution context |
| `server/.../ConfigRegistry.kt` | Central config registry |
| `server/.../GameConstants.kt` | Server-side game constants |
| `server/.../session/PlayerSession.kt` | Per-player WebSocket session |
| `server/.../session/WorldActionRecord.kt` | Undo-able world action record |
| `server/.../tick/BlockBreaker.kt` | Block-break progress + drop spawning |
| `server/.../tick/BlockPlacer.kt` | Block placement logic |
| `server/.../tick/ChunkStreamer.kt` | Chunk send/stream to clients |
| `server/.../tick/MovementProcessor.kt` | Server-side movement validation |
| `server/.../tick/LiquidManager.kt` | Water/liquid propagation |
| `server/.../tick/VegetationManager.kt` | Vegetation growth |
| `server/.../tick/IntentCollector.kt` | Collects player intents per tick |
| `server/.../world/WorldState.kt` | Live world state (chunks, players, items) |
| `server/.../world/WorldPersistence.kt` | Chunk save/load |
| `server/.../world/WorldMetadata.kt` | World metadata (seed, name…) |
| `server/.../world/WorldItemManager.kt` | Tracks live world items |
| `server/.../world/DropConfig.kt` | YAML-driven drop table loader |
| `server/.../world/BlockRegistryLoader.kt` | Loads `data/config/blocks.yaml` → `BlockRegistry` |
| `server/.../world/ItemRegistryLoader.kt` | Loads `data/config/items.yaml` → `ItemRegistry` |
| `server/.../world/ArmorRegistryLoader.kt` | `WearableSlots` + loads `resources/armors/` → armor registry |
| `server/.../world/NpcRegistryLoader.kt` | Loads `data/config/npcs.yaml` → NPC definitions |
| `server/.../world/HouseConfigLoader.kt` | Loads house/structure config |
| `server/.../world/RoadConfigLoader.kt` | Loads road config |
| `server/.../world/VegetationConfig.kt` | Vegetation spawn config |
| `server/.../world/GameConfigLoader.kt` | Loads `data/config/game.yaml` |
| `server/.../world/ServerConfigLoader.kt` | Loads `data/config/server.yaml` |
| `server/.../world/WeatherManager.kt` | Weather state + transitions |
| `server/.../world/WeatherConfig.kt` | Weather config |
| `server/.../world/ChatService.kt` | Chat message routing |
| `server/.../world/ChatChannelManager.kt` | Chat channel management |
| `server/.../world/I18nConfig.kt` | i18n YAML loader + `t()` |
| `server/.../world/KeyBindingsConfig.kt` | Per-player keybinding persistence |
| `server/.../world/YamlSchemaValidator.kt` | JSON schema validation for YAML configs |
| `server/.../npc/NpcManager.kt` | NPC tick, movement, interaction |
| `server/.../npc/NpcBehavior.kt` | `NpcBehavior` interface |
| `server/.../npc/NpcBehaviorRegistry.kt` | Registered NPC behaviors |
| `server/.../npc/NpcInstance.kt` | Live NPC instance |
| `server/.../npc/NpcDefinition.kt` | NPC definition (type, model, behavior) |
| `server/.../npc/NpcSpawner.kt` | NPC spawning logic |
| `server/.../npc/NpcPhysics.kt` | NPC AABB physics |
| `server/.../npc/NpcConfigLoader.kt` | Loads NPC config YAML |
| `server/.../auth/AuthProvider.kt` | `AuthProvider` interface + `AuthResult` |
| `server/.../auth/TokenStore.kt` | UUID→AuthResult in-memory store, TTL 10 min |
| `server/.../auth/LocalAuthProvider.kt` | bcrypt login; re-reads `users.yaml` on every call |
| `server/.../auth/OAuthProvider.kt` | Google Authorization Code flow |
| `server/.../auth/AuthRoutes.kt` | HTTP auth routes |
| `server/.../auth/AddUserCommand.kt` | In-game `/adduser <email> <password> [displayName]` |
| `server/.../auth/AddUserCli.kt` | CLI entry point — run via `./gradlew :server:addUser` |
| `server/.../http/MapRoutes.kt` | HTTP map/minimap routes |
| `server/.../http/MetricsRoutes.kt` | HTTP metrics routes |
| `server/.../http/TerrainCache.kt` | Terrain tile cache |

## Web client (Kotlin/Wasm + BabylonJS)

| File | Purpose |
|------|---------|
| `app/webApp/.../main.kt` | Wasm entry point, auth token parsing |
| `app/webApp/.../GameClient.kt` | Client-side prediction + server reconciliation |
| `app/webApp/.../ChunkManager.kt` | Client chunk storage + dirty tracking |
| `app/webApp/.../LocalPlayerController.kt` | Local player input → `MoveIntent` |
| `app/webApp/.../RemotePlayerManager.kt` | Other players' state + interpolation |
| `app/webApp/.../NpcManager.kt` | Client-side NPC state |
| `app/webApp/.../NetworkStats.kt` | RTT / packet stats |
| `app/webApp/.../WebUiBridge.kt` | Kotlin→React UI bridge (calls JS functions) |
| `app/webApp/.../babylon/BabylonBindingsScene.kt` | Engine, scene, camera, lights |
| `app/webApp/.../babylon/BabylonBindingsWorld.kt` | Meshes, materials, chunk geometry, block defs, fog/sky |
| `app/webApp/.../babylon/BabylonBindingsInput.kt` | Keyboard, mouse, camera controls, target/break overlays |
| `app/webApp/.../babylon/BabylonBindingsUI.kt` | Overlays, console, hotbar, HUD, layout, minimap, autocomplete |
| `app/webApp/.../babylon/BabylonBindingsModels.kt` | Player model, NPC models, FP arms |
| `app/webApp/.../babylon/BabylonBindingsWeather.kt` | Weather visual effects |
| `app/webApp/.../babylon/BabylonBindingsUtil.kt` | Logging, URL/page utils, i18n, biome colors, block registry, debug camera |
| `app/webApp/.../resources/mc_bindings.js` | JS-side BabylonJS binding glue (generated — do not edit directly) |

## Web UI (TypeScript / React)

| File | Purpose |
|------|---------|
| `app/webApp/ts-src/ui/game/Character.tsx` | Character screen — armor equip/unequip, skin preview (key `Y`) |
| `app/webApp/ts-src/ui/shared/PlayerModelPreview.tsx` | Shared BabylonJS player model canvas (skin + armor, walking toggle) |
