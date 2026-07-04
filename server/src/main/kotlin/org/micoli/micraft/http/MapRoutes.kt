package org.micoli.micraft.http

import io.ktor.http.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import java.util.concurrent.ConcurrentHashMap
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import org.micoli.micraft.GameLoop
import org.micoli.micraft.world.BlockRegistry
import org.micoli.micraft.world.proceduralGenerator.ProceduralChunkGenerator
import org.micoli.micraft.world.proceduralGenerator.weather.WeatherZoneInfo

@Serializable
data class PlayerMapInfo(
    val id: String,
    val name: String,
    val x: Float,
    val y: Float,
    val z: Float,
    val yaw: Float,
)

@Serializable
data class NpcMapInfo(
    val id: String,
    val name: String,
    val type: String,
    val x: Float,
    val y: Float,
    val z: Float,
    val yaw: Float,
)

@Serializable
data class MapStateResponse(
    val gameTicks: Long,
    val players: List<PlayerMapInfo>,
    val npcs: List<NpcMapInfo>,
    val weatherZones: List<WeatherZoneInfo> = emptyList(),
)

@Serializable
data class VoronoiCellInfo(val x: Int, val z: Int, val biome: String, val color: String)

@Serializable
data class ChunkTerrainInfo(
    val cx: Int,
    val cz: Int,
    /** Flat 16×16 array (index = lx*16+lz), hex color or null for air/no color. */
    val colors: List<String?>,
    val avgHeight: Int? = null,
)

@Serializable
data class HouseMapInfo(val x: Int, val z: Int, val type: String, val width: Int, val depth: Int)

@Serializable
data class RoadSegmentInfo(val x1: Float, val z1: Float, val x2: Float, val z2: Float)

@Serializable
data class ChunkRoadInfo(val cx: Int, val cz: Int, val mask: List<Boolean>)

@Serializable
data class ChunkBiomeBorderInfo(val cx: Int, val cz: Int, val mask: List<Boolean>)

