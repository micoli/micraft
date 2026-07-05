# data-directory

Layout of the `data/` directory.

```
data/
  config/
    xxx.yaml
    schemas/                # JSON Schemas for YAML config files (VS Code validation)
  world/default_world/
    world.json              # world metadata
    players/Player.json     # persisted player states
    chunks/*.mcc.gz         # binary chunk files (DO NOT READ)
```
