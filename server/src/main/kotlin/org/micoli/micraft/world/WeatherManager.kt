package org.micoli.micraft.world

import java.util.UUID
import kotlin.math.sqrt
import kotlin.random.Random
import org.micoli.micraft.protocol.ServerMessage
import org.slf4j.LoggerFactory

private val log = LoggerFactory.getLogger("WeatherManager")

private data class ActiveZone(
    val id: String,
    val type: WeatherType,
    var cx: Float,
    var cz: Float,
    val vx: Float,
    val vz: Float,
    val radius: Float,
    val intensity: Float,
    var ticksRemaining: Long,
)

private fun ActiveZone.toInfo() = WeatherZoneInfo(id, type.name, cx, cz, radius, intensity)

class WeatherManager(private var config: WeatherConfig) {
    private val activeZones = mutableListOf<ActiveZone>()
    private var spawnCheckCounter = 0
    private var broadcastCounter = 0

    @Volatile private var dirty = false

    suspend fun tick(world: WorldState, broadcast: suspend (ServerMessage) -> Unit) {
        var changed = false

        // Move zones and decrement TTL
        val expired = mutableListOf<ActiveZone>()
        for (zone in activeZones) {
            zone.cx += zone.vx
            zone.cz += zone.vz
            zone.ticksRemaining--
            if (zone.ticksRemaining <= 0) expired.add(zone)
        }
        if (expired.isNotEmpty()) {
            activeZones.removeAll(expired)
            changed = true
            log.debug("Expired {} weather zones", expired.size)
        }

        // Try spawning new zones every 20 ticks
        if (config.data.enabled) {
            spawnCheckCounter++
            if (spawnCheckCounter >= 20) {
                spawnCheckCounter = 0
                if (trySpawn(world)) changed = true
            }
        }

        broadcastCounter++
        if (changed || broadcastCounter >= 200) {
            broadcastCounter = 0
            broadcast(ServerMessage.WeatherUpdate(activeZones.map { it.toInfo() }))
        }
    }

    private fun trySpawn(world: WorldState): Boolean {
        val chunks = world.discoveredChunks().toList()
        if (chunks.isEmpty()) return false

        // Group chunks by biome
        val byBiome = mutableMapOf<String, MutableList<ChunkPos>>()
        for (pos in chunks) {
            val biome =
                world.biomeAt(
                    pos.cx * WorldConstants.CHUNK_SIZE + 8, pos.cz * WorldConstants.CHUNK_SIZE + 8)
            byBiome.getOrPut(biome) { mutableListOf() }.add(pos)
        }

        var spawned = false
        for (typeConfig in config.data.weatherTypes) {
            if (!typeConfig.enabled) continue
            val matchingChunks =
                typeConfig.biomes.flatMap { biome -> byBiome[biome] ?: emptyList() }
            if (matchingChunks.isEmpty()) continue

            val roll = Random.nextDouble()
            val threshold = typeConfig.spawnRatePerBiomeTick * matchingChunks.size
            if (roll >= threshold) continue

            val spawnChunk = matchingChunks.random()
            val cx = spawnChunk.cx * WorldConstants.CHUNK_SIZE.toFloat() + 8f
            val cz = spawnChunk.cz * WorldConstants.CHUNK_SIZE.toFloat() + 8f

            val radius =
                typeConfig.minRadius +
                    Random.nextFloat() * (typeConfig.maxRadius - typeConfig.minRadius)
            val duration =
                typeConfig.minDurationTicks +
                    Random.nextLong(typeConfig.maxDurationTicks - typeConfig.minDurationTicks + 1)
            val speed = typeConfig.driftSpeed
            val angle = Random.nextFloat() * 2f * kotlin.math.PI.toFloat()
            val vx = kotlin.math.cos(angle) * speed
            val vz = kotlin.math.sin(angle) * speed

            val weatherType =
                runCatching { WeatherType.valueOf(typeConfig.type) }.getOrNull() ?: continue

            activeZones.add(
                ActiveZone(
                    id = UUID.randomUUID().toString(),
                    type = weatherType,
                    cx = cx,
                    cz = cz,
                    vx = vx,
                    vz = vz,
                    radius = radius,
                    intensity = 0.8f,
                    ticksRemaining = duration,
                ))
            log.info(
                "Spawned {} zone at ({}, {}) r={} dur={} ticks",
                typeConfig.type,
                cx.toInt(),
                cz.toInt(),
                radius.toInt(),
                duration)
            spawned = true
        }
        return spawned
    }

    fun forceWeather(type: WeatherType, cx: Float, cz: Float, radius: Float = 64f) {
        val duration =
            config.data.weatherTypes.find { it.type == type.name }?.maxDurationTicks ?: 6000L
        activeZones.add(
            ActiveZone(
                id = UUID.randomUUID().toString(),
                type = type,
                cx = cx,
                cz = cz,
                vx = 0f,
                vz = 0f,
                radius = radius,
                intensity = 1f,
                ticksRemaining = duration,
            ))
        dirty = true
        log.info("Forced {} zone at ({}, {})", type, cx.toInt(), cz.toInt())
    }

    fun clearAllZones() {
        activeZones.clear()
        dirty = true
        log.info("All weather zones cleared")
    }

    fun getZones(): List<WeatherZoneInfo> = activeZones.map { it.toInfo() }

    fun distanceTo(zone: WeatherZoneInfo, x: Float, z: Float): Float {
        val dx = zone.cx - x
        val dz = zone.cz - z
        return sqrt(dx * dx + dz * dz)
    }

    fun reload(newConfig: WeatherConfig) {
        config = newConfig
    }
}
