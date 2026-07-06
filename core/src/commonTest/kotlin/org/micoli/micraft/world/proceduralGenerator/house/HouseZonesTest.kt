package org.micoli.micraft.world.proceduralGenerator.house

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class HouseZonesTest {

    private val houseType =
        HouseTypeConfig(
            id = "hut",
            widthMin = 5,
            widthMax = 7,
            depthMin = 5,
            depthMax = 7,
            floorsMin = 1,
            floorsMax = 1,
            roofTypes = listOf("flat"),
            roomsMin = 1,
            roomsMax = 1,
            doorsMin = 1,
            doorsMax = 1,
        )

    private fun config(houseProbability: Double = 1.0, enabled: Boolean = true) =
        HouseConfig(
            enabled = enabled,
            houseTypes = listOf(houseType),
            defaultBiome =
                HouseBiomeConfig(
                    houseProbability = houseProbability, typeRates = mapOf("hut" to 1.0)))

    private fun zones(cfg: HouseConfig = config(), seed: Long = 1L) =
        HouseZones(seed, cfg, biomeAt = { _, _ -> "plains" }, surfaceY = { _, _ -> 64 })

    @Test
    fun disabledConfig_hasNoHouses() {
        val z = zones(config(enabled = false))
        assertFalse(z.hasHouseAt(0, 0))
        assertTrue(z.housesInArea(-100, -100, 100, 100).isEmpty())
    }

    @Test
    fun zeroProbability_producesNoHouses() {
        val z = zones(config(houseProbability = 0.0))
        for (cx in -5..5) for (cz in -5..5) assertFalse(z.hasHouseAt(cx, cz))
    }

    @Test
    fun noAllowedTypes_producesNoHouses() {
        val cfg = config().copy(defaultBiome = HouseBiomeConfig(houseProbability = 1.0))
        val z = zones(cfg)
        assertFalse(z.hasHouseAt(0, 0))
    }

    @Test
    fun fullProbability_placesHousesInArea() {
        val z = zones(config(houseProbability = 1.0))
        val houses = z.housesInArea(0, 0, 200, 200)
        assertTrue(houses.isNotEmpty())
    }

    @Test
    fun buildHouse_sizeWithinConfiguredBounds() {
        val z = zones(config(houseProbability = 1.0))
        val house = assertNotNull(z.buildHouse(0, 0))
        assertTrue(house.width in houseType.widthMin..houseType.widthMax)
        assertTrue(house.depth in houseType.depthMin..houseType.depthMax)
    }

    @Test
    fun sameSeed_producesSameHouseLayout() {
        val a = zones(seed = 42L)
        val b = zones(seed = 42L)
        assertEquals(a.buildHouse(1, 1), b.buildHouse(1, 1))
    }

    @Test
    fun housesNear_onlyReturnsHousesOverlappingChunk() {
        val z = zones(config(houseProbability = 1.0))
        val nearby = z.housesNear(0, 0)
        val chunkSize = org.micoli.micraft.world.WorldConstants.CHUNK_SIZE
        for (h in nearby) {
            assertTrue(h.anchorX + h.width > 0)
            assertTrue(h.anchorX < chunkSize)
            assertTrue(h.anchorZ + h.depth > 0)
            assertTrue(h.anchorZ < chunkSize)
        }
    }
}
