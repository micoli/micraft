package org.micoli.micraft.game.world.claim

import org.micoli.micraft.I18nConfig
import org.micoli.micraft.game.session.PlayerSession
import org.micoli.micraft.game.world.BlockPos
import org.micoli.micraft.game.world.ChunkPos
import org.micoli.micraft.game.world.WorldConstants
import org.micoli.micraft.protocol.ServerMessage

/**
 * Player-facing land-ownership ("cadastre") operations: claiming a chunk-aligned region for a
 * copper cost, and granting/revoking build rights to other players on a claim you own. Read-only
 * lookups (`claimAt`, `canEdit`) are exposed directly on [ClaimRegistry] and consumed by
 * [org.micoli.micraft.game.world.block.BlockBreaker]/[org.micoli.micraft.game.world.block.BlockPlacer]/[org.micoli.micraft.game.world.block.BlockInteractor];
 * this class owns the mutating, wallet-charging half, mirroring
 * [org.micoli.micraft.game.auction.AuctionManager]'s shape.
 */
class ClaimManager(
    private val registry: ClaimRegistry,
    private val config: ClaimConfig,
    private val getSessions: () -> Collection<PlayerSession>,
    private val i18n: I18nConfig,
    private val savePlayer: (PlayerSession) -> Unit,
) {
    private fun chunksBetween(pos1: BlockPos, pos2: BlockPos): Set<ChunkPos> {
        val cx1 = Math.floorDiv(pos1.x, WorldConstants.CHUNK_SIZE)
        val cx2 = Math.floorDiv(pos2.x, WorldConstants.CHUNK_SIZE)
        val cz1 = Math.floorDiv(pos1.z, WorldConstants.CHUNK_SIZE)
        val cz2 = Math.floorDiv(pos2.z, WorldConstants.CHUNK_SIZE)
        val chunks = mutableSetOf<ChunkPos>()
        for (cx in minOf(cx1, cx2)..maxOf(cx1, cx2)) {
            for (cz in minOf(cz1, cz2)..maxOf(cz1, cz2)) {
                chunks.add(ChunkPos(cx, cz))
            }
        }
        return chunks
    }

    suspend fun sendSync(session: PlayerSession) {
        session.send(
            ServerMessage.ClaimSync(registry.forOwnerOrTrusted(session.id).map { it.toInfo() }))
    }

    suspend fun createClaim(session: PlayerSession, pos1: BlockPos, pos2: BlockPos) {
        val lang = session.state.language
        val chunks = chunksBetween(pos1, pos2)
        val yMin = minOf(pos1.y, pos2.y)
        val yMax = maxOf(pos1.y, pos2.y)
        if (chunks.isEmpty() || yMin > yMax) {
            session.send(ServerMessage.ClaimDenied(i18n.t(lang, "claim:server:invalid_area")))
            return
        }
        if (chunks.size > config.maxChunksPerClaim) {
            session.send(
                ServerMessage.ClaimDenied(
                    i18n.t(
                        lang,
                        "claim:server:area_too_large",
                        chunks.size,
                        config.maxChunksPerClaim)))
            return
        }
        val ownedCount = registry.all().count { it.ownerId == session.id }
        if (ownedCount >= config.maxClaimsPerPlayer) {
            session.send(
                ServerMessage.ClaimDenied(
                    i18n.t(lang, "claim:server:max_claims_reached", config.maxClaimsPerPlayer)))
            return
        }
        if (registry.overlaps(chunks, yMin, yMax)) {
            session.send(ServerMessage.ClaimDenied(i18n.t(lang, "claim:server:overlaps_existing")))
            return
        }
        val cost = chunks.size * config.costPerChunk
        if (session.state.wallet < cost) {
            session.send(
                ServerMessage.ClaimDenied(i18n.t(lang, "claim:server:insufficient_funds", cost)))
            return
        }

        session.state = session.state.copy(wallet = session.state.wallet - cost)
        savePlayer(session)
        session.send(ServerMessage.WalletUpdate(session.state.wallet))

        registry.create(chunks, yMin, yMax, session.id, session.state.name)
        session.send(ServerMessage.Notification(i18n.t(lang, "claim:server:claim_created", cost)))
        sendSync(session)
    }

    suspend fun abandonClaim(session: PlayerSession, claimId: String) {
        val lang = session.state.language
        val claim = registry.get(claimId)
        if (claim == null) {
            session.send(ServerMessage.ClaimDenied(i18n.t(lang, "claim:server:claim_not_found")))
            return
        }
        if (claim.ownerId != session.id && "*" !in session.permissions) {
            session.send(ServerMessage.ClaimDenied(i18n.t(lang, "claim:server:not_your_claim")))
            return
        }
        registry.delete(claimId)
        session.send(ServerMessage.Notification(i18n.t(lang, "claim:server:claim_abandoned")))
        sendSync(session)
        claim.trustedPlayerIds.forEach { trustedId ->
            getSessions().find { it.id == trustedId }?.let { sendSync(it) }
        }
    }

    suspend fun setTrusted(
        session: PlayerSession,
        claimId: String,
        playerName: String,
        trusted: Boolean,
    ) {
        val lang = session.state.language
        val claim = registry.get(claimId)
        if (claim == null) {
            session.send(ServerMessage.ClaimDenied(i18n.t(lang, "claim:server:claim_not_found")))
            return
        }
        if (claim.ownerId != session.id && "*" !in session.permissions) {
            session.send(ServerMessage.ClaimDenied(i18n.t(lang, "claim:server:not_your_claim")))
            return
        }
        val target =
            getSessions().find { it.state.name.equals(playerName, ignoreCase = true) }
                ?: run {
                    session.send(
                        ServerMessage.ClaimDenied(
                            i18n.t(lang, "claim:server:player_not_found", playerName)))
                    return
                }
        registry.setTrusted(claimId, target.id, target.state.name, trusted)
        val key = if (trusted) "claim:server:trust_granted" else "claim:server:trust_revoked"
        session.send(ServerMessage.Notification(i18n.t(lang, key, target.state.name)))
        sendSync(session)
        sendSync(target)
    }
}
