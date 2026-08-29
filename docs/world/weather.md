---
title: Weather zones
---

# Weather zones

## How to play

Weather is spatial: `RAIN`, `STORM`, `SNOW` and `FOG` zones spawn dynamically per
biome, drift, and expire.

- **`/weather [rain|storm|snow|fog|none]`** *(admin)* — force a zone at your
  position, or clear all zones.
- **`/weather-forecast`** — list active zones and their distance.

`WeatherManager` ticks zone drift and expiry inside the game loop.

## Configuration

`data/config/weather.yaml` (bundled default `resources/config/weather.yaml`,
schema `weather.schema.json`):

```yaml
enabled: true
weatherTypes:
  - type: RAIN
    biomes: [plains, forest]
    enabled: true
    spawnRatePerBiomeTick: 0.0002
    minDurationTicks: 1200
    maxDurationTicks: 12000
    minRadius: 48.0
    maxRadius: 192.0
    driftSpeed: 0.1
```

Reload with `/reload`.

--8<-- "reference/_generated/weather.md"
