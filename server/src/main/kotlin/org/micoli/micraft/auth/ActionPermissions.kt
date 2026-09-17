package org.micoli.micraft.auth

/** Movement/interaction permissions checked in [org.micoli.micraft.game.tick.IntentCollector]. */
object ActionPermissions {
    val FLY = PermissionRegistry.register(Permission.of("action", "fly"))
    val BREAK = PermissionRegistry.register(Permission.of("action", "break"))
    val PLACE = PermissionRegistry.register(Permission.of("action", "place"))
}
