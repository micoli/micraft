package org.micoli.micraft.world.proceduralGenerator.road

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class RoadVoronoiZonesTest {

    private fun zones(config: RoadConfig = RoadConfig(), seed: Long = 5L) =
        RoadVoronoiZones(seed, config) { _, _ -> "plains" }

    @Test
    fun disabledConfig_isNeverOnRoad() {
        val z = zones(RoadConfig(enabled = false))
        for (wx in 0 until 500 step 37) {
            assertFalse(z.isOnRoadAt(wx, wx))
        }
    }

    @Test
    fun disabledConfig_neverBlocksVegetation() {
        val z = zones(RoadConfig(enabled = false))
        assertFalse(z.shouldBlockVegetation("plains", 10, 10))
    }

    @Test
    fun isOnRoad_isDeterministicForSameSeed() {
        val a = zones(seed = 3L)
        val b = zones(seed = 3L)
        for (wx in 0 until 400 step 23) {
            assertEquals(a.isOnRoadAt(wx, wx), b.isOnRoadAt(wx, wx))
        }
    }

    @Test
    fun someColumnsAreOnRoad_withFullProbability() {
        val z =
            zones(
                RoadConfig(
                    defaultRoad =
                        RoadBiomeConfig(
                            width = 5,
                            surface = org.micoli.micraft.world.BlockType.GRAVEL,
                            roadProbability = 1.0)))
        var found = false
        outer@ for (wx in 0 until 2000 step 5) {
            for (wz in 0 until 2000 step 5) {
                if (z.isOnRoadAt(wx, wz)) {
                    found = true
                    break@outer
                }
            }
        }
        assertTrue(found, "expected at least one road column with roadProbability=1.0")
    }

    @Test
    fun zeroRoadProbability_neverProducesRoad() {
        val z =
            zones(
                RoadConfig(
                    defaultRoad =
                        RoadBiomeConfig(
                            width = 5,
                            surface = org.micoli.micraft.world.BlockType.GRAVEL,
                            roadProbability = 0.0)))
        for (wx in 0 until 300 step 17) {
            for (wz in 0 until 300 step 17) {
                assertFalse(z.isOnRoadAt(wx, wz))
            }
        }
    }

    @Test
    fun shouldBlockVegetation_falseWhenAllowedOnRoadAndZeroDistance() {
        val z = zones(RoadConfig(vegetationAllowedOnRoad = true, minVegetationDistanceFromRoad = 0))
        assertFalse(z.shouldBlockVegetation("plains", 50, 50))
    }
}
