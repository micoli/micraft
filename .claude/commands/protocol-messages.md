# protocol-messages

All WebSocket messages between client and server.

## Client → Server (`ClientMessage`)

- `Connect(playerName, userName, preferredLanguage, token)` — join; `token` required when server auth enabled
- `MoveIntent(dx, dz, yaw, pitch, stance, jump, dy, flyToggle, speedUp, speedDown)`
- `ChunkUnload(positions)` — client unloaded these chunks
- `BlockBreakStart(pos)` / `BlockBreakStop`
- `Command(text)` — slash command
- `Disconnect(reason)`

## Server → Client (`ServerMessage`)

- `Welcome(playerId, playerName, spawnPos)`
- `ChunkData(pos, topY, wireBlocks: ByteArray)`
- `PlayerUpdate(state: PlayerState)`
- `WorldUpdate(changes: List<BlockChange>)`
- `PlayerLeft(playerId)`
- `BlockBreakProgress(pos, progress, hardness)`
- `Notification(message)`
- `ItemsSpawned(items: List<WorldItem>)`
- `ItemDespawned(id)`
- `InventoryUpdate(inventory: Map<ItemType, Int>)`
- `RegistrySync(blocks: List<BlockInfo>, items: Map<String, ItemInfo>)` — sent on connect and `/reload`; client uses for AO, minimap colors
