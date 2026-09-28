package org.micoli.micraft.game.world.claim

import org.micoli.micraft.auth.Permission
import org.micoli.micraft.auth.PermissionRegistry

/** Neither implies the other: `build` never lets a Character abandon a Claim, nor vice versa. */
object ClaimPermissions {
    /** Break / place / interact / edit Action blocks inside any Claim. */
    val BUILD = PermissionRegistry.register(Permission.of("claim", "build"))

    /** Abandon any Claim and edit its trusted list. */
    val ADMINISTER = PermissionRegistry.register(Permission.of("claim", "administer"))
}
