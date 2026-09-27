package org.micoli.micraft.game.npc.roster

import org.micoli.micraft.game.npc.AggroMode
import org.micoli.micraft.game.npc.NpcDefinition
import org.micoli.micraft.game.quest.QuestDefinition
import org.micoli.micraft.game.world.Region
import org.micoli.micraft.game.world.ZoneTier
import org.micoli.micraft.game.world.biome.BiomeDefinition

enum class CoverageGapReason {
    NO_SUITED_QUEST,
    NO_DRY_GROUND_FOR_A_QUEST_GIVER,
}

/** A (Biome, Danger tier) pair whose Regions never get a Quest giver. */
data class CoverageGap(
    val biome: String,
    val tier: ZoneTier,
    val reason: CoverageGapReason,
    val npcTypes: Set<String>,
)

/** Which Biome × Danger tier pairs no Quest can serve, whatever Roster their Regions draw. */
object QuestCoverage {
    fun gaps(
        biomes: Collection<BiomeDefinition>,
        npcTypes: Map<String, NpcDefinition>,
        quests: Collection<QuestDefinition>,
    ): List<CoverageGap> =
        biomes.flatMap { biome ->
            ZoneTier.entries.mapNotNull { tier -> gap(biome, tier, npcTypes, quests) }
        }

    private fun gap(
        biome: BiomeDefinition,
        tier: ZoneTier,
        npcTypes: Map<String, NpcDefinition>,
        quests: Collection<QuestDefinition>,
    ): CoverageGap? {
        // A Roster is drawn at one Danger level, so every level is its own pool.
        val eligibleByLevel =
            tier.npcLevelRange.map { level ->
                val region = Region(0, 0, biome, biome.id, level)
                npcTypes.values
                    .filter { RosterBuilder.isEligible(it, region) }
                    .associateBy { it.type }
            }
        val available = eligibleByLevel.flatMapTo(sortedSetOf()) { it.keys }
        if (biome.liquid) {
            return CoverageGap(
                biome.id, tier, CoverageGapReason.NO_DRY_GROUND_FOR_A_QUEST_GIVER, available)
        }
        val served =
            quests
                .filter { it.level in tier.npcLevelRange }
                .any { quest -> eligibleByLevel.any { pool -> fitsOneRoster(quest, pool) } }
        return if (served) null
        else CoverageGap(biome.id, tier, CoverageGapReason.NO_SUITED_QUEST, available)
    }

    /** Whether one Roster drawn from [pool] could hold every target of [quest] at once. */
    private fun fitsOneRoster(quest: QuestDefinition, pool: Map<String, NpcDefinition>): Boolean {
        val targets = quest.objectives.map { it.npcType }.toSet().map { pool[it] ?: return false }
        val (rares, commons) = targets.partition { RosterBuilder.isRare(it) }
        val hostiles = commons.count { it.aggroMode == AggroMode.AGGRESSIVE }
        return rares.size <= 1 &&
            hostiles <= RosterBuilder.HOSTILE_COUNT.last &&
            commons.size - hostiles <= RosterBuilder.PASSIVE_COUNT.last
    }
}
