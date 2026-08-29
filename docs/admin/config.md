---
title: Config editor
---

# Config editor

A live YAML editor for the whitelisted config files, with **JSON Schema
validation** as you type. Saving writes the file and (for most files) a `/reload`
picks it up without a restart.

| Route | Purpose |
|-------|---------|
| `GET /api/admin/configs` | names of all editable config files |
| `GET /api/admin/configs/{...}` | raw YAML of one file |
| `PUT /api/admin/configs/{...}` | overwrite one file's YAML |
| `GET /api/admin/schemas/{filename}` | the JSON Schema for the editor |

The full list of files, their schemas and override paths is in
[Configuration files](../reference/config-files.md).

!!! note
    JSON Schemas live in `server/src/main/resources/schemas/` and are generated
    from annotated data classes (`make gen-schemas`, verified by
    `make check-schemas`). Never hand-edit them.
