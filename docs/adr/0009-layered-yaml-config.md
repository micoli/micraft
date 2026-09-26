---
status: accepted
---

# Layered YAML configuration: bundled defaults, data overrides

Game data and server configuration are YAML files. Shipped defaults live in `resources/` and move with the code.
Per-installation overrides live in the data root (`data/config/`, `data/resources/`, relocatable with
`MICRAFT_DATA_DIR`) and win over the defaults, file by file or entry by entry. Every file is validated by a JSON
Schema. Admins then only maintain what they changed, and a code upgrade can ship new defaults without clobbering
local tuning.

## Consequences

- Schemas are generated from the annotated data classes (`make gen-schemas`, checked by `make check-schemas`);
  regenerate them in the same commit as the data class change.
- Generated reference docs describe the bundled defaults, not a given installation.
