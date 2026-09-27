package org.micoli.micraft.game.npc.roster

import java.util.concurrent.ConcurrentHashMap
import org.micoli.micraft.game.npc.NpcDefinition
import org.micoli.micraft.game.npc.NpcInstance
import org.micoli.micraft.game.world.Region
import org.micoli.micraft.game.world.WorldState

/** Rosters and wild populations of the World's Regions (ADR-0010). */
class RegionPopulation(
    private val world: WorldState,
    private val definitions: () -> Map<String, NpcDefinition>,
) {
    private val rosters = ConcurrentHashMap<Long, Roster>()
    @Volatile private var rostersBuiltFrom: Map<String, NpcDefinition>? = null

    fun rosterOf(region: Region): Roster {
        val defs = definitions()
        if (defs !== rostersBuiltFrom) {
            rosters.clear()
            rostersBuiltFrom = defs
        }
        return rosters.getOrPut(region.key) { RosterBuilder.build(world.worldSeed, region, defs) }
    }

    fun census(npcs: Collection<NpcInstance>): RegionCensus {
        val census = RegionCensus(this)
        npcs.filter(::isWild).forEach { npc ->
            world.regionAt(npc.state.pos)?.let { census.record(it, npc.state.type) }
        }
        return census
    }

    /** Whether [npc] may live in [region]: wild NPCs must be of a type in its Roster. */
    fun fitsRoster(npc: NpcInstance, region: Region): Boolean =
        !isWild(npc) || npc.state.type in rosterOf(region).types

    /** Spawned or born into the World and owned by nobody: Pets and Quest givers stay out. */
    private fun isWild(npc: NpcInstance): Boolean {
        if (npc.isDead || npc.ownerId != null) return false
        return npc.definition.spawn.autoSpawn || npc.definition.animalConfig != null
    }
}

/** Wild NPCs counted per Region and NPC type, kept up to date as a pass spawns more. */
class RegionCensus(private val population: RegionPopulation) {
    private val perRegion = HashMap<Long, MutableMap<String, Int>>()

    fun total(region: Region): Int = perRegion[region.key]?.values?.sum() ?: 0

    fun count(region: Region, type: String): Int = perRegion[region.key]?.get(type) ?: 0

    /** A zero budget turns auto-spawn off without capping births. */
    fun isFull(region: Region): Boolean {
        val budget = population.rosterOf(region).budget
        return budget > 0 && total(region) >= budget
    }

    fun record(region: Region, type: String) {
        perRegion.getOrPut(region.key) { HashMap() }.merge(type, 1, Int::plus)
    }
}
