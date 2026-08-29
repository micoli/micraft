---
title: Block catalog
---

# Block catalog

## Overview

Core block types: `AIR BEDROCK STONE DIRT GRASS SAND SANDSTONE GRAVEL SNOW
OAK_LOG OAK_LEAVES PINE_LOG PINE_LEAVES PINE_LEAVES_SNOW FLOWER WEED` plus rails,
planks, lego bricks and more. `Y ∈ [0, 1024]`, `CHUNK_SIZE = 16`.

For break/place behaviour and drop tables see
[Breaking & placing blocks](../gameplay/building-blocks.md).

## Configuration

- **Properties** — `resources/blocks/<name>/<name>.yaml`, overridable under
  `data/resources/blocks/<name>/<name>.yaml`. Schema `blocks.schema.json`.
- **Numeric wire ids** — `data/config/block_ids.yaml` (bundled default
  `resources/config/block_ids.yaml`). Schema `block_ids.schema.json`:

```yaml
blocks:
  "AIR": 0
  "BEDROCK": 3
  "STONE": ...
```

Adding a block: use `scripts/generate_new_blocks.mjs`, then `/reload` or
`/config:reload block`.

--8<-- "reference/_generated/blocks.md"
