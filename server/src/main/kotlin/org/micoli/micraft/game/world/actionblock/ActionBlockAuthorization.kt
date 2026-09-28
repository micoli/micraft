package org.micoli.micraft.game.world.actionblock

import org.micoli.micraft.game.session.PlayerSession
import org.micoli.micraft.game.world.BlockPos
import org.micoli.micraft.game.world.claim.Authorizer
import org.micoli.micraft.game.world.claim.EditTarget

/**
 * The one rule for editing an Action block, shared by the editor flow and `/actionblock edit`:
 * [ActionBlockPermissions.EDIT], its owner, or Claim build rights — and unclaimed land is open.
 */
fun Authorizer.canEditActionBlock(
    session: PlayerSession,
    pos: BlockPos,
    ownerName: String?,
): Boolean {
    val target =
        if (ownerName == null) EditTarget.InWorld(pos) else EditTarget.OwnedInWorld(ownerName, pos)
    return isAllowed(session, ActionBlockPermissions.EDIT, target) || canBuildAt(session, pos)
}
