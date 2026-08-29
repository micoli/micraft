---
title: Game loop — tick sequence
---

# Game loop — tick sequence

The server runs at **20 tps (50 ms/tick)**. Each tick drives player input,
physics, automatic world managers, and periodic housekeeping.

```mermaid
flowchart TD
    GL(["GameLoop.tick()\n50 ms · 20 tps"])

    GL --> SESS

    subgraph SESS ["Per connected player"]
        direction LR
        IC["IntentCollector\nMoveIntent · BlockBreak\nBlockPlace · Command"]
        BRK["BlockBreaker\nbreak progress + drops"]
        MOV["MovementProcessor\nAABB physics · gravity\nclient-side prediction"]
        STR["ChunkStreamer\ndeliver ready chunks"]
        IC --> BRK --> MOV --> STR
    end

    SESS --> WIT["WorldItemManager\nitem proximity pickup"]
    WIT --> NPC["NpcManager\nwander · pathfind · interact"]
    NPC --> WEA["WeatherManager\nzone drift · expiry"]

    WEA --> LIQ
    LIQ --> VEG_M

    subgraph AUTO ["Automatic world managers — broadcast WorldUpdate on change"]
        direction TB
        LIQ["LiquidManager — every tick\ncheck activeLiquids, flow down, spread horizontally (max 7)"]
        VEG_M["VegetationManager — every 40 ticks\nverify block, accumulate ticks, SEED→SPROUT→SAPLING→tree"]
    end

    GL -->|"every 20 ticks"| T1["TimeUpdate broadcast"]
    GL -->|"every 200 ticks"| T2["NpcSpawner.trySpawn"]
    GL -->|"every 600 ticks"| T3["flushDirty chunks + player/NPC/vegetation state"]
```

Combat runs its own processors alongside this loop (`SpellProcessor`,
`StatusEffectProcessor`, `RegenProcessor`, `ExperienceProcessor`).
