---
title: Automatic manager state machine
---

# Automatic manager state machine

Both `LiquidManager` and `VegetationManager` follow the same pattern: an
in-memory set of active positions is mutated by world events, consumed by the
tick, and produces `WorldUpdate` broadcasts to all clients.

```mermaid
stateDiagram-v2
    direction LR

    state "LiquidManager" as LIQ {
        [*] --> active : WATER placed / adjacent block removed
        active --> flowing : tick — can flow
        flowing --> active : spread continues
        flowing --> settled : no AIR neighbor (remove from set)
        settled --> [*]
    }

    state "VegetationManager" as VEG {
        [*] --> tracking : SEED placed on vegetationHost (tryActivate)
        tracking --> tracking : ticks < ticksRequired (accumulate every 40 ticks)
        tracking --> next_stage : ticks >= ticksRequired (SEED->SPROUT->SAPLING)
        next_stage --> tracking : stageIndex++ / new random ticksRequired
        next_stage --> spawning : last stage reached
        spawning --> [*] : oakTreeBlocks / pineTreeBlocks -> WorldUpdate batch
        tracking --> [*] : block missing (player broke it)
    }
```

See [Liquids](../world/liquids.md) and [Vegetation](../world/vegetation.md) for
the player-facing behaviour and the YAML that tunes each chain.
