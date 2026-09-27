package org.micoli.micraft.game.npc.roster

import kotlin.random.Random
import org.micoli.micraft.game.npc.AggroMode
import org.micoli.micraft.game.npc.NpcDefinition
import org.micoli.micraft.game.world.Region

data class RosterEntry(val type: String, val weight: Int, val share: Int, val rare: Boolean)

/** The NPC types that populate one Region and their share of its budget. See CONTEXT.md. */
data class Roster(val region: Region, val budget: Int, val entries: List<RosterEntry>) {
    val types: Set<String>
        get() = entries.mapTo(mutableSetOf()) { it.type }
}

/**
 * Derives a Region's Roster from the World seed, the Region and the NPC types. Never persisted: the
 * same inputs always give the same Roster (ADR-0010).
 */
object RosterBuilder {
    val PASSIVE_COUNT = 2..4
    val HOSTILE_COUNT = 1..3
    private const val RARE_MAX_TOTAL = 5
    private const val RARE_CHANCE = 0.2
    private const val RARE_MAX_SHARE = 2

    fun build(worldSeed: Long, region: Region, npcTypes: Map<String, NpcDefinition>): Roster {
        val random = Random(mix(worldSeed, region.seedX, region.seedZ))
        val eligible = npcTypes.values.filter { isEligible(it, region) }.sortedBy { it.type }
        val (rares, commons) = eligible.partition { isRare(it) }
        val (hostiles, passives) = commons.partition { it.aggroMode == AggroMode.AGGRESSIVE }

        val picked = draw(passives, PASSIVE_COUNT, random) + draw(hostiles, HOSTILE_COUNT, random)
        val rare = rares.filter { random.nextDouble() < RARE_CHANCE }.randomOrNull(random)

        val budget = region.biome.regionBudget
        val rareShare = rare?.let { rareShare(it, picked, budget) } ?: 0
        val entries =
            share(picked, budget - rareShare) +
                listOfNotNull(rare?.let { RosterEntry(it.type, weightOf(it), rareShare, true) })
        return Roster(region, budget, entries)
    }

    private fun rareShare(rare: NpcDefinition, commons: List<NpcDefinition>, budget: Int): Int {
        val totalWeight = commons.sumOf { weightOf(it) } + weightOf(rare)
        if (totalWeight == 0) return 0
        val proportional = budget * weightOf(rare) / totalWeight
        return proportional.coerceIn(1, RARE_MAX_SHARE).coerceAtMost(budget)
    }

    private fun weightOf(def: NpcDefinition): Int = def.spawn.weight.coerceAtLeast(0)

    fun isEligible(def: NpcDefinition, region: Region): Boolean =
        def.spawn.autoSpawn &&
            (def.spawn.spawnBiomes.isEmpty() || region.biome.id in def.spawn.spawnBiomes) &&
            region.dangerLevel in def.minLevel..def.maxLevel &&
            def.isAquatic == region.biome.liquid

    fun isRare(def: NpcDefinition): Boolean = def.spawn.maxTotal in 1..RARE_MAX_TOTAL

    private fun draw(
        pool: List<NpcDefinition>,
        count: IntRange,
        random: Random
    ): List<NpcDefinition> {
        val wanted = random.nextInt(count.first, count.last + 1)
        return pool.shuffled(random).take(wanted)
    }

    /** Splits [budget] by weight, handing the rounding remainder to the largest fractions. */
    private fun share(types: List<NpcDefinition>, budget: Int): List<RosterEntry> {
        val totalWeight = types.sumOf { weightOf(it) }
        if (totalWeight <= 0 || budget <= 0) {
            return types.map { RosterEntry(it.type, weightOf(it), 0, false) }
        }
        val exact = types.map { budget.toDouble() * weightOf(it) / totalWeight }
        val shares = exact.map { it.toInt() }.toMutableList()
        val remainder = budget - shares.sum()
        exact.indices
            .sortedByDescending { exact[it] - shares[it] }
            .take(remainder)
            .forEach { shares[it]++ }
        return types.mapIndexed { i, def -> RosterEntry(def.type, weightOf(def), shares[i], false) }
    }

    private fun mix(worldSeed: Long, seedX: Int, seedZ: Int): Long {
        var h =
            worldSeed xor
                (seedX.toLong() * -7046029254386353131L) xor
                (seedZ.toLong() * 0x6C62272E07BB0142L)
        h = h xor (h ushr 33)
        h *= -49064778989728563L
        return h xor (h ushr 33)
    }
}
