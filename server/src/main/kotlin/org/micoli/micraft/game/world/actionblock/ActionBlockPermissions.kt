package org.micoli.micraft.game.world.actionblock

import org.micoli.micraft.auth.Permission
import org.micoli.micraft.auth.PermissionRegistry

/** Permission checked outside the owner/claim-editor bypass in [ActionBlockService.canEdit]. */
object ActionBlockPermissions {
    val EDIT = PermissionRegistry.register(Permission.of("actionblock", "edit"))
}
