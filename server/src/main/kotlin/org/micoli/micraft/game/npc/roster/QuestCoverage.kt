package org.micoli.micraft.game.npc.roster

import org.micoli.micraft.game.npc.NpcDefinition
import org.micoli.micraft.game.quest.QuestDefinition
import org.micoli.micraft.game.world.Region
import org.micoli.micraft.game.world.ZoneTier
import org.micoli.micraft.game.world.biome.BiomeDefinition

/** A (Biome, Danger tier) pair whose Regions never get a Quest giver. */
data class CoverageGap(
    val biome: String,
    val tier: Int,
    val reason: String,
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
        val levels = tier.npcLevelRange
        val available =
            levels
                .flatMap { level ->
                    val region = Region(0, 0, biome, biome.id, level)
                    npcTypes.values.filter { RosterBuilder.isEligible(it, region) }.map { it.type }
                }
                .toSortedSet()
        if (biome.liquid)
            return CoverageGap(biome.id, tier.tier, "no dry ground for a Quest giver", available)
        val served =
            quests.any { quest ->
                quest.level in levels && quest.objectives.all { it.npcType in available }
            }
        return if (served) null else CoverageGap(biome.id, tier.tier, "no suited Quest", available)
    }
}
