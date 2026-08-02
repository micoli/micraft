package org.micoli.micraft.game.world.vegetation

import java.nio.file.Path
import kotlin.io.path.exists
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The regrowth rules the game actually ships.
 *
 * `data/config/` is gitignored, so the shipped values are the code defaults; the yaml is only a
 * local override. Asserting the defaults is therefore asserting what every install gets.
 *
 * `data/config/vegetation.yaml` was entirely commented out, so the code defaults were in force and
 * editing the file changed nothing. Now that herbivores walk to their food and starve without it,
 * how fast a meadow comes back is the difference between a regulated population and one that
 * collapses — so the shipped value is worth asserting rather than assuming.
 */
class ShippedVegetationConfigTest {

    private val configPath = Path.of("data/config/vegetation.yaml")

    @Test
    fun theShippedConfigParsesAndCarriesRegrowthRules() {
        if (!configPath.exists()) return
        val data = VegetationConfig(configPath).data

        val byBlock = data.regrowth.associateBy { it.grazed }
        assertTrue(
            byBlock.containsKey("WEED") && byBlock.containsKey("FLOWER"),
            "both grazing blocks must regrow, got ${byBlock.keys}")
        for ((block, rule) in byBlock) {
            assertTrue(rule.minTicks > 0, "$block has a non-positive minTicks")
            assertTrue(rule.maxTicks > rule.minTicks, "$block has an empty regrowth window")
            assertEquals(block, rule.regrows, "$block must regrow as itself, not as $rule.regrows")
        }
    }

    /**
     * Pinned rather than merely "positive": these are the numbers the ecology was balanced against,
     * and halving or doubling them moves every herbivore population.
     */
    @Test
    fun theDefaultsAreTheBalancedValues() {
        val defaults = VegetationConfigData().regrowth.associateBy { it.grazed }

        val weed = defaults["WEED"]
        assertTrue(weed != null, "WEED must regrow, got ${defaults.keys}")
        assertEquals(300, weed.minTicks)
        assertEquals(1_200, weed.maxTicks)

        val flower = defaults["FLOWER"]
        assertTrue(flower != null, "FLOWER must regrow")
        assertEquals(600, flower.minTicks)
        assertEquals(2_400, flower.maxTicks)
    }
}
