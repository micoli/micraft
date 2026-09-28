package org.micoli.micraft.game.world.claim

import org.micoli.micraft.auth.Permission
import org.micoli.micraft.game.session.PlayerSession
import org.micoli.micraft.game.session.hasPermission
import org.micoli.micraft.game.world.BlockPos

/** What a Character is trying to act on; the Claim lookup happens from the position, if any. */
sealed interface EditTarget {
    data class ByOwner(val ownerName: String) : EditTarget

    data class InWorld(val pos: BlockPos) : EditTarget

    data class OwnedInWorld(val ownerName: String, val pos: BlockPos) : EditTarget
}

/**
 * Owns the composite authorization cascade: named permission → owner → Claim (owner, trusted,
 * faction ally, or [ClaimPermissions.BUILD]). Callers ask one question and never re-derive it.
 */
class Authorizer(private val claims: ClaimRegistry?) {

    fun isAllowed(session: PlayerSession, permission: Permission, target: EditTarget): Boolean {
        if (session.hasPermission(permission)) return true
        return when (target) {
            is EditTarget.ByOwner -> isOwner(session, target.ownerName)
            is EditTarget.InWorld -> claimAllowsBuild(session, target.pos)
            is EditTarget.OwnedInWorld ->
                isOwner(session, target.ownerName) || claimAllowsBuild(session, target.pos)
        }
    }

    /** Break / place / interact: unclaimed land is open to everyone. */
    fun canBuildAt(session: PlayerSession, pos: BlockPos): Boolean {
        val claim = claims?.claimAt(pos.x, pos.y, pos.z) ?: return true
        return canBuildIn(session, claim)
    }

    fun canBuildIn(session: PlayerSession, claim: Claim): Boolean =
        claim.ownerId == session.id ||
            session.id in claim.trustedPlayerIds ||
            session.hasPermission(ClaimPermissions.BUILD) ||
            claims?.factionAlly?.invoke(session.id, claim.ownerId) == true

    /** Abandon / trusted list: owner or [ClaimPermissions.ADMINISTER] only, never trust or ally. */
    fun canAdminister(session: PlayerSession, claim: Claim): Boolean =
        claim.ownerId == session.id || session.hasPermission(ClaimPermissions.ADMINISTER)

    private fun isOwner(session: PlayerSession, ownerName: String) = ownerName == session.state.name

    private fun claimAllowsBuild(session: PlayerSession, pos: BlockPos): Boolean {
        val claim = claims?.claimAt(pos.x, pos.y, pos.z) ?: return false
        return canBuildIn(session, claim)
    }
}