fun Route.mapRoutes(gameLoop: GameLoop) {
    if (!(System.getenv("MICRAFT_MAP_ENABLED") != "0")) {
        return
    }
    val roadRasterCache = ConcurrentHashMap<Long, List<Boolean>>()
    val biomeBorderCache = ConcurrentHashMap<Long, List<Boolean>>()
    get("/api/map/state") {
        val players =
            gameLoop.getPlayerStates().map { s ->
                PlayerMapInfo(s.id, s.name, s.pos.x, s.pos.y, s.pos.z, s.orientation.yaw)
            }
        val npcs =
            gameLoop.getNpcStates().map { n ->
                NpcMapInfo(n.id, n.name, n.type, n.pos.x, n.pos.y, n.pos.z, n.yaw)
            }
        val response =
            MapStateResponse(gameLoop.getGameTicks(), players, npcs, gameLoop.getWeatherZones())
        call.response.headers.append(HttpHeaders.AccessControlAllowOrigin, "*")
        call.respondText(Json.encodeToString(response), ContentType.Application.Json)
    }

    get("/api/map/terrain") {
        call.response.headers.append(HttpHeaders.AccessControlAllowOrigin, "*")
        call.respondText(gameLoop.terrainCache.cachedJson, ContentType.Application.Json)
    }

    get("/api/map/voronoi") {
        val cx = call.request.queryParameters["cx"]?.toIntOrNull() ?: 0
        val cz = call.request.queryParameters["cz"]?.toIntOrNull() ?: 0
        val radius = call.request.queryParameters["radius"]?.toIntOrNull() ?: (50 * 16)
        val gen = gameLoop.getChunkGenerator() as? ProceduralChunkGenerator
        val cells = gen?.voronoi?.cells(cx, cz, radius)?.map { cell ->
            val rgb = BlockRegistry.get(cell.biome.surface).minimapColor
            val color = "#%02x%02x%02x".format(rgb[0], rgb[1], rgb[2])
            VoronoiCellInfo(cell.seedX, cell.seedZ, cell.biome.id, color)
        } ?: emptyList()
        call.response.headers.append(HttpHeaders.AccessControlAllowOrigin, "*")
        call.respondText(Json.encodeToString(cells), ContentType.Application.Json)
    }

    get("/api/map/houses") {
        val cx = call.request.queryParameters["cx"]?.toIntOrNull() ?: 0
        val cz = call.request.queryParameters["cz"]?.toIntOrNull() ?: 0
        val radius = call.request.queryParameters["radius"]?.toIntOrNull() ?: 800
        val gen = gameLoop.getChunkGenerator() as? ProceduralChunkGenerator
        val houses =
            gen?.houseZones
                ?.housesInArea(cx - radius, cz - radius, cx + radius, cz + radius)
                ?.map { HouseMapInfo(it.anchorX, it.anchorZ, it.typeCfg.id, it.width, it.depth) }
                ?: emptyList()
        call.response.headers.append(HttpHeaders.AccessControlAllowOrigin, "*")
        call.respondText(Json.encodeToString(houses), ContentType.Application.Json)
    }

    get("/api/map/roads") {
        val cx = call.request.queryParameters["cx"]?.toIntOrNull() ?: 0
        val cz = call.request.queryParameters["cz"]?.toIntOrNull() ?: 0
        val radius = call.request.queryParameters["radius"]?.toIntOrNull() ?: 800
        val gen = gameLoop.getChunkGenerator() as? ProceduralChunkGenerator
        val segments =
            gen?.roadVoronoi
                ?.roadVertexSegmentsInArea(cx - radius, cz - radius, cx + radius, cz + radius)
                ?.map { RoadSegmentInfo(it.x1.toFloat(), it.z1.toFloat(), it.x2.toFloat(), it.z2.toFloat()) }
                ?: emptyList()
        call.response.headers.append(HttpHeaders.AccessControlAllowOrigin, "*")
        call.respondText(Json.encodeToString(segments), ContentType.Application.Json)
    }

    get("/api/map/road-raster") {
        val cx = call.request.queryParameters["cx"]?.toIntOrNull() ?: 0
        val cz = call.request.queryParameters["cz"]?.toIntOrNull() ?: 0
        val radius = call.request.queryParameters["radius"]?.toIntOrNull() ?: 800
        val gen = gameLoop.getChunkGenerator() as? ProceduralChunkGenerator
        val roadVoronoi = gen?.roadVoronoi
        val chunks = mutableListOf<ChunkRoadInfo>()
        if (roadVoronoi != null) {
            val cxMin = Math.floorDiv(cx - radius, 16)
            val cxMax = Math.floorDiv(cx + radius, 16)
            val czMin = Math.floorDiv(cz - radius, 16)
            val czMax = Math.floorDiv(cz + radius, 16)
            for (chunkX in cxMin..cxMax) {
                for (chunkZ in czMin..czMax) {
                    val key = chunkX.toLong() shl 32 or (chunkZ.toLong() and 0xFFFFFFFFL)
                    val mask = roadRasterCache.getOrPut(key) {
                        buildList {
                            for (lx in 0 until 16) {
                                for (lz in 0 until 16) {
                                    add(roadVoronoi.isOnRoadAt(chunkX * 16 + lx, chunkZ * 16 + lz))
                                }
                            }
                        }
                    }
                    if (mask.any { it }) chunks.add(ChunkRoadInfo(chunkX, chunkZ, mask))
                }
            }
        }
        call.response.headers.append(HttpHeaders.AccessControlAllowOrigin, "*")
        call.respondText(Json.encodeToString(chunks), ContentType.Application.Json)
    }

    get("/api/map/biome-borders") {
        val cx = call.request.queryParameters["cx"]?.toIntOrNull() ?: 0
        val cz = call.request.queryParameters["cz"]?.toIntOrNull() ?: 0
        val radius = call.request.queryParameters["radius"]?.toIntOrNull() ?: 1500
        val gen = gameLoop.getChunkGenerator() as? ProceduralChunkGenerator
        val voronoi = gen?.voronoi
        val chunks = mutableListOf<ChunkBiomeBorderInfo>()
        if (voronoi != null) {
            val cxMin = Math.floorDiv(cx - radius, 16)
            val cxMax = Math.floorDiv(cx + radius, 16)
            val czMin = Math.floorDiv(cz - radius, 16)
            val czMax = Math.floorDiv(cz + radius, 16)
            for (chunkX in cxMin..cxMax) {
                for (chunkZ in czMin..czMax) {
                    val key = chunkX.toLong() shl 32 or (chunkZ.toLong() and 0xFFFFFFFFL)
                    val mask = biomeBorderCache.getOrPut(key) {
                        buildList {
                            for (lx in 0 until 16) {
                                for (lz in 0 until 16) {
                                    val wx = chunkX * 16 + lx
                                    val wz = chunkZ * 16 + lz
                                    val s = voronoi.sample(wx, wz)
                                    val cellId = "${s.primarySeedX},${s.primarySeedZ}"
                                    fun cellAt(x: Int, z: Int): String {
                                        val n = voronoi.sample(x, z)
                                        return "${n.primarySeedX},${n.primarySeedZ}"
                                    }
                                    add(
                                        cellAt(wx + 1, wz) != cellId ||
                                            cellAt(wx - 1, wz) != cellId ||
                                            cellAt(wx, wz + 1) != cellId ||
                                            cellAt(wx, wz - 1) != cellId
                                    )
                                }
                            }
                        }
                    }
                    if (mask.any { it }) chunks.add(ChunkBiomeBorderInfo(chunkX, chunkZ, mask))
                }
            }
        }
        call.response.headers.append(HttpHeaders.AccessControlAllowOrigin, "*")
        call.respondText(Json.encodeToString(chunks), ContentType.Application.Json)
    }

    get("/map") {
        val html = Thread.currentThread().contextClassLoader.getResourceAsStream("map.html")!!.bufferedReader().readText()
        call.respondText(html, ContentType.Text.Html)
    }
    get("/map.js") {
        val js = Thread.currentThread().contextClassLoader.getResourceAsStream("map.js")!!.bufferedReader().readText()
        call.respondText(js, ContentType.Text.JavaScript)
    }
}
