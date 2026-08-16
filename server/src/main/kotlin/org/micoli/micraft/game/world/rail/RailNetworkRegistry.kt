package org.micoli.micraft.game.world.rail

import java.util.concurrent.ConcurrentHashMap
import org.micoli.micraft.game.world.BlockPos
import org.micoli.micraft.game.world.WorldState

/**
 * Server-side cache of rail-network topology (segment vs loop), computed on demand from placed rail
 * blocks and invalidated whenever a rail block is placed, broken or has its switch toggled. Mirrors
 * the [org.micoli.micraft.game.world.instance.InstanceRegistry] pattern: a plain
 * constructor-injected singleton, not a `WorldState`-owned concern.
 */
class RailNetworkRegistry(private val world: WorldState) {
    private val cache = ConcurrentHashMap<BlockPos, RailTopology>()

    /** Topology of the rail network component [pos] belongs to, or null if [pos] isn't a rail. */
    fun topologyAt(pos: BlockPos): RailTopology? {
        cache[pos]?.let {
            return it
        }
        if (!RailConnection.isRail(world.getBlock(pos.x, pos.y, pos.z))) return null
        val topology = rebuild(pos)
        for (p in topology.positions) cache[p] = topology
        return topology
    }

    /**
     * Drops cached topology for [pos], its 4 grid neighbors, and — since a cached [RailTopology] is
     * shared by every position it covers — every other position that was part of the same (now
     * possibly split/merged) cached component at [pos] or a neighbor. Call after any change (place,
     * break, switch toggle) that could affect what [pos] or its neighbors connect to. The next
     * [topologyAt] query for any position touched recomputes it.
     */
    fun invalidate(pos: BlockPos) {
        dropComponent(pos)
        for (dir in Direction.entries) {
            dropComponent(BlockPos(pos.x + dir.dx, pos.y, pos.z + dir.dz))
        }
    }

    private fun dropComponent(pos: BlockPos) {
        val cached = cache.remove(pos) ?: return
        for (p in cached.positions) cache.remove(p)
    }

    private fun activeConnectionsAt(pos: BlockPos): Set<Direction> {
        val type = world.getBlock(pos.x, pos.y, pos.z)
        val state = world.getBlockState(pos.x, pos.y, pos.z)
        val extraState = world.getExtraState(pos.x, pos.y, pos.z)
        return RailConnection.active(type, state, extraState)
    }

    // NOTE: neighbor lookup assumes same-Y adjacency — vertical connections for RAIL_SLOPE_* are
    // resolved by vehicle traversal geometry (Phase 6), not by this graph.
    private fun neighborConnectsBack(from: BlockPos, dir: Direction): Boolean {
        val n = BlockPos(from.x + dir.dx, from.y, from.z + dir.dz)
        if (!RailConnection.isRail(world.getBlock(n.x, n.y, n.z))) return false
        return dir.opposite in activeConnectionsAt(n)
    }

    /**
     * Walks the connected component through [pos] along active connections in both directions,
     * classifying it as [RailTopology.Segment] (two dead ends) or [RailTopology.Loop] (one walk
     * direction leads back to [pos]). [RailConnection.active] always resolves a placement to at
     * most 2 directions (a switch's inactive branch is pruned), so the whole network is a simple
     * chain or cycle — no branching traversal needed.
     */
    private fun rebuild(pos: BlockPos): RailTopology {
        // Walks outward from `pos` starting in direction `first`, until a dead end (returns the
        // visited positions and false) or the walk lands back on `pos` (returns true — a loop).
        fun walk(first: Direction): Pair<List<BlockPos>, Boolean> {
            val path = mutableListOf<BlockPos>()
            var current = pos
            var next = first
            while (true) {
                if (!neighborConnectsBack(current, next)) return path to false
                val nPos = BlockPos(current.x + next.dx, current.y, current.z + next.dz)
                if (nPos == pos) return path to true
                path.add(nPos)
                val forward = (activeConnectionsAt(nPos) - next.opposite).firstOrNull()
                if (forward == null) return path to false
                current = nPos
                next = forward
            }
        }

        val here = activeConnectionsAt(pos).toList()
        if (here.isEmpty()) return RailTopology.Segment(listOf(pos))

        val (forwardPath, looped) = walk(here[0])
        if (looped) return RailTopology.Loop(listOf(pos) + forwardPath)

        val backwardPath = if (here.size > 1) walk(here[1]).first.asReversed() else emptyList()
        return RailTopology.Segment(backwardPath + pos + forwardPath)
    }
}
