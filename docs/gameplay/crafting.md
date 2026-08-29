---
title: Crafting
---

# Crafting

## How to play

- **`/craft`** — open the crafting window.
- **`/learnrecipe <recipeId>`** — teach a recipe to the player (recipes must be
  unlocked before use).
- **`/docraft <recipeId> [count]`** — craft directly without the window.

`RecipeRegistry` is loaded from YAML at startup. Recipe unlocking is per-player
state.

## Configuration

`data/config/recipes.yaml` (bundled default `resources/config/recipes.yaml`):

```yaml
COBBLESTONE_BRICK:
  giveType: block          # block | item
  giveId: COBBLESTONE
  giveAmount: 4
  items: ["COBBLESTONE*2", "GRAVEL*1"]   # TYPE*count inputs
```

Schema: `recipes.schema.json`. Reload with `/reload`.

--8<-- "reference/_generated/recipes.md"
