# update-schema

Keep JSON Schemas in `data/config/schemas/` in sync when Kotlin data classes change.

## Modified file → schema to update

| Modified file | Schema |
|---|---|
| `core/.../world/BiomeDefinition.kt`, `Block.kt` | `biomes.schema.json` |
| `server/.../world/DropConfig.kt`, `core/.../world/ItemType.kt` | `drops.schema.json` |
| `server/.../world/BlockRegistryLoader.kt`, `core/.../world/BlockDefinition.kt`, `Block.kt` | `blocks.schema.json` |
| `server/.../world/ItemRegistryLoader.kt`, `core/.../world/ItemDefinition.kt`, `Block.kt` | `items.schema.json` |
| `server/.../world/KeyBindingsConfig.kt` | `keybindings.schema.json` |
| `server/.../world/I18nConfig.kt` | `i18n.schema.json` |
| `server/.../world/ServerConfigLoader.kt` (`AuthSection`, `OAuthConfig`, `LocalAuthConfig`) | `server.schema.json` |
| `server/.../auth/LocalAuthProvider.kt` (`UserEntry`, `UsersConfig`) | `auth-users.schema.json` |

Update schema in same commit as the data class change.
