---
title: Configuration files
---

# Configuration files

Each file is optional. On first run the server writes a fully-commented template
into `data/config/`, merging any bundled default from `resources/config/`. Reload
most of them at runtime with `/reload` or `/config:reload`;
[`server.yaml`](../systems/server-config.md) needs a restart.

--8<-- "reference/_generated/config-files.md"
