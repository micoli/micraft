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
    // A position normally belongs to exactly one component (one list entry) — a crossing (declared
    // groups sharing no direction, see RailConnection) belongs to one independent component per
    // group at once, so this maps to a list rather than a single RailTopology.
    private val cache = ConcurrentHashMap<BlockPos, List<RailTopology>>()

    /**
     * Topology component(s) [pos] belongs to — 0 if not a rail, 1 normally, 1 per group for a
     * crossing.
     */
    fun topologyAt(pos: BlockPos): List<RailTopology> {
        cache[pos]?.let {
            return it
        }
        if (!RailConnection.isRail(world.getBlock(pos.x, pos.y, pos.z))) return emptyList()
        val topologies = rebuild(pos)
        for (topology in topologies) {
            for (p in topology.positions) {
                cache.compute(p) { _, existing -> (existing ?: emptyList()) + topology }
            }
        }
        return cache[pos] ?: emptyList()
    }

    /**
     * Drops cached topology for [pos], its 4 grid neighbors, and — since a cached [RailTopology] is
     * shared by every position it covers — every other position that was part of any of the same
     * (now possibly split/merged) cached component(s) at [pos] or a neighbor. Call after any change
     * (place, break, switch toggle) that could affect what [pos] or its neighbors connect to. The
     * next [topologyAt] query for any position touched recomputes it.
     */
    fun invalidate(pos: BlockPos) {
        dropComponent(pos)
        for (dir in Direction.entries) {
            dropComponent(BlockPos(pos.x + dir.dx, pos.y, pos.z + dir.dz))
        }
    }

    private fun dropComponent(pos: BlockPos) {
        val cached = cache.remove(pos) ?: return
        for (topology in cached) {
            for (p in topology.positions) cache.remove(p)
        }
    }

    private fun activeGroupsAt(pos: BlockPos): List<List<RailConnectionPoint>> {
        val type = world.getBlock(pos.x, pos.y, pos.z)
        val state = world.getBlockState(pos.x, pos.y, pos.z)
        val extraState = world.getExtraState(pos.x, pos.y, pos.z)
        return RailConnection.activeGroups(type, state, extraState)
    }

    /**
     * The independent through-pair(s) [pos] starts from — one per [RailConnection.activeGroups]
     * group (a crossing declares several disjoint groups, all active at once; anything else has
     * exactly one). A second element of `null` means a dead end on that side.
     */
    private fun startingPairs(pos: BlockPos): List<Pair<Direction, Direction?>> {
        val type = world.getBlock(pos.x, pos.y, pos.z)
        val state = world.getBlockState(pos.x, pos.y, pos.z)
        val extraState = world.getExtraState(pos.x, pos.y, pos.z)
        return RailConnection.activeGroups(type, state, extraState).mapNotNull { group ->
            val dirs = group.map { it.direction }
            dirs.getOrNull(0)?.let { first -> first to dirs.getOrNull(1) }
        }
    }

    /**
     * Walks each of [pos]'s independent through-pairs ([startingPairs]) along active connections in
     * both directions, classifying each as [RailTopology.Segment] (two dead ends) or
     * [RailTopology.Loop] (one walk direction leads back to [pos]). Mid-walk, a crossing is passed
     * straight through ([RailConnection.preferredContinuation]) rather than branching, so — aside
     * from [pos] itself possibly starting two independent pairs — each walk is still a simple chain
     * or cycle, no branching traversal needed.
     */
    private fun rebuild(pos: BlockPos): List<RailTopology> {
        // Walks outward from `pos` starting in direction `first`, until a dead end (returns the
        // visited positions and false) or the walk lands back on `pos` (returns true — a loop).
        fun walk(first: Direction): Pair<List<BlockPos>, Boolean> {
            val path = mutableListOf<BlockPos>()
            var current = pos
            var next = first
            while (true) {
                val nPos =
                    RailTraversal.connectingNeighbor(world, current, next) ?: return path to false
                if (nPos == pos) return path to true
                path.add(nPos)
                val forward =
                    RailConnection.preferredContinuation(activeGroupsAt(nPos), next.opposite)
                if (forward == null) return path to false
                current = nPos
                next = forward
            }
        }

        val pairs = startingPairs(pos)
        if (pairs.isEmpty()) return listOf(RailTopology.Segment(listOf(pos)))

        return pairs.map { (first, second) ->
            val (forwardPath, looped) = walk(first)
            if (looped) {
                RailTopology.Loop(listOf(pos) + forwardPath)
            } else {
                val backwardPath = second?.let { walk(it).first.asReversed() } ?: emptyList()
                RailTopology.Segment(backwardPath + pos + forwardPath)
            }
        }
    }
}
