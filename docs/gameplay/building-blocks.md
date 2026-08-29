---
title: Breaking & placing blocks
---

# Breaking & placing blocks

## How to play

- **Break** — hold the break key while targeting a block. Break time scales with
  the block's `hardness` (`-1` = unbreakable, e.g. `BEDROCK`). `BlockBreaker`
  tracks progress and rolls the drop table on completion.
- **Place** — a buildable item places its `placesBlock`. See
  [Inventory & items](inventory-items.md).
- **`/undo [N]`** — reverts the last `N` block breaks, restoring blocks and
  reversing item collection.
- **`/mode <game|creative>`** *(admin)* — creative mode edits the world without
  drops or break time.
- **`/explode <radius>`** *(admin)* — destroys every block in a sphere.

A `WorldUpdate` re-meshes only the affected chunk.

## Configuration

Block properties live in `resources/blocks/<name>/<name>.yaml`, overridable
per-block under `data/resources/blocks/<name>/<name>.yaml`:

```yaml
hardness: 5            # -1 = unbreakable
solid: true
transparent: false
minimapColor: [136, 136, 136]
liquid: false
replaceable: false
vegetationHost: false
treeAllowed: true
drops:                 # omit entirely if the block drops nothing
  - item: COBBLESTONE
    dropRate: 100      # percent
    minCount: 1
    maxCount: 1
```

Schema: `blocks.schema.json`. Numeric block ids are assigned in
[`block_ids.yaml`](../world/blocks-catalog.md). Reload with `/reload` or
`/config:reload block`.

--8<-- "reference/_generated/blocks.md"
