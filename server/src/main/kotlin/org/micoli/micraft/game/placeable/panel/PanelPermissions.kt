package org.micoli.micraft.game.placeable.panel

import org.micoli.micraft.auth.Permission
import org.micoli.micraft.auth.PermissionRegistry

/** Checked outside the owner/claim-editor bypass in [PanelManager.canEdit]. */
object PanelPermissions {
    val EDIT = PermissionRegistry.register(Permission.of("panel", "edit"))
}
