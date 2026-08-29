package org.micoli.micraft.game.world.claim

import kotlinx.serialization.EncodeDefault
import kotlinx.serialization.EncodeDefault.Mode.ALWAYS
import kotlinx.serialization.Serializable
import org.micoli.micraft.game.session.PlayerSession
import org.micoli.micraft.game.world.ChunkPos
import org.micoli.micraft.game.world.WorldConstants
import org.micoli.micraft.protocol.ClaimInfo

@Serializable
data class Claim(
    val id: String,
    val chunks: Set<ChunkPos>,
    val yMin: Int,
    val yMax: Int,
    val ownerId: String,
    val ownerName: String,
    val createdAt: Long,
    // Default Json (encodeDefaults=false) drops fields equal to their default — without this,
    // an empty trusted set (the common case) would be omitted from the admin claims JSON, and
    // the frontend would see `undefined` instead of `[]`.
    @EncodeDefault(ALWAYS) val trustedPlayerIds: Set<String> = emptySet(),
    @EncodeDefault(ALWAYS) val trustedPlayerNames: Set<String> = emptySet(),
) {
    fun contains(x: Int, y: Int, z: Int): Boolean =
        y in yMin..yMax &&
            ChunkPos(
                Math.floorDiv(x, WorldConstants.CHUNK_SIZE),
                Math.floorDiv(z, WorldConstants.CHUNK_SIZE)) in chunks

    /**
     * Owner, an explicitly trusted player, or a full admin ("*" permission) may break/place here.
     */
    fun canEdit(session: PlayerSession): Boolean =
        ownerId == session.id || session.id in trustedPlayerIds || "*" in session.permissions
}

fun Claim.toInfo(): ClaimInfo =
    ClaimInfo(
        id = id,
        chunks = chunks.toList(),
        yMin = yMin,
        yMax = yMax,
        ownerId = ownerId,
        ownerName = ownerName,
        trustedPlayerNames = trustedPlayerNames.toList())
