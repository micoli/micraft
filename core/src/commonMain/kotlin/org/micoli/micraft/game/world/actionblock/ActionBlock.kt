package org.micoli.micraft.game.world.actionblock

import kotlinx.serialization.Serializable
import org.micoli.micraft.game.world.BlockPos

/**
 * A block a player has named and attached scripted logic to. Persisted world-level (see
 * `ActionBlockRegistry` / `actionblocks.yaml`), keyed by [pos], with [name] unique across the map.
 *
 * The three scripts are JEXL macro bodies run by `ActionBlockScriptEngine`, with `player` and
 * `self` bound as variables. [variables] is a stringly-typed key/value store the scripts read and
 * write through `getBlock('name').get/set`.
 */
@Serializable
data class ActionBlock(
    val name: String,
    val pos: BlockPos,
    val owner: String,
    val onActivate: String = "",
    val onTargetEvent: String = "",
    val onRemoteEvent: String = "",
    val variables: Map<String, String> = emptyMap(),
)
