# update-schema

JSON Schemas in `server/src/main/resources/schemas/*.schema.json` are generated from the annotated Kotlin data
classes (`org.micoli.micraft.tools.GenerateJsonSchemasKt`).

After changing a config/data class:

1. `make gen-schemas`
2. Review the schema diff.
3. Commit it in the same commit as the data class change. `make check-schemas` (part of `code-standard` / CI) fails on drift.
