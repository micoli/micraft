# data-directory

Layout of the `data/` directory.

```
data/
  config/
    blocks.yaml             # block properties (hardness, solid, minimapColor, modelElement)
    items.yaml              # item properties (buildable, placesBlock)
    drops.yaml              # block → item drop table
    biomes.yaml             # biome definitions
    server.yaml             # server config (auth provider, world, physics)
    game.yaml               # gameplay constants (tick rate, gravity, etc.)
    npc.yaml                # NPC behavior constants
    npcs.yaml               # NPC definitions
    roads.yaml              # road generation config
    houses.yaml             # house generation config
    weather.yaml            # weather config
    spawns.json             # NPC spawn state (runtime)
    auth/users.yaml         # local auth users (email, passwordHash, displayName)
    personal/keybindings.yaml
    i18n/en.yaml
    i18n/fr.yaml
    schemas/                # JSON Schemas for YAML config files (VS Code validation)
  world/default_world/
    world.json              # world metadata
    players/Player.json     # persisted player states
    chunks/*.mcc.gz         # binary chunk files (DO NOT READ)
```
