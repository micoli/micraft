package org.micoli.micraft.game.world.actionblock

import java.util.concurrent.ConcurrentHashMap
import org.micoli.micraft.game.world.BlockPos
import org.micoli.micraft.game.world.BlockType
import org.micoli.micraft.game.world.WorldPersistence
import org.micoli.micraft.game.world.actionblock.ActionBlockConstants.DEFAULT_NAME_PREFIX

/**
 * World-level store of [ActionBlock]s, persisted as `actionblocks.yaml`. Mirrors `ClaimRegistry`:
 * an in-memory map keyed by position plus a name index enforcing map-wide name uniqueness and
 * powering `getBlock('name')` lookups from scripts.
 */
class ActionBlockRegistry(private val persistence: WorldPersistence?) {
    private val byPos = ConcurrentHashMap<BlockPos, ActionBlock>()
    private val byName = ConcurrentHashMap<String, BlockPos>()

    init {
        persistence?.loadActionBlocks()?.forEach { block ->
            byPos[block.pos] = block
            byName[block.name] = block.pos
        }
    }

    enum class UpsertResult {
        OK,
        NAME_TAKEN,
        NOT_FOUND,
        INVALID,
    }

    fun all(): List<ActionBlock> = byPos.values.sortedBy { it.name }

    fun at(pos: BlockPos): ActionBlock? = byPos[pos]

    fun byName(name: String): ActionBlock? = byName[name]?.let { byPos[it] }

    fun isActionBlock(pos: BlockPos): Boolean = byPos.containsKey(pos)

    /** Smallest free `actionblock-<n>` name. */
    fun generateName(): String {
        var n = 1
        while (byName.containsKey("$DEFAULT_NAME_PREFIX$n")) n++
        return "$DEFAULT_NAME_PREFIX$n"
    }

    /** Create a bare action block at [pos] with an auto name and empty scripts. */
    fun create(pos: BlockPos, owner: String, blockAt: (BlockPos) -> BlockType): ActionBlock? {
        if (byPos.containsKey(pos)) return byPos[pos]
        if (blockAt(pos) == BlockType.AIR) return null
        val block = ActionBlock(name = generateName(), pos = pos, owner = owner)
        byPos[pos] = block
        byName[block.name] = pos
        persist()
        return block
    }

    fun upsert(
        pos: BlockPos,
        name: String,
        onActivate: String,
        onTargetEvent: String,
        onRemoteEvent: String,
        variables: Map<String, String>,
    ): UpsertResult {
        val existing = byPos[pos] ?: return UpsertResult.NOT_FOUND
        val trimmed = name.trim()
        if (trimmed.isEmpty() || trimmed.length > ActionBlockConstants.MAX_NAME_LENGTH)
            return UpsertResult.INVALID
        val holder = byName[trimmed]
        if (holder != null && holder != pos) return UpsertResult.NAME_TAKEN
        if (variables.size > ActionBlockConstants.MAX_VARIABLES) return UpsertResult.INVALID

        byName.remove(existing.name)
        byName[trimmed] = pos
        byPos[pos] =
            existing.copy(
                name = trimmed,
                onActivate = onActivate.take(ActionBlockConstants.MAX_SCRIPT_LENGTH),
                onTargetEvent = onTargetEvent.take(ActionBlockConstants.MAX_SCRIPT_LENGTH),
                onRemoteEvent = onRemoteEvent.take(ActionBlockConstants.MAX_SCRIPT_LENGTH),
                variables = variables,
            )
        persist()
        return UpsertResult.OK
    }

    /** Set one variable on the named block. Returns false when the block is unknown. */
    fun setVariable(name: String, key: String, value: String): Boolean {
        val pos = byName[name] ?: return false
        val existing = byPos[pos] ?: return false
        byPos[pos] = existing.copy(variables = existing.variables + (key to value))
        persist()
        return true
    }

    fun removeAt(pos: BlockPos): ActionBlock? {
        val removed = byPos.remove(pos) ?: return null
        byName.remove(removed.name)
        persist()
        return removed
    }

    /** Drop entries whose position is no longer a solid block (bulk edits, explosions). */
    fun pruneAgainst(blockAt: (BlockPos) -> BlockType): List<BlockPos> {
        val stale = byPos.keys.filter { blockAt(it) == BlockType.AIR }
        stale.forEach { pos -> byPos.remove(pos)?.let { byName.remove(it.name) } }
        if (stale.isNotEmpty()) persist()
        return stale
    }

    private fun persist() {
        persistence?.saveActionBlocks(all())
    }
}
