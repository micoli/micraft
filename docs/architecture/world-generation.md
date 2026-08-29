---
title: World generation pipeline
---

# World generation pipeline

Chunks are generated on demand: `ChunkStreamer` requests a chunk →
`WorldState.getOrGenerate()` checks the in-memory cache, then disk, then runs the
generator.

```mermaid
flowchart LR
    REQ["ChunkStreamer\n(player moves)"]
    REQ --> WS

    subgraph WS ["WorldState.getOrGenerate()"]
        direction TB
        CACHE{"in cache?"}
        DISK["WorldPersistence\n.mcc.gz"]
    end

    CACHE -->|yes| OUT
    CACHE -->|no - disk?| DISK
    DISK -->|yes| OUT
    DISK -->|no| GEN

    subgraph GEN ["ProceduralChunkGenerator"]
        direction TB
        BIO["VoronoiBiomeSelector\ndesert · plains · forest\nmountains · tundra\n(moisture × altitude grid)"]
        TRN["Terrain\nPerlin noise elevation\n+ mountain boost"]
        VEG["VegetationGenerator\noak tree · pine tree\nflower · weed\n(density hash per column)"]
        HSE["HouseGenerator\nrectangular · circular temple\n(Voronoi cell centres)"]
        RD["RoadVoronoi\nroads between cells"]
        BIO --> TRN
        TRN --> VEG
        TRN --> HSE
        TRN --> RD
    end

    GEN --> OUT["Chunk\n(ByteArray wire-encoded)"]
    OUT -->|ChunkData msg| CLIENT["Client"]
    OUT -->|mark dirty| SAVE["flushDirty → disk\n(every 600 ticks)"]
```

Tuning: [`biomes.yaml`](../world/biomes.md), [`houses.yaml` / `roads.yaml`](../world/structures.md),
[`vegetation.yaml`](../world/vegetation.md). Terrain constants (`WATER_LEVEL`,
`WORLD_MAX_Y`, view radius) are in [world constants](../reference/constants.md),
overridable via [`server.yaml`](../systems/server-config.md).
