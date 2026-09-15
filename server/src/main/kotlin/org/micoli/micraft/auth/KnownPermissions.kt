package org.micoli.micraft.auth

/**
 * Permission strings checked outside the command system — `PluginCommand.permission` values are
 * discovered dynamically from the live command registry instead (see
 * `GameLoop.knownCommandPermissions` / `GET /api/admin/permissions`), since those aren't. Keep in
 * sync with `hasPermission(...)` call sites: `game/tick/IntentCollector.kt`,
 * `game/world/actionblock/ActionBlockService.kt`, `command/commands/ActionBlockEditCommand.kt`,
 * `command/commands/ActionBlockDeleteCommand.kt`.
 */
val KNOWN_STANDALONE_PERMISSIONS =
    setOf(
        "action.fly",
        "action.break",
        "action.place",
        "actionblock:edit",
    )
