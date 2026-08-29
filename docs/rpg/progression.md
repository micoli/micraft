---
title: Progression & XP
---

# Progression & XP

## Overview

`ExperienceProcessor` awards XP on kills and quest completion; crossing a
threshold raises the level (`XpBar` shows progress). Max level is
`RPG_LEVEL_MAX`. A party splits XP with a per-member bonus.

## Configuration

`data/config/experience.yaml` (bundled default `resources/config/experience.yaml`,
schema `experience.schema.json`):

```yaml
progression:
  thresholds: [300, 900, 2700, 6500, ...]   # cumulative XP per level up
sources:
  commonPerLevel: 50
  elitePerLevel: 200
  bossPerLevel: 1000
group:
  enabled: true
  bonusPerMember: 0.10
```

`*PerLevel` values are multiplied by the target's level. Reload with `/reload`.

--8<-- "reference/_generated/experience.md"
