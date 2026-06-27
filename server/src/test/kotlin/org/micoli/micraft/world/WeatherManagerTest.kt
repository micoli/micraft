package org.micoli.micraft.world

import kotlin.io.path.createTempFile
import kotlin.io.path.writeText
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlinx.coroutines.runBlocking
import org.micoli.micraft.world.proceduralGenerator.chunkGenerator.ChunkGenerator
import org.micoli.micraft.world.proceduralGenerator.weather.WeatherType

private fun minimalConfig(): WeatherConfig {
    val tmp = createTempFile(suffix = ".yaml")
    tmp.toFile().deleteOnExit()
    tmp.writeText(
        """
weatherTypes:
  - type: RAIN
    biomes: [plains]
    spawnRatePerBiomeTick: 1.0
    minDurationTicks: 100
    maxDurationTicks: 100
    minRadius: 50.0
    maxRadius: 50.0
    driftSpeed: 0.0
"""
            .trimIndent())
    return WeatherConfig(tmp)
}

class WeatherManagerTest {

    @Test
    fun forceWeather_createsZone() {
        val world = fakeWorldState()
        world.getOrGenerate(ChunkPos(6, 12)) // chunk covering (96..111, 192..207)
        val manager = WeatherManager(minimalConfig())
        assertTrue(manager.forceWeather(WeatherType.RAIN, 100f, 200f, world))
        val zones = manager.getZones()
        assertEquals(1, zones.size)
        assertEquals("RAIN", zones[0].type)
        assertEquals(100f, zones[0].cx)
        assertEquals(200f, zones[0].cz)
    }

    @Test
    fun clearAllZones_removesAll() = runBlocking {
        val world = fakeWorldState()
        world.getOrGenerate(ChunkPos(0, 0))
        world.getOrGenerate(ChunkPos(12, 0)) // chunk covering (192..207, 0..15)
        val manager = WeatherManager(minimalConfig())
        manager.forceWeather(WeatherType.RAIN, 0f, 0f, world)
        manager.forceWeather(
            WeatherType.RAIN, 200f, 0f, world) // 200m apart > 128 (r+r), no overlap
        assertEquals(2, manager.getZones().size)
        manager.clearAllZones()
        assertTrue(manager.getZones().isEmpty())
    }

    @Test
    fun tick_expiredZone_removed() = runBlocking {
        val world = fakeWorldState()
        world.getOrGenerate(ChunkPos(0, 0))
        val manager = WeatherManager(minimalConfig())
        manager.forceWeather(WeatherType.RAIN, 0f, 0f, world)

        assertEquals(1, manager.getZones().size)

        // Tick 110 times — zone expires after 100
        repeat(110) { manager.tick(fakeWorldState()) {} }

        assertTrue(
            manager.getZones().isEmpty(), "Zone should have expired after max duration ticks")
    }

    @Test
    fun tick_driftMovesZone() = runBlocking {
        val configTmp = createTempFile(suffix = ".yaml")
        configTmp.toFile().deleteOnExit()
        configTmp.writeText(
            """
weatherTypes:
  - type: RAIN
    biomes: [plains]
    spawnRatePerBiomeTick: 0.0
    minDurationTicks: 10000
    maxDurationTicks: 10000
    minRadius: 50.0
    maxRadius: 50.0
    driftSpeed: 2.0
"""
                .trimIndent())
        val config = WeatherConfig(configTmp)
        val world = fakeWorldState()
        world.getOrGenerate(ChunkPos(0, 1)) // chunk covering (0..15, 16..31), contains (10, 20)
        val manager = WeatherManager(config)

        // forceWeather uses vx=0/vz=0, so position should remain unchanged after tick
        manager.forceWeather(WeatherType.RAIN, 10f, 20f, world)
        manager.tick(fakeWorldState()) {}
        val zone = manager.getZones().first()
        assertEquals(10f, zone.cx, "cx should not change — forced zone has zero drift")
        assertEquals(20f, zone.cz, "cz should not change — forced zone has zero drift")
    }

    @Test
    fun distanceTo_correctDistance() {
        val world = fakeWorldState()
        world.getOrGenerate(ChunkPos(0, 0))
        val manager = WeatherManager(minimalConfig())
        manager.forceWeather(WeatherType.RAIN, 0f, 0f, world)
        val zone = manager.getZones().first()
        val dist = manager.distanceTo(zone, 3f, 4f)
        assertEquals(5f, dist, 0.001f)
    }

    @Test
    fun reload_updatesConfig() {
        val manager = WeatherManager(minimalConfig())
        val newConfig = minimalConfig()
        manager.reload(newConfig)
        assertTrue(manager.getZones().isEmpty())
    }

    @Test
    fun forceWeather_rejectedOnUndiscoveredChunk() {
        val world = fakeWorldState() // no chunks discovered
        val manager = WeatherManager(minimalConfig())
        assertFalse(manager.forceWeather(WeatherType.RAIN, 100f, 200f, world))
        assertTrue(manager.getZones().isEmpty())
    }

    @Test
    fun forceWeather_acceptedOnDiscoveredChunk() {
        val world = fakeWorldState()
        world.getOrGenerate(ChunkPos(0, 0))
        val manager = WeatherManager(minimalConfig())
        assertTrue(manager.forceWeather(WeatherType.RAIN, 8f, 8f, world))
        assertEquals(1, manager.getZones().size)
    }

    @Test
    fun forceWeather_replacesOverlapping() {
        val world = fakeWorldState()
        world.getOrGenerate(ChunkPos(0, 0))
        val manager = WeatherManager(minimalConfig())
        manager.forceWeather(WeatherType.RAIN, 8f, 8f, world)
        manager.forceWeather(WeatherType.SNOW, 8f, 8f, world) // same position — replaces
        assertEquals(1, manager.getZones().size)
        assertEquals("SNOW", manager.getZones()[0].type)
    }

    @Test
    fun noOverlap_distantZones() {
        val world = fakeWorldState()
        world.getOrGenerate(ChunkPos(0, 0))
        world.getOrGenerate(ChunkPos(100, 0)) // chunk at cx=100, far from chunk 0
        val manager = WeatherManager(minimalConfig())
        manager.forceWeather(WeatherType.RAIN, 8f, 8f, world) // radius=64f default
        manager.forceWeather(WeatherType.SNOW, 1608f, 8f, world) // 100*16+8=1608, 1600m apart
        assertEquals(2, manager.getZones().size)
    }

    @Test
    fun trySpawn_noOverlap() = runBlocking {
        val world = fakeWorldState()
        world.getOrGenerate(ChunkPos(0, 0))
        val manager = WeatherManager(minimalConfig())
        // Force a large zone that covers the only discovered chunk
        manager.forceWeather(WeatherType.RAIN, 8f, 8f, world, radius = 10000f)
        assertEquals(1, manager.getZones().size)
        // 25 ticks triggers spawnCheckCounter (fires every 20); spawnRatePerBiomeTick=1.0 always
        // spawns
        repeat(25) { manager.tick(world) {} }
        assertEquals(1, manager.getZones().size, "trySpawn must not create overlapping zone")
    }
}

private object PlainsGenerator : ChunkGenerator {
    override fun generate(pos: ChunkPos): Chunk = Chunk.empty(pos)

    override fun biomeAt(wx: Int, wz: Int): String = "plains"
}

/** Minimal WorldState for tests — returns "plains" for every position. */
private fun fakeWorldState() = WorldState(generator = PlainsGenerator)
