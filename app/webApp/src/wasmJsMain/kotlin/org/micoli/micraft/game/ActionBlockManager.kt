@file:OptIn(kotlin.js.ExperimentalWasmJsInterop::class)

package org.micoli.micraft.game

import kotlin.js.JsAny
import org.micoli.micraft.babylon.jsAddActionBlockIcon
import org.micoli.micraft.babylon.jsClearActionBlockHighlight
import org.micoli.micraft.babylon.jsClearActionBlockIcons
import org.micoli.micraft.babylon.jsRemoveActionBlockIcon
import org.micoli.micraft.babylon.jsSetActionBlockHighlight
import org.micoli.micraft.game.world.BlockPos
import org.micoli.micraft.game.world.actionblock.ActionBlockInfo

/**
 * Client mirror of the server action-block registry: draws the ★ icons and drives Tab targeting.
 */
class ActionBlockManager(private val scene: JsAny) {
    private val blocks = LinkedHashMap<String, ActionBlockInfo>()
    private var highlighted: BlockPos? = null

    private fun key(p: BlockPos) = "${p.x},${p.y},${p.z}"

    fun sync(list: List<ActionBlockInfo>) {
        jsClearActionBlockIcons()
        blocks.clear()
        list.forEach { upsert(it) }
    }

    fun upsert(info: ActionBlockInfo) {
        val k = key(info.pos)
        blocks[k] = info
        jsAddActionBlockIcon(
            scene, k, info.pos.x.toDouble(), info.pos.y.toDouble(), info.pos.z.toDouble())
    }

    fun remove(pos: BlockPos) {
        val k = key(pos)
        blocks.remove(k)
        jsRemoveActionBlockIcon(k)
        if (highlighted == pos) setHighlight(null)
    }

    fun clear() {
        jsClearActionBlockIcons()
        blocks.clear()
        setHighlight(null)
    }

    fun currentTarget(): BlockPos? = highlighted

    fun at(pos: BlockPos): ActionBlockInfo? = blocks[key(pos)]

    /** `{"name":..,"values":{..}}` for [pos] if it is an action block, else `"null"`. */
    fun hudJson(pos: BlockPos?): String {
        val info = pos?.let { blocks[key(it)] } ?: return "null"
        val vars =
            info.variables.entries.joinToString(",") { (k, v) ->
                "\"${jsonEscape(k)}\":\"${jsonEscape(v)}\""
            }
        return """{"name":"${jsonEscape(info.name)}","values":{$vars}}"""
    }

    private fun jsonEscape(s: String): String =
        s.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n")

    fun setHighlight(pos: BlockPos?) {
        highlighted = pos
        if (pos == null) jsClearActionBlockHighlight(scene)
        else jsSetActionBlockHighlight(scene, pos.x.toDouble(), pos.y.toDouble(), pos.z.toDouble())
    }

    /** Distance-sorted list of nearby action-block positions — for the unified Tab cycle. */
    fun targetCandidates(px: Double, py: Double, pz: Double): List<BlockPos> {
        val maxDist2 = 24.0 * 24.0
        return blocks.values
            .map { it.pos }
            .map { p ->
                val dx = p.x + 0.5 - px
                val dy = p.y + 0.5 - py
                val dz = p.z + 0.5 - pz
                p to dx * dx + dy * dy + dz * dz
            }
            .filter { it.second <= maxDist2 }
            .sortedBy { it.second }
            .map { it.first }
    }

    fun e2eJson(): String =
        "[" +
            blocks.values.joinToString(",") {
                """{"name":"${it.name}","x":${it.pos.x},"y":${it.pos.y},"z":${it.pos.z}}"""
            } +
            "]"
}
