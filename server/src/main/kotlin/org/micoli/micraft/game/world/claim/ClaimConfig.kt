package org.micoli.micraft.game.world.claim

import kotlinx.serialization.EncodeDefault
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.Serializable

@OptIn(ExperimentalSerializationApi::class)
@Serializable
data class ClaimConfig(
    @EncodeDefault(EncodeDefault.Mode.ALWAYS) val costPerChunk: Long = 50L,
    @EncodeDefault(EncodeDefault.Mode.ALWAYS) val maxChunksPerClaim: Int = 64,
    @EncodeDefault(EncodeDefault.Mode.ALWAYS) val maxClaimsPerPlayer: Int = 3,
)
