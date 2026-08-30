# MiCraft

**MiCraft** is a multiplayer RPG voxel game built with **Kotlin Multiplatform**.
It runs a Ktor WebSocket server with authoritative physics and serves a browser
client via Kotlin/Wasm + BabylonJS. Procedural terrain generation, a biome
system, liquid physics, NPC entities, weather zones, an RPG layer, and a
plugin-based slash command system.

## 📖 Documentation

Full documentation — how to play and how to configure every system —
**<https://micoli.github.io/micraft>**.

Locally: `make docs-site-serve` → <http://localhost:8000>.

## URLs (running server)

- [Main game **/**](http://127.0.0.1:8080/)
- [Map **/map**](http://127.0.0.1:8080/map)
- [Administration **/admin/**](http://127.0.0.1:8080/admin/)
- [API doc **/api/docs**](http://127.0.0.1:8080/api/docs)

## Development

All build/run/test/lint commands execute **inside the dev container**.

```bash
make dev-up                   # build image + start container (daemons auto-start)
make shell                    # bash inside the container
make dc CMD="<any command>"   # run one command inside the container
make dev-restart-server       # rebuild + restart Ktor after a server-side change
make build-wasm               # one-shot WASM recompile after a Kotlin/Wasm change
make code-standard            # full lint (run before a PR)
make test                     # all test suites
```

`make help` lists every target. See the
[Getting started](https://micoli.github.io/micraft/getting-started/) guide.

## Slash Commands

<!-- BEGIN_COMMANDS -->

### Core commands

| Command | Usage | Description | Options / Autocomplete |
|---------|-------|-------------|------------------------|
| `/auction` | `/auction` | Opens the auction house. | — |
| `/buff` | `/buff <hp\|mana\|hpregen\|manaregen>` | Apply a temporary buff to yourself. | hp, mana, hpregen, manaregen |
| `/character` | `/character` | Show your RPG character sheet | — |
| `/claim` | `/claim <trust\|untrust\|abandon\|info> [playerName]` | Manage the land claim you're standing in. | dynamic |
| `/codex` | `/codex` | Opens the codex (blocks, items, bestiary). | — |
| `/config` | `/config <get\|set> <key> [value]` | Get or set a runtime config value. | dynamic |
| `/config:reload` | `/config:reload` | Reloads block, NPC, or RBAC definitions from resource files. | block, npc, rbac |
| `/craft` | `/craft` | Opens the crafting window. | — |
| `/createcharacter` | `/createcharacter <name> <class> <str> <dex> <intel> <wis> <con> <cha>` | Create your RPG character | — |
| `/createchat` | `/createchat <channelName>` | Create a new chat channel. | — |
| `/disconnect` | `/disconnect` | Déconnecte le joueur courant. | — |
| `/docraft` | `/docraft <recipeId> [count]` | Crafts a recipe. | — |
| `/drink` | `/drink <itemType>` | Consomme un item consommable de l'inventaire. | — |
| `/equip` | `/equip <armorName>` | Equip an armor piece. | dynamic |
| `/explode` | `/explode <radius>` | Destroy all blocks in a sphere around the player. | — |
| `/faction` | `/faction list\|join <id>\|leave\|info` | View and change your faction affiliation. | list, join, leave, info |
| `/give` | `/give <name> [N]` | Give an item, or grant an armor/weapon/tool, to yourself. | dynamic |
| `/give:money` | `/give:money <amount> [playerName]` | Give copper to a player (or yourself if name omitted). | — |
| `/god:off` | `/god:off` | Disable god mode. | — |
| `/god:on` | `/god:on` | Enable god mode (immune to damage). | — |
| `/group` | `/group create\|invite <player>\|accept\|leave\|kick <player>\|transfer <player>\|disband\|who` | Manage your temporary party (max 5). | create, invite, accept, leave, kick, transfer, disband, who |
| `/guild` | `/guild create <name> <tag>\|invite <player>\|accept\|leave\|kick <player>\|motd <text>\|rank <player> <rankName>\|transfer <player>\|disband\|info` | Manage your guild. | create, invite, accept, decline, leave, kick, motd, rank, transfer, disband, info |
| `/help` | `/help [command]` | Lists available commands. | — |
| `/join` | `/join <channelName>` | Join a chat channel. | dynamic |
| `/lang` | `/lang [locale]` | Changes your language preference. | — |
| `/layout` | `/layout <name>` | Switches to a named layout. | — |
| `/layouts` | `/layouts` | Opens the layout editor. | — |
| `/learnrecipe` | `/learnrecipe <recipeId>` | Teach a recipe to the player. | — |
| `/leave` | `/leave <channelName>` | Leave a chat channel. | dynamic |
| `/light:off` | `/light:off` | Restores natural cavern darkness. | — |
| `/light:on` | `/light:on` | Boosts ambient light underground (cavern lighting override). | — |
| `/mail` | `/mail` | Open your mailbox. | — |
| `/map` | `/map` | Toggles the biome map overlay. | — |
| `/mode` | `/mode <game\|creative>` | Switch between normal game mode and creative edit mode. (admin) | game, creative |
| `/mount` | `/mount` | Mount or dismount the vehicle you're targeting. | — |
| `/npcbuy` | `/npcbuy <npcId> <itemType> [quantity]` | Buy an item from a seller NPC. | — |
| `/npcsell` | `/npcsell <npcId> <itemType> [quantity]` | Sell an item to a seller NPC. | — |
| `/preferences` | `/preferences` | Opens the preferences panel. | — |
| `/pump` | `/pump` | Remove all connected liquid blocks in sight. | — |
| `/quest` | `/quest [list\|accept\|abandon\|status] [id]` | Manage your quests. | dynamic |
| `/refetch` | `/refetch` | Reloads all chunks around the player. | — |
| `/reload` | `/reload` | Reloads configuration files without restarting the server. | resources/blocks/*.yaml — block properties + drop tables, biomes.yaml — biome definitions, i18n/*.yaml — translations |
| `/rest` | `/rest` | Take a short rest: restore rage and tokens to maximum. | — |
| `/resurect` | `/resurect [playerName]` | Resurrect a downed player (self if no name given). | dynamic |
| `/save` | `/save` | Saves the world and player state to disk. | — |
| `/scene:place` | `/scene:place <sceneId> <rotation:0-3> <x> <y> <z>` | Stamp a scene into the live world at the given position. | — |
| `/set` | `/set <hp\|mana> <playerName> <value>` | Set a player stat. | dynamic |
| `/shaders` | `/shaders [on\|off]` | Toggles visual shaders (ambient occlusion, directional shading, fog). | on, off |
| `/siege_weapon` | `/siege_weapon <rotation\|pitch\|power> <value>` | Set the targeted siege weapon's rotation, pitch, or power. | dynamic |
| `/skin` | `/skin <skinName>` | Changes your player skin. | dynamic |
| `/skiprpg` | `/skiprpg` | Opt out of RPG system | — |
| `/spawn` | `/spawn <npc_model> [x y z]` | Spawn an NPC of the given model on the solid block you are looking at. (admin) | dynamic |
| `/talk` | `/talk <playerName>` | Open a private chat with a player. | dynamic |
| `/time` | `/time [0-23]` | Shows or sets the in-game time. | 0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15, 16, 17, 18, 19, 20, 21, 22, 23 |
| `/trade` | `/trade <playerName>` | Initiates a trade with another player. | dynamic |
| `/tradeaccept` | `/tradeaccept <tradeId>` | Accepts the current trade offer. | — |
| `/tradecancel` | `/tradecancel <tradeId>` | Cancels the current trade. | — |
| `/tradeoffer` | `/tradeoffer <tradeId> <json>` | Updates your current trade offer. | — |
| `/undo` | `/undo [N]` | Undo the last N block breaks, restoring blocks and reversing item collection. | — |
| `/unequip` | `/unequip <armorName>` | Remove an equipped armor piece. | dynamic |
| `/unwield` | `/unwield <hand>` | Empty a hand slot. | dynamic |
| `/vehicule:add` | `/vehicule:add <vehiculeName>` | Spawn a vehicle on the rail block you're standing on. | — |
| `/water` | `/water [x y z]` | Place a water source on the solid block you are looking at (or x y z). (admin) | — |
| `/weather` | `/weather [rain\|storm\|snow\|fog\|none]` | Force a weather zone at your position or clear all zones. (admin) | rain, storm, snow, fog, none |
| `/weather-forecast` | `/weather-forecast` | Shows active weather zones and their location. | — |
| `/wield` | `/wield <name> [hand]` | Wield a weapon or tool in a hand. | dynamic |

### Plugin commands

| Command | Usage | Description | Options / Autocomplete |
|---------|-------|-------------|------------------------|
| `/adduser` | `/adduser <email> <password> [displayName] [group1,group2,...]` | Add a local auth user. Usage: /adduser <email> <password> [displayName] [group1,group2,...] | — |
| `/goto` | `/goto <playerName\|npcName>` | Teleports you to a player or NPC. | dynamic |
| `/kick` | `/kick <playerName>` | Kicks a connected player. | dynamic |
| `/npc` | `/npc <spawn\|list\|remove\|tp> [args]` | Manage NPCs in the world. | — |
| `/rbac:listgroups` | `/rbac:listgroups` | List all groups and their permissions. | — |
| `/rbac:removegroup` | `/rbac:removegroup <email> <group1,group2,...>` | Remove groups from a user. | — |
| `/rbac:setgroup` | `/rbac:setgroup <email> <group1,group2,...>` | Add groups to a user. | — |
| `/summon` | `/summon <playerName>` | Teleports another player to your location. | dynamic |
| `/teleport` | `/teleport <x> <y> <z>  \|  /teleport <playerName>` | Teleports you to the given coordinates. | dynamic |
| `/who` | `/who` | Lists connected players with their position. | — |
| `/yield` | `/yield <message>` | Broadcasts a message to all connected players. | — |

<!-- END_COMMANDS -->

Regenerate: `make docs`.

## API Routes

Full machine-readable spec: [`server/openapi/openapi.yaml`](server/openapi/openapi.yaml),
browsable at `/api/docs` (Redoc) when the server is running. This table and the
YAML spec are both generated from the same `@ktoropenapi`-annotated Ktor routes —
never hand-edit either.

<!-- BEGIN_API_ROUTES -->

| Method | Path | Description |
|--------|------|-------------|
| GET | `/api/admin/auctions` | List all auction listings, any status |
| POST | `/api/admin/auctions/{id}/force-cancel` | Force-cancels a listing: returns the item to the seller and refunds the highest bidder, no tax |
| GET | `/api/admin/blocks` | All registered block definitions |
| GET | `/api/admin/chunks/discovered` | All chunk coordinates generated so far (in-memory ∪ persisted) |
| GET | `/api/admin/claims` | All land claims |
| DELETE | `/api/admin/claims/{id}` | Delete a land claim |
| GET | `/api/admin/claims/{id}` | A single land claim |
| PUT | `/api/admin/claims/{id}/bounds` | Update a claim's Y bounds |
| PUT | `/api/admin/claims/{id}/trust` | Grant or revoke a player's trust on a claim (online or offline) |
| GET | `/api/admin/classes` | RPG class definitions, keyed by class name |
| GET | `/api/admin/configs` | Names of all whitelisted editable YAML config files |
| GET | `/api/admin/configs/{...}` | Raw YAML content of a whitelisted config file |
| PUT | `/api/admin/configs/{...}` | Overwrite a whitelisted config file's raw YAML content |
| PUT | `/api/admin/gametime` | Set the in-game time of day |
| GET | `/api/admin/instances` | All instance zones |
| POST | `/api/admin/instances` | Create an instance zone covering already-generated chunks |
| DELETE | `/api/admin/instances/{id}` | Delete an instance zone |
| GET | `/api/admin/instances/{id}` | A single instance zone |
| PUT | `/api/admin/instances/{id}` | Rename an instance zone |
| GET | `/api/admin/instances/{id}/blocks` | Non-air blocks in an instance zone, streamed as newline-delimited JSON (application/x-ndjson, one InstanceBlockDto per line), capped at 300000 |
| PUT | `/api/admin/instances/{id}/bounds` | Update an instance zone's Y bounds |
| PUT | `/api/admin/instances/{id}/chunks` | Update the set of chunks covered by an instance zone |
| PUT | `/api/admin/instances/{id}/enabled` | Enable/disable an instance zone |
| PUT | `/api/admin/instances/{id}/layout` | Update an instance zone's clip planes and shortcut bar layout |
| GET | `/api/admin/items` | Item definitions, keyed by item type id |
| GET | `/api/admin/npc-types` | NPC type definitions (codex info), keyed by type id |
| GET | `/api/admin/npcs` | Live NPC instances with full animal/combat state |
| GET | `/api/admin/plain-colors` | All registered plain paint colors |
| GET | `/api/admin/players` | All player names |
| GET | `/api/admin/players/{name}` | Full player file (state, keybindings, RPG data) |
| PUT | `/api/admin/players/{name}/equipment` | Partially update a player's owned/equipped armor and wielded hand items |
| POST | `/api/admin/players/{name}/give` | Give an inventory item, or grant ownership of an armor/weapon/tool |
| PUT | `/api/admin/players/{name}/keybindings` | Overwrite a player's saved key bindings |
| PUT | `/api/admin/players/{name}/preferences` | Partially update a player's preferences (only given fields change) |
| POST | `/api/admin/players/{name}/rename` | Rename a player |
| PUT | `/api/admin/players/{name}/rpg` | Partially update a player's RPG class/base stats |
| POST | `/api/admin/reload` | Reload configuration files without restarting the server — same behavior as the in-game /reload command |
| POST | `/api/admin/restart` | Trigger a pitchfork server restart |
| GET | `/api/admin/scenes` | All scenes (bounded off-world block-structure buffers) |
| POST | `/api/admin/scenes` | Create a scene |
| DELETE | `/api/admin/scenes/{id}` | Delete a scene |
| GET | `/api/admin/scenes/{id}` | A single scene's metadata |
| PUT | `/api/admin/scenes/{id}` | Rename a scene |
| GET | `/api/admin/scenes/{id}/blocks/raw` | Scene block/state/extraState buffers as a binary blob: 3×4-byte big-endian dimensions (width,height,depth) followed by the blocks byte array, then the states byte array, then the extraStates byte array (wire-index-per-byte, 0 = AIR) |
| PUT | `/api/admin/scenes/{id}/dimensions` | Resize a scene |
| POST | `/api/admin/scenes/{id}/duplicate` | Duplicate a scene (copies name, dimensions, and blocks) |
| GET | `/api/admin/scenes/{id}/entities` | Fractional (lego/plate/arch) block entities placed in this scene — not carried by the blocks/raw binary blob, so the client loads them separately on scene open |
| PUT | `/api/admin/scenes/{id}/layout` | Update a scene's shortcut bar layout |
| GET | `/api/admin/schemas/{filename}` | A JSON Schema file (data/config/schemas/*.schema.json) for the config editor |
| GET | `/api/admin/simulation/defaults` | Defaults the world simulator admin UI prefills its editors with |
| GET | `/api/admin/skills` | All attack and spell ids |
| GET | `/api/admin/status` | Server status snapshot (TPS, players, chunks, heap, CPU) |
| GET | `/api/admin/users` | All local/no-auth accounts |
| POST | `/api/admin/users` | Create a local/no-auth user account |
| DELETE | `/api/admin/users/{email}` | Delete a user account |
| PUT | `/api/admin/users/{email}` | Update a local user's display name/groups |
| GET | `/api/admin/worlds` | All worlds on disk, with stats |
| POST | `/api/admin/worlds` | Create a new world |
| GET | `/api/admin/ws/instances/{id}` |  |
| GET | `/api/admin/ws/npcs` |  |
| GET | `/api/admin/ws/scenes/{id}` |  |
| GET | `/api/admin/ws/simulation` |  |
| GET | `/api/armors` | List all armor definitions |
| GET | `/api/assets/manifest` |  |
| POST | `/api/assets/reload` |  |
| GET | `/api/attacks` | Attack definitions, flattened by "attackId:level" key |
| GET | `/api/auth/config` | Active auth provider, used by the client to pick the right login UI |
| GET | `/api/autocomplete/{commandId}/{argIndex}` | Autocomplete suggestions for a slash command argument |
| GET | `/api/biomes` | Grass color per biome id, as [r, g, b] in 0..1 |
| POST | `/api/character/create` | Create a new (non-RPG) character |
| POST | `/api/character/rpgcreate` | Create a new RPG character (point-buy base stats + class) |
| GET | `/api/chunks/{cx}/{cz}` | Binary-encoded chunk data (protocol.ServerMessage.ChunkData wire format). Not a JSON API — used by the game client, not by TanStack Query hooks. |
| GET | `/api/classes` | Attack ids accessible per RPG class, keyed by level |
| GET | `/api/game-assets` | 3D game asset files discovered under resources/game-assets |
| GET | `/api/game-assets/bbmodel-export/{...}` | Converts an OBJ/MTL mesh into a Blockbench-compatible mesh .bbmodel (cached). The generated mesh elements are not rendered by the admin viewer — open the result in Blockbench to edit it. |
| DELETE | `/api/game-assets/blend-cache/{...}` | Clears the cached Blender conversion (scene tree + all OBJ/bbmodel exports) for a .blend file |
| GET | `/api/game-assets/blend-preview/{...}` | Converts a .blend file to OBJ/MTL via headless Blender (cached) and returns the OBJ asset path |
| GET | `/api/game-assets/blend-scene/{...}` | Reads a .blend file's collection/object tree via headless Blender (cached) |
| GET | `/api/game-assets/file/{...}` | Raw asset file bytes (glb/gltf/fbx/textures) |
| GET | `/api/i18n/{locale}` | Client-facing translation keys for a locale |
| GET | `/api/items/meta` | Item metadata (label, background color, consumable flags) by item type id |
| GET | `/api/keybindings` | Key bindings — a player's saved bindings if ?player= is given and persistence is available, otherwise the default config |
| GET | `/api/layout/registry` | All widgets registered for the UI layout editor |
| GET | `/api/macros/context` | Variables available to the macro JEXL evaluation context |
| GET | `/api/map/houses` | Generated houses within an area |
| GET | `/api/map/road-raster` | Per-chunk road bitmask within an area |
| GET | `/api/map/road-raster.png` | Rasterized road overlay as a PNG image |
| GET | `/api/map/roads` | Road vertex segments within an area |
| GET | `/api/map/staircases` | Named staircase points for the map overlay |
| GET | `/api/map/state` | Live players, NPCs and weather zones for the map overlay |
| GET | `/api/map/terrain` | Cached terrain summary JSON for the map overlay |
| GET | `/api/map/terrain-raster.png` | Rasterized terrain overlay as a PNG image |
| GET | `/api/map/voronoi` | Voronoi biome cells around a point |
| GET | `/api/map/voronoi-borders` | Voronoi cell border segments within an area |
| GET | `/api/player/{id}/armors` | Armor names currently equipped by a player |
| GET | `/api/player/{id}/hands` | Wielded weapon/tool names and dominant hand for a player |
| GET | `/api/player/{id}/owned` | Armor/weapon/tool names owned by a player |
| GET | `/api/player/{id}/rpg` | A player's RPG character class |
| POST | `/api/player/{id}/screenshots` | Upload a player screenshot (base64 PNG, optionally as a data: URI) |
| GET | `/api/player/{id}/skin` | A player's current skin |
| PUT | `/api/player/{id}/skin` | Change a player's skin |
| GET | `/api/players/by-email/{email}` | Player characters (name + id) linked to an account email |
| GET | `/api/players/names` | Names of all known players |
| GET | `/api/quests` | All quest definitions |
| GET | `/api/server/info` | Server build timestamp |
| GET | `/api/siege-weapons` | List all siege weapon definitions |
| GET | `/api/skins` | Names of all available player skins |
| GET | `/api/skins/{name}/config` | Skin config (eye offset, hidden bones) for a named skin |
| GET | `/api/spells` | Spell definitions, keyed by spell id |
| GET | `/api/tools` | List all tool definitions |
| GET | `/api/vehicles/{name}/config` | Vehicle model config (speed, seat offset) for a named vehicle |
| GET | `/api/weapons` | List all weapon definitions |
| POST | `/auth/noauth-login` | Create/reuse an account by email when auth is disabled (auth.provider=none) |

<!-- END_API_ROUTES -->

Regenerate: `make dc CMD="./gradlew :server:exportOpenApi"`.

## Contributing

See [Contributing](https://micoli.github.io/micraft/contributing/). In short:
Conventional Commits, `make code-standard` before a PR, a test for every
`server/src/main/` change, i18n strings in both `en.yaml` and `fr.yaml`, JSON
Schema updated in the same commit as the data class.

## Credits

- **Fantasy name generation** — NPC names are generated using syllable data and
  logic from [FyefoxxM/fantasy-name-generator](https://github.com/FyefoxxM/fantasy-name-generator),
  inspired by [Day 7: Fantasy Name Generator](https://jdookeran.medium.com/day-7-fantasy-name-generator-c2b4458b13f7)
  by J. Dookeran.
