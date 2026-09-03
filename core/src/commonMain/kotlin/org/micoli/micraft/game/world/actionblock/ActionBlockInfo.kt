package org.micoli.micraft.game.world.actionblock

import kotlinx.serialization.Serializable
import org.micoli.micraft.game.world.BlockPos

/**
 * Wire form sent to clients: the star + Tab need [name]/[pos]; the HUD shows [variables]. The three
 * scripts stay server-side (only the edit form receives them, via [ActionBlockPayload]).
 */
@Serializable
data class ActionBlockInfo(
    val name: String,
    val pos: BlockPos,
    val variables: Map<String, String> = emptyMap(),
)

fun ActionBlock.toInfo(): ActionBlockInfo = ActionBlockInfo(name, pos, variables)
