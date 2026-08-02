package org.micoli.micraft.game.world.vegetation

import kotlinx.serialization.Serializable

@Serializable
data class VegetationConfigData(
    val enabled: Boolean = true,
    val growthCheckIntervalTicks: Int = 40,
    val chains: List<GrowthChain> = emptyList(),
    /**
     * Regrowth of grazed plants. Herbivores only eat FLOWER and WEED; without these rules a pasture
     * is eaten once and never comes back.
     */
    val regrowth: List<RegrowthRule> = DEFAULT_REGROWTH,
)

private val DEFAULT_REGROWTH =
    listOf(
        // Halved: herbivores now walk to their food and starve without it, so a meadow that comes
        // back slowly is a herbivore population that collapses rather than one that is regulated.
        RegrowthRule(grazed = "WEED", regrows = "WEED", minTicks = 300, maxTicks = 1_200),
        RegrowthRule(grazed = "FLOWER", regrows = "FLOWER", minTicks = 600, maxTicks = 2_400),
    )
