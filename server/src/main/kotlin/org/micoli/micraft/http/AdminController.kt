package org.micoli.micraft.http

import io.github.smiley4.ktoropenapi.config.RouteConfig
import io.github.smiley4.ktoropenapi.delete
import io.github.smiley4.ktoropenapi.get
import io.github.smiley4.ktoropenapi.post
import io.github.smiley4.ktoropenapi.put
import io.ktor.http.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.server.routing.get as undocumentedGet
import io.ktor.server.websocket.*
import io.ktor.websocket.*
import java.io.ByteArrayOutputStream
import java.io.DataOutputStream
import java.io.File
import java.nio.file.Path
import kotlin.io.path.exists
import kotlin.io.path.isDirectory
import kotlin.io.path.listDirectoryEntries
import kotlin.io.path.nameWithoutExtension
import kotlin.io.path.readText
import kotlin.io.path.writeText
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.micoli.micraft.auth.LocalAuthProvider
import org.micoli.micraft.auth.TokenStore
import org.micoli.micraft.game.GameLoop
import org.micoli.micraft.game.TICKS_PER_DAY
import org.micoli.micraft.game.classes.ClassDefinitionEntry
import org.micoli.micraft.game.npc.NpcConstants
import org.micoli.micraft.game.world.BlockPos
import org.micoli.micraft.game.world.BlockRegistry
import org.micoli.micraft.game.world.BlockState
import org.micoli.micraft.game.world.BlockType
import org.micoli.micraft.game.world.ChunkPos
import org.micoli.micraft.game.world.ItemRegistry
import org.micoli.micraft.game.world.PlainColorRegistry
import org.micoli.micraft.game.world.PlayerFile
import org.micoli.micraft.game.world.WorldConstants
import org.micoli.micraft.game.world.WorldMetadata
import org.micoli.micraft.game.world.WorldPersistence
import org.micoli.micraft.game.world.block.BlockBreaker
import org.micoli.micraft.game.world.block.BlockPlacer
import org.micoli.micraft.game.world.instance.InstanceClipPlanes
import org.micoli.micraft.game.world.instance.InstanceZone
import org.micoli.micraft.game.world.scene.Scene
import org.micoli.micraft.player.rpg.BaseStats
import org.micoli.micraft.player.rpg.CharacterClass
import org.micoli.micraft.protocol.BlockChange
import org.micoli.micraft.protocol.BlockEntityProto
import org.micoli.micraft.protocol.BlockInfo
import org.micoli.micraft.protocol.EntityRemoveAt
import org.micoli.micraft.protocol.ItemInfo
import org.micoli.micraft.protocol.NpcCodexInfo
import org.micoli.micraft.protocol.PlainColorInfo

@Serializable
data class UserDto(val email: String, val displayName: String, val groups: List<String>)

@Serializable
data class NpcAdminDto(
    val id: String,
    val name: String,
    val type: String,
    val level: Int,
    val xp: Int,
    val gender: String?,
    val currentHp: Int,
    val maxHp: Int,
    val isDead: Boolean,
    val aggroMode: String,
    val tier: String,
    val x: Float,
    val y: Float,
    val z: Float,
    val yaw: Float,
    val zone: String,
    val parentIds: List<String>,
    val skills: List<String>,
    val ageGameDays: Double?,
    val hunger: Double?,
    val gestationRemainingDays: Double?,
    val lastReproductionDay: Double?,
    val motherLevel: Int?,
    val animalStats: BaseStats?,
)

@Serializable data class InstanceRenameRequest(val name: String)

@Serializable data class InstanceBoundsRequest(val yMin: Int, val yMax: Int)

@Serializable data class InstanceChunksRequest(val chunks: List<ChunkPos>)

@Serializable data class InstanceEnabledRequest(val enabled: Boolean)

@Serializable
data class InstanceLayoutRequest(
    val clipPlanes: InstanceClipPlanes,
    val shortcutBarPages: List<List<String?>>
)

@Serializable
data class InstanceCreateRequest(
    val name: String,
    val yMin: Int,
    val yMax: Int,
    val chunks: List<ChunkPos>
)

@Serializable
data class InstanceBlockDto(
    val x: Int,
    val y: Int,
    val z: Int,
    val type: String,
    val state: Byte = 0,
    val xOffset: Byte = 0,
    val zOffset: Byte = 0
)

// Batch envelope for the collaborative edit WS — lets a bulk selection edit (fill/shell/cut in the
// admin voxel editor) apply as one frame with one broadcast, instead of one WS round-trip and one
// broadcast per voxel. Distinguished from a bare InstanceBlockDto/SceneBlockDto frame purely by the
// required "edits" field (decoding a bare single-edit frame as the batch DTO fails since it has no
// "edits" key — see registerEditWs).
@Serializable data class InstanceBlockBatchDto(val edits: List<InstanceBlockDto>)

@Serializable
data class SceneDto(
    val id: String,
    val name: String,
    val width: Int,
    val height: Int,
    val depth: Int,
    val ownerName: String,
    val createdAt: Long,
    val shortcutBarPages: List<List<String?>>,
)

@Serializable
data class SceneCreateRequest(val name: String, val width: Int, val height: Int, val depth: Int)

@Serializable data class SceneRenameRequest(val name: String)

@Serializable data class SceneDimensionsRequest(val width: Int, val height: Int, val depth: Int)

@Serializable data class SceneLayoutRequest(val shortcutBarPages: List<List<String?>>)

@Serializable
data class SceneBlockDto(val x: Int, val y: Int, val z: Int, val type: String, val state: Byte = 0)

// See InstanceBlockBatchDto for the rationale — same envelope shape, scene variant.
@Serializable data class SceneBlockBatchDto(val edits: List<SceneBlockDto>)

private fun Scene.toDto() =
    SceneDto(
        id = id,
        name = name,
        width = width,
        height = height,
        depth = depth,
        ownerName = ownerName,
        createdAt = createdAt,
        shortcutBarPages = shortcutBarPages)

@Serializable private data class SetGameTimeRequest(val hour: Int, val minute: Int = 0)

@Serializable
private data class CreateUserRequest(
    val email: String,
    val password: String? = null,
    val displayName: String? = null,
    val groups: List<String> = emptyList(),
)

@Serializable
private data class UpdateUserRequest(
    val displayName: String? = null,
    val groups: List<String>? = null
)

@Serializable private data class RenamePlayerRequest(val newName: String)

@Serializable
private data class UpdatePlayerPreferencesRequest(
    val skin: String? = null,
    val language: String? = null,
    val fieldOfView: Int? = null,
    val shadersEnabled: Boolean? = null,
    val animatedFavicon: Boolean? = null,
    val godMode: Boolean? = null,
    val lightBoostEnabled: Boolean? = null,
)

@Serializable
private data class UpdatePlayerRpgRequest(
    val characterClass: String? = null,
    val str: Int? = null,
    val dex: Int? = null,
    val intel: Int? = null,
    val wis: Int? = null,
    val con: Int? = null,
    val cha: Int? = null,
)

@Serializable private data class CreateWorldRequest(val name: String, val seed: Long = 42L)

@Serializable
private data class SkillsResponse(val attacks: List<String>, val spells: List<String>)

@Serializable
data class WorldStatsDto(
    val name: String,
    val seed: Long,
    val generator: String,
    val createdAt: String,
    val chunkCount: Int,
    val playerCount: Int,
    val isActive: Boolean,
)

private const val MAX_STREAMED_BLOCKS = 300_000

private val adminJson = Json {
    encodeDefaults = true
    ignoreUnknownKeys = true
}

private val configFileWhitelist =
    setOf(
        "server.yaml",
        "biomes.yaml",
        "roads.yaml",
        "houses.yaml",
        "keybindings.yaml",
        "npc.yaml",
        "items.yaml",
        "recipes.yaml",
        "classes.yaml",
        "combat.yaml",
        "experience.yaml",
        "weather.yaml",
        "trade.yaml",
        "vegetation.yaml",
    )

class AdminController(
    private val localAuth: LocalAuthProvider?,
    private val noAuthAccountStore: org.micoli.micraft.auth.NoAuthAccountStore?,
    private val persistence: WorldPersistence?,
    private val gameLoop: GameLoop,
    private val dataPath: String,
    private val tokenStore: TokenStore? = null,
) {
    private val configDir = Path.of("$dataPath/config")
    private val worldsDir = Path.of("$dataPath/world")
    private val activeWorldName: String =
        System.getenv("MICRAFT_WORLD_NAME")?.takeIf { it.isNotBlank() } ?: "default_world"

    // Chunks eligible for an instance zone: in-memory (this run) union persisted-to-disk (any
    // prior run) — WorldState.discoveredChunks() alone misses chunks generated before the last
    // server restart that no player has revisited yet.
    private fun generatedChunks(): Set<ChunkPos> =
        gameLoop.getWorldState().discoveredChunks() +
            (persistence?.persistedChunkPositions() ?: emptySet())

    private suspend fun RoutingContext.requireAdmin(): Boolean {
        tokenStore ?: return true
        val token = call.request.headers[HttpHeaders.Authorization]?.removePrefix("Bearer ")?.trim()
        val auth = if (token != null) tokenStore.validate(token) else null
        if (auth == null) {
            call.respond(HttpStatusCode.Unauthorized)
            return false
        }
        if ("*" !in auth.permissions && "admin" !in auth.permissions) {
            call.respond(HttpStatusCode.Forbidden)
            return false
        }
        return true
    }

    // ── Collaborative block-edit WS (scenes/instances) ──────────────────────
    // Keyed by "scene:$id" / "instance:$id". Each listener pushes the JSON-encoded applied edit
    // to one connected admin editor socket; broadcastEdit excludes the socket that sent it (it
    // already applied the edit optimistically, no round-trip needed).
    private val editListeners =
        java.util.concurrent.ConcurrentHashMap<String, MutableSet<suspend (String) -> Unit>>()

    private fun addEditListener(key: String, listener: suspend (String) -> Unit) {
        editListeners
            .computeIfAbsent(key) { java.util.concurrent.ConcurrentHashMap.newKeySet() }
            .add(listener)
    }

    private fun removeEditListener(key: String, listener: suspend (String) -> Unit) {
        editListeners[key]?.remove(listener)
    }

    private suspend fun broadcastEdit(
        key: String,
        json: String,
        excluding: suspend (String) -> Unit
    ) {
        editListeners[key]?.forEach { listener ->
            if (listener !== excluding) {
                try {
                    listener(json)
                } catch (_: Exception) {}
            }
        }
    }

    private sealed class EditResult<out T> {
        data class Applied<T>(val dto: T) : EditResult<T>()

        data class Failed(val message: String) : EditResult<Nothing>()
    }

    // What a single instance edit changed, still unbroadcast — split out from
    // applyInstanceBlockEdit so a batch of edits (see InstanceBlockBatchDto) can accumulate every
    // DTO's changes/entityAdds/entityRemoves and fire ONE GameLoop.broadcastWorldUpdate for the
    // whole batch instead of one per voxel.
    private data class InstanceEditOutcome(
        val dto: InstanceBlockDto,
        val changes: List<BlockChange>,
        val entityAdds: List<BlockEntityProto> = emptyList(),
        val entityRemoves: List<BlockPos> = emptyList(),
        val entityRemovesAt: List<EntityRemoveAt> = emptyList(),
    )

    /**
     * Shared by the WS scene-edit handler (only writer for scene blocks). Mirrors the previous `PUT
     * /api/admin/scenes/{id}/blocks` handler body.
     */
    private fun applySceneBlockEdit(id: String, dto: SceneBlockDto): EditResult<SceneBlockDto> {
        val type =
            BlockRegistry.all().find { it.id == dto.type }
                ?: return EditResult.Failed("Unknown block type")
        val ok =
            gameLoop
                .scenes()
                .setBlock(
                    id, dto.x, dto.y, dto.z, BlockRegistry.wireIndex(type).toByte(), dto.state)
        if (!ok) return EditResult.Failed("Scene not found or coordinate out of bounds")
        return EditResult.Applied(dto)
    }

    /**
     * Shared by the WS instance-edit handler (only writer for instance blocks). Mirrors the
     * previous `PUT /api/admin/instances/{id}/blocks` handler body — mutates [WorldState] and
     * returns what changed, but does NOT broadcast: the caller (single-edit or batch path in
     * registerEditWs) decides how many outcomes to fold into one [GameLoop.broadcastWorldUpdate]
     * call.
     */
    private fun applyInstanceBlockEdit(
        id: String,
        dto: InstanceBlockDto
    ): EditResult<InstanceEditOutcome> {
        val zone = gameLoop.instances().get(id) ?: return EditResult.Failed("Instance not found")
        if (!zone.contains(dto.x, dto.y, dto.z)) {
            return EditResult.Failed("Coordinate outside zone bounds")
        }
        val type =
            BlockRegistry.all().find { it.id == dto.type }
                ?: return EditResult.Failed("Unknown block type")
        val world = gameLoop.getWorldState()
        val pos = BlockPos(dto.x, dto.y, dto.z)
        if (type == BlockType.AIR) {
            val result =
                BlockBreaker.breakAt(
                    pos, dto.xOffset.toInt() and 0xFF, dto.zOffset.toInt() and 0xFF, world)
            return EditResult.Applied(
                InstanceEditOutcome(
                    dto,
                    changes = result.changes,
                    entityRemoves = result.entityRemoves,
                    entityRemovesAt = result.entityRemovesAt))
        }
        val rotation = BlockState.rotation(dto.state)
        val colorIndex = BlockState.colorIndex(dto.state)
        val result =
            BlockPlacer.placeAt(
                pos,
                type,
                rotation,
                colorIndex,
                dto.xOffset.toInt() and 0xFF,
                dto.zOffset.toInt() and 0xFF,
                world)
        if (result.rejectedReason != null) return EditResult.Failed(result.rejectedReason)
        return EditResult.Applied(
            InstanceEditOutcome(dto, changes = result.changes, entityAdds = result.entityAdds))
    }

    private suspend fun DefaultWebSocketServerSession.authorizeAdminWs(): Boolean {
        if (tokenStore == null) return true
        val token = call.request.queryParameters["token"]
        val auth = token?.let { tokenStore.validate(it) }
        if (auth == null || ("*" !in auth.permissions && "admin" !in auth.permissions)) {
            close(CloseReason(CloseReason.Codes.VIOLATED_POLICY, "Unauthorized"))
            return false
        }
        return true
    }

    fun registerEditWs(route: Route) =
        route.apply {
            webSocket("/api/admin/ws/scenes/{id}") {
                if (!authorizeAdminWs()) return@webSocket
                val id =
                    call.parameters["id"]
                        ?: return@webSocket close(
                            CloseReason(CloseReason.Codes.CANNOT_ACCEPT, "Missing id"))
                if (gameLoop.scenes().get(id) == null) {
                    return@webSocket close(
                        CloseReason(CloseReason.Codes.CANNOT_ACCEPT, "Not found"))
                }
                val key = "scene:$id"
                val listener: suspend (String) -> Unit = { json ->
                    try {
                        send(json)
                    } catch (_: Exception) {}
                }
                addEditListener(key, listener)
                try {
                    for (frame in incoming) {
                        if (frame !is Frame.Text) continue
                        val text = frame.readText()
                        // Batch envelope (fill/shell/cut selection edits) is tried first — it
                        // reliably fails to decode a bare single-edit frame, since "edits" has no
                        // default and is absent there. See SceneBlockBatchDto.
                        val batch =
                            runCatching { Json.decodeFromString<SceneBlockBatchDto>(text) }
                                .getOrNull()
                        if (batch != null) {
                            val applied = mutableListOf<SceneBlockDto>()
                            for (dto in batch.edits) {
                                if (applySceneBlockEdit(id, dto) is EditResult.Applied)
                                    applied += dto
                            }
                            if (applied.isNotEmpty()) {
                                broadcastEdit(
                                    key,
                                    adminJson.encodeToString(
                                        SceneBlockBatchDto.serializer(),
                                        SceneBlockBatchDto(applied)),
                                    listener)
                            } else {
                                send(
                                    """{"type":"error","message":"Batch rejected: no valid edits"}""")
                            }
                            continue
                        }
                        val dto =
                            try {
                                Json.decodeFromString<SceneBlockDto>(text)
                            } catch (e: Exception) {
                                send("""{"type":"error","message":"Invalid block edit"}""")
                                continue
                            }
                        when (val result = applySceneBlockEdit(id, dto)) {
                            is EditResult.Applied ->
                                broadcastEdit(
                                    key,
                                    adminJson.encodeToString(SceneBlockDto.serializer(), dto),
                                    listener)
                            is EditResult.Failed ->
                                send("""{"type":"error","message":${"\"${result.message}\""}}""")
                        }
                    }
                } finally {
                    removeEditListener(key, listener)
                }
            }

            webSocket("/api/admin/ws/instances/{id}") {
                if (!authorizeAdminWs()) return@webSocket
                val id =
                    call.parameters["id"]
                        ?: return@webSocket close(
                            CloseReason(CloseReason.Codes.CANNOT_ACCEPT, "Missing id"))
                if (gameLoop.instances().get(id) == null) {
                    return@webSocket close(
                        CloseReason(CloseReason.Codes.CANNOT_ACCEPT, "Not found"))
                }
                val key = "instance:$id"
                val listener: suspend (String) -> Unit = { json ->
                    try {
                        send(json)
                    } catch (_: Exception) {}
                }
                addEditListener(key, listener)
                try {
                    for (frame in incoming) {
                        if (frame !is Frame.Text) continue
                        val text = frame.readText()
                        // Batch envelope — see the scene route above for the decode-fallback
                        // rationale.
                        val batch =
                            runCatching { Json.decodeFromString<InstanceBlockBatchDto>(text) }
                                .getOrNull()
                        if (batch != null) {
                            val applied = mutableListOf<InstanceBlockDto>()
                            val changes = mutableListOf<BlockChange>()
                            val entityAdds = mutableListOf<BlockEntityProto>()
                            val entityRemoves = mutableListOf<BlockPos>()
                            val entityRemovesAt = mutableListOf<EntityRemoveAt>()
                            for (dto in batch.edits) {
                                val result = applyInstanceBlockEdit(id, dto)
                                if (result is EditResult.Applied) {
                                    applied += dto
                                    changes += result.dto.changes
                                    entityAdds += result.dto.entityAdds
                                    entityRemoves += result.dto.entityRemoves
                                    entityRemovesAt += result.dto.entityRemovesAt
                                }
                            }
                            if (applied.isNotEmpty()) {
                                gameLoop.broadcastWorldUpdate(
                                    changes,
                                    entityAdds = entityAdds,
                                    entityRemoves = entityRemoves,
                                    entityRemovesAt = entityRemovesAt)
                                broadcastEdit(
                                    key,
                                    adminJson.encodeToString(
                                        InstanceBlockBatchDto.serializer(),
                                        InstanceBlockBatchDto(applied)),
                                    listener)
                            } else {
                                send(
                                    """{"type":"error","message":"Batch rejected: no valid edits"}""")
                            }
                            continue
                        }
                        val dto =
                            try {
                                Json.decodeFromString<InstanceBlockDto>(text)
                            } catch (e: Exception) {
                                send("""{"type":"error","message":"Invalid block edit"}""")
                                continue
                            }
                        when (val result = applyInstanceBlockEdit(id, dto)) {
                            is EditResult.Applied -> {
                                gameLoop.broadcastWorldUpdate(
                                    result.dto.changes,
                                    entityAdds = result.dto.entityAdds,
                                    entityRemoves = result.dto.entityRemoves,
                                    entityRemovesAt = result.dto.entityRemovesAt)
                                broadcastEdit(
                                    key,
                                    adminJson.encodeToString(InstanceBlockDto.serializer(), dto),
                                    listener)
                            }
                            is EditResult.Failed ->
                                send("""{"type":"error","message":${"\"${result.message}\""}}""")
                        }
                    }
                } finally {
                    removeEditListener(key, listener)
                }
            }
        }

    fun register(route: Route) {
        fun RouteConfig.requireAdminDocs() {
            response {
                code(HttpStatusCode.Unauthorized) { description = "Missing or invalid token" }
                code(HttpStatusCode.Forbidden) { description = "Missing admin permission" }
            }
        }

        route.apply {
            // ── Static assets ────────────────────────────────────────────────
            // Uses the plain (undocumented) routing.get: admin.html is markup, not a JSON API,
            // and is excluded from the spec anyway by the Application.kt pathFilter (path
            // doesn't start with /api or /auth).
            undocumentedGet("/admin/{...}") {
                call.respondFile(File("server/src/main/resources/admin.html"))
            }
            undocumentedGet("/admin") {
                call.respondFile(File("server/src/main/resources/admin.html"))
            }
            // admin.js/admin.css are NOT served here: they're esbuild/tailwind output, written
            // straight into $MICRAFT_WEB_DIST (app/webApp/build/web) by `make build-admin` — the
            // generic staticFiles("/", ...) route in Application.kt serves them from there, always
            // fresh. A dedicated route reading a fixed server/src/main/resources/ copy would
            // silently go stale the moment that copy stops being regenerated.

            // ── Status ───────────────────────────────────────────────────────
            get(
                "/api/admin/status",
                {
                    description = "Server status snapshot (TPS, players, chunks, heap, CPU)"
                    response { code(HttpStatusCode.OK) { body<StatusSnapshot>() } }
                    requireAdminDocs()
                }) {
                    if (!requireAdmin()) return@get
                    val snapshot = buildStatusSnapshot(gameLoop)
                    call.respondText(
                        adminJson.encodeToString(StatusSnapshot.serializer(), snapshot),
                        ContentType.Application.Json)
                }

            post(
                "/api/admin/restart",
                {
                    description = "Trigger a pitchfork server restart"
                    response { code(HttpStatusCode.NoContent) {} }
                    requireAdminDocs()
                }) {
                    if (!requireAdmin()) return@post
                    ProcessBuilder("pitchfork", "restart", "server").inheritIO().start()
                    call.respond(HttpStatusCode.NoContent)
                }

            put(
                "/api/admin/gametime",
                {
                    description = "Set the in-game time of day"
                    request { body<SetGameTimeRequest>() }
                    response {
                        code(HttpStatusCode.NoContent) {}
                        code(HttpStatusCode.BadRequest) { description = "Missing hour" }
                    }
                    requireAdminDocs()
                }) {
                    if (!requireAdmin()) return@put
                    val body =
                        runCatching { Json.parseToJsonElement(call.receiveText()).jsonObject }
                            .getOrNull() ?: return@put call.respond(HttpStatusCode.BadRequest)
                    val hour =
                        body["hour"]?.jsonPrimitive?.content?.toIntOrNull()
                            ?: return@put call.respond(HttpStatusCode.BadRequest)
                    val minute = body["minute"]?.jsonPrimitive?.content?.toIntOrNull() ?: 0
                    val newTicks =
                        (hour.toLong() * TICKS_PER_DAY / 24L) +
                            (minute.toLong() * TICKS_PER_DAY / 1440L)
                    gameLoop.setGameTicks(newTicks)
                    call.respond(HttpStatusCode.NoContent)
                }

            // ── Users ────────────────────────────────────────────────────────
            get(
                "/api/admin/users",
                {
                    description = "All local/no-auth accounts"
                    response {
                        code(HttpStatusCode.OK) { body<List<UserDto>>() }
                        code(HttpStatusCode.ServiceUnavailable) { description = "No auth backend" }
                    }
                    requireAdminDocs()
                }) {
                    if (!requireAdmin()) return@get
                    val users: List<UserDto> =
                        when {
                            localAuth != null ->
                                localAuth.listUsers().map {
                                    UserDto(it.email, it.displayName, it.groups)
                                }
                            noAuthAccountStore != null ->
                                noAuthAccountStore.listAccounts().map {
                                    UserDto(it.email, it.email, emptyList())
                                }
                            else -> return@get call.respond(HttpStatusCode.ServiceUnavailable)
                        }
                    call.respondText(
                        adminJson.encodeToString(ListSerializer(UserDto.serializer()), users),
                        ContentType.Application.Json)
                }

            post(
                "/api/admin/users",
                {
                    description = "Create a local/no-auth user account"
                    request { body<CreateUserRequest>() }
                    response {
                        code(HttpStatusCode.Created) {}
                        code(HttpStatusCode.BadRequest) {
                            description = "Missing email or password"
                        }
                        code(HttpStatusCode.Conflict) { description = "Email already registered" }
                        code(HttpStatusCode.ServiceUnavailable) { description = "No auth backend" }
                    }
                    requireAdminDocs()
                }) {
                    if (!requireAdmin()) return@post
                    val body =
                        runCatching { Json.parseToJsonElement(call.receiveText()).jsonObject }
                            .getOrNull() ?: return@post call.respond(HttpStatusCode.BadRequest)
                    val email =
                        body["email"]?.jsonPrimitive?.content
                            ?: return@post call.respond(HttpStatusCode.BadRequest)
                    when {
                        localAuth != null -> {
                            val password =
                                body["password"]?.jsonPrimitive?.content
                                    ?: return@post call.respond(HttpStatusCode.BadRequest)
                            val displayName = body["displayName"]?.jsonPrimitive?.content ?: email
                            val groups =
                                body["groups"]?.jsonArray?.map { it.jsonPrimitive.content }
                                    ?: emptyList()
                            runCatching { localAuth.addUser(email, password, displayName, groups) }
                                .onFailure {
                                    return@post call.respond(HttpStatusCode.Conflict)
                                }
                        }
                        noAuthAccountStore != null -> {
                            if (noAuthAccountStore.exists(email))
                                return@post call.respond(HttpStatusCode.Conflict)
                            noAuthAccountStore.getOrCreate(email)
                        }
                        else -> return@post call.respond(HttpStatusCode.ServiceUnavailable)
                    }
                    call.respond(HttpStatusCode.Created)
                }

            delete(
                "/api/admin/users/{email}",
                {
                    description = "Delete a user account"
                    request { pathParameter<String>("email") { description = "Account email" } }
                    response {
                        code(HttpStatusCode.NoContent) {}
                        code(HttpStatusCode.NotFound) { description = "User not found" }
                        code(HttpStatusCode.ServiceUnavailable) { description = "No auth backend" }
                    }
                    requireAdminDocs()
                }) {
                    if (!requireAdmin()) return@delete
                    val email =
                        call.parameters["email"]
                            ?: return@delete call.respond(HttpStatusCode.BadRequest)
                    when {
                        localAuth != null ->
                            runCatching { localAuth.deleteUser(email) }
                                .onFailure {
                                    return@delete call.respond(HttpStatusCode.NotFound)
                                }
                        noAuthAccountStore != null -> noAuthAccountStore.delete(email)
                        else -> return@delete call.respond(HttpStatusCode.ServiceUnavailable)
                    }
                    call.respond(HttpStatusCode.NoContent)
                }

            put(
                "/api/admin/users/{email}",
                {
                    description = "Update a local user's display name/groups"
                    request {
                        pathParameter<String>("email") { description = "Account email" }
                        body<UpdateUserRequest>()
                    }
                    response {
                        code(HttpStatusCode.NoContent) {}
                        code(HttpStatusCode.NotFound) { description = "User not found" }
                        code(HttpStatusCode.ServiceUnavailable) {
                            description = "Local auth not enabled"
                        }
                    }
                    requireAdminDocs()
                }) {
                    if (!requireAdmin()) return@put
                    val auth =
                        localAuth ?: return@put call.respond(HttpStatusCode.ServiceUnavailable)
                    val email =
                        call.parameters["email"]
                            ?: return@put call.respond(HttpStatusCode.BadRequest)
                    val body =
                        runCatching { Json.parseToJsonElement(call.receiveText()).jsonObject }
                            .getOrNull() ?: return@put call.respond(HttpStatusCode.BadRequest)
                    val displayName = body["displayName"]?.jsonPrimitive?.content
                    val groups = body["groups"]?.jsonArray?.map { it.jsonPrimitive.content }
                    runCatching { auth.updateUser(email, displayName, groups) }
                        .onFailure {
                            return@put call.respond(HttpStatusCode.NotFound)
                        }
                    call.respond(HttpStatusCode.NoContent)
                }

            // ── Players ──────────────────────────────────────────────────────
            get(
                "/api/admin/players",
                {
                    description = "All player names"
                    response {
                        code(HttpStatusCode.OK) { body<List<String>>() }
                        code(HttpStatusCode.ServiceUnavailable) {
                            description = "No persistence backend"
                        }
                    }
                    requireAdminDocs()
                }) {
                    if (!requireAdmin()) return@get
                    val p =
                        persistence ?: return@get call.respond(HttpStatusCode.ServiceUnavailable)
                    val names = p.listPlayers()
                    call.respondText(
                        adminJson.encodeToString(ListSerializer(String.serializer()), names),
                        ContentType.Application.Json)
                }

            get(
                "/api/admin/players/{name}",
                {
                    description = "Full player file (state, keybindings, RPG data)"
                    request { pathParameter<String>("name") { description = "Player name" } }
                    response {
                        code(HttpStatusCode.OK) { body<PlayerFile>() }
                        code(HttpStatusCode.NotFound) { description = "Player not found" }
                        code(HttpStatusCode.ServiceUnavailable) {
                            description = "No persistence backend"
                        }
                    }
                    requireAdminDocs()
                }) {
                    if (!requireAdmin()) return@get
                    val p =
                        persistence ?: return@get call.respond(HttpStatusCode.ServiceUnavailable)
                    val name =
                        call.parameters["name"]
                            ?: return@get call.respond(HttpStatusCode.BadRequest)
                    val file =
                        p.loadPlayerFile(name) ?: return@get call.respond(HttpStatusCode.NotFound)
                    call.respondText(
                        adminJson.encodeToString(PlayerFile.serializer(), file),
                        ContentType.Application.Json)
                }

            put(
                "/api/admin/players/{name}/keybindings",
                {
                    description = "Overwrite a player's saved key bindings"
                    request {
                        pathParameter<String>("name") { description = "Player name" }
                        body<Map<String, List<String>>>()
                    }
                    response {
                        code(HttpStatusCode.NoContent) {}
                        code(HttpStatusCode.ServiceUnavailable) {
                            description = "No persistence backend"
                        }
                    }
                    requireAdminDocs()
                }) {
                    if (!requireAdmin()) return@put
                    val p =
                        persistence ?: return@put call.respond(HttpStatusCode.ServiceUnavailable)
                    val name =
                        call.parameters["name"]
                            ?: return@put call.respond(HttpStatusCode.BadRequest)
                    val body =
                        runCatching { Json.parseToJsonElement(call.receiveText()).jsonObject }
                            .getOrNull() ?: return@put call.respond(HttpStatusCode.BadRequest)
                    val bindings =
                        body.entries.associate { (k, v) ->
                            k to v.jsonArray.map { it.jsonPrimitive.content }
                        }
                    p.savePlayerKeyBindings(name, bindings)
                    call.respond(HttpStatusCode.NoContent)
                }

            put(
                "/api/admin/players/{name}/preferences",
                {
                    description =
                        "Partially update a player's preferences (only given fields change)"
                    request {
                        pathParameter<String>("name") { description = "Player name" }
                        body<UpdatePlayerPreferencesRequest>()
                    }
                    response {
                        code(HttpStatusCode.NoContent) {}
                        code(HttpStatusCode.NotFound) { description = "Player not found" }
                        code(HttpStatusCode.ServiceUnavailable) {
                            description = "No persistence backend"
                        }
                    }
                    requireAdminDocs()
                }) {
                    if (!requireAdmin()) return@put
                    val p =
                        persistence ?: return@put call.respond(HttpStatusCode.ServiceUnavailable)
                    val name =
                        call.parameters["name"]
                            ?: return@put call.respond(HttpStatusCode.BadRequest)
                    val existing =
                        p.loadPlayerFile(name) ?: return@put call.respond(HttpStatusCode.NotFound)
                    val body =
                        runCatching { Json.parseToJsonElement(call.receiveText()).jsonObject }
                            .getOrNull() ?: return@put call.respond(HttpStatusCode.BadRequest)
                    fun str(key: String) = body[key]?.jsonPrimitive?.content
                    fun bool(key: String) =
                        body[key]?.jsonPrimitive?.content?.toBooleanStrictOrNull()
                    fun int(key: String) = body[key]?.jsonPrimitive?.content?.toIntOrNull()
                    val updated =
                        existing.state.copy(
                            skin = str("skin") ?: existing.state.skin,
                            language = str("language") ?: existing.state.language,
                            fieldOfView = int("fieldOfView") ?: existing.state.fieldOfView,
                            shadersEnabled =
                                bool("shadersEnabled") ?: existing.state.shadersEnabled,
                            animatedFavicon =
                                bool("animatedFavicon") ?: existing.state.animatedFavicon,
                            godMode = bool("godMode") ?: existing.state.godMode,
                            lightBoostEnabled =
                                bool("lightBoostEnabled") ?: existing.state.lightBoostEnabled,
                        )
                    p.savePlayerState(name, updated)
                    call.respond(HttpStatusCode.NoContent)
                }

            post(
                "/api/admin/players/{name}/rename",
                {
                    description = "Rename a player"
                    request {
                        pathParameter<String>("name") { description = "Current player name" }
                        body<RenamePlayerRequest>()
                    }
                    response {
                        code(HttpStatusCode.NoContent) {}
                        code(HttpStatusCode.BadRequest) { description = "Missing newName" }
                        code(HttpStatusCode.ServiceUnavailable) {
                            description = "No persistence backend"
                        }
                    }
                    requireAdminDocs()
                }) {
                    if (!requireAdmin()) return@post
                    val p =
                        persistence ?: return@post call.respond(HttpStatusCode.ServiceUnavailable)
                    val name =
                        call.parameters["name"]
                            ?: return@post call.respond(HttpStatusCode.BadRequest)
                    val body =
                        runCatching { Json.parseToJsonElement(call.receiveText()).jsonObject }
                            .getOrNull() ?: return@post call.respond(HttpStatusCode.BadRequest)
                    val newName =
                        body["newName"]?.jsonPrimitive?.content
                            ?: return@post call.respond(HttpStatusCode.BadRequest)
                    p.renamePlayer(name, newName)
                    call.respond(HttpStatusCode.NoContent)
                }

            put(
                "/api/admin/players/{name}/rpg",
                {
                    description = "Partially update a player's RPG class/base stats"
                    request {
                        pathParameter<String>("name") { description = "Player name" }
                        body<UpdatePlayerRpgRequest>()
                    }
                    response {
                        code(HttpStatusCode.NoContent) {}
                        code(HttpStatusCode.NotFound) { description = "Player not found" }
                        code(HttpStatusCode.BadRequest) {
                            description = "Player has no RPG character"
                        }
                        code(HttpStatusCode.ServiceUnavailable) {
                            description = "No persistence backend"
                        }
                    }
                    requireAdminDocs()
                }) {
                    if (!requireAdmin()) return@put
                    val p =
                        persistence ?: return@put call.respond(HttpStatusCode.ServiceUnavailable)
                    val name =
                        call.parameters["name"]
                            ?: return@put call.respond(HttpStatusCode.BadRequest)
                    val existing =
                        p.loadPlayerFile(name) ?: return@put call.respond(HttpStatusCode.NotFound)
                    val body =
                        runCatching { Json.parseToJsonElement(call.receiveText()).jsonObject }
                            .getOrNull() ?: return@put call.respond(HttpStatusCode.BadRequest)
                    val cd =
                        existing.state.characterData
                            ?: return@put call.respond(HttpStatusCode.BadRequest)
                    fun int(key: String) = body[key]?.jsonPrimitive?.content?.toIntOrNull()
                    val characterClass =
                        body["characterClass"]?.jsonPrimitive?.content?.let {
                            runCatching { CharacterClass.valueOf(it) }.getOrNull()
                        } ?: cd.characterClass
                    val updatedCd =
                        cd.copy(
                            characterClass = characterClass,
                            baseStats =
                                cd.baseStats.copy(
                                    str = int("str") ?: cd.baseStats.str,
                                    dex = int("dex") ?: cd.baseStats.dex,
                                    intel = int("intel") ?: cd.baseStats.intel,
                                    wis = int("wis") ?: cd.baseStats.wis,
                                    con = int("con") ?: cd.baseStats.con,
                                    cha = int("cha") ?: cd.baseStats.cha,
                                ),
                        )
                    p.savePlayerState(name, existing.state.copy(characterData = updatedCd))
                    call.respond(HttpStatusCode.NoContent)
                }

            // ── Worlds ───────────────────────────────────────────────────────
            get(
                "/api/admin/worlds",
                {
                    description = "All worlds on disk, with stats"
                    response { code(HttpStatusCode.OK) { body<List<WorldStatsDto>>() } }
                    requireAdminDocs()
                }) {
                    if (!requireAdmin()) return@get
                    val worlds = listWorlds()
                    call.respondText(
                        adminJson.encodeToString(
                            ListSerializer(WorldStatsDto.serializer()), worlds),
                        ContentType.Application.Json)
                }

            post(
                "/api/admin/worlds",
                {
                    description = "Create a new world"
                    request { body<CreateWorldRequest>() }
                    response {
                        code(HttpStatusCode.Created) { body<WorldStatsDto>() }
                        code(HttpStatusCode.BadRequest) { description = "Missing or invalid name" }
                        code(HttpStatusCode.Conflict) { description = "World already exists" }
                    }
                    requireAdminDocs()
                }) {
                    if (!requireAdmin()) return@post
                    val body =
                        runCatching { Json.parseToJsonElement(call.receiveText()).jsonObject }
                            .getOrNull() ?: return@post call.respond(HttpStatusCode.BadRequest)
                    val name =
                        body["name"]?.jsonPrimitive?.content?.trim()
                            ?: return@post call.respond(HttpStatusCode.BadRequest)
                    if (!name.matches(Regex("[a-zA-Z0-9_-]+")))
                        return@post call.respond(HttpStatusCode.BadRequest)
                    val seed = body["seed"]?.jsonPrimitive?.content?.toLongOrNull() ?: 42L
                    val worldDir = worldsDir.resolve(name)
                    if (worldDir.exists()) return@post call.respond(HttpStatusCode.Conflict)
                    val worldPersistence = WorldPersistence(worldDir)
                    worldPersistence.saveMetadata(
                        WorldMetadata(
                            seed = seed,
                            generator = "procedural",
                            createdAt = java.time.Instant.now().toString()))
                    call.respondText(
                        adminJson.encodeToString(
                            WorldStatsDto.serializer(),
                            WorldStatsDto(
                                name = name,
                                seed = seed,
                                generator = "procedural",
                                createdAt = java.time.Instant.now().toString(),
                                chunkCount = 0,
                                playerCount = 0,
                                isActive = name == activeWorldName,
                            )),
                        ContentType.Application.Json,
                        HttpStatusCode.Created)
                }

            // ── Config files ─────────────────────────────────────────────────
            get(
                "/api/admin/configs",
                {
                    description = "Names of all whitelisted editable YAML config files"
                    response { code(HttpStatusCode.OK) { body<List<String>>() } }
                    requireAdminDocs()
                }) {
                    if (!requireAdmin()) return@get
                    val files =
                        if (configDir.exists()) {
                            configDir
                                .listDirectoryEntries("*.yaml")
                                .filter {
                                    !it.isDirectory() &&
                                        it.nameWithoutExtension + ".yaml" in configFileWhitelist
                                }
                                .map { it.fileName.toString() }
                                .sorted()
                        } else emptyList()
                    val authDir = configDir.resolve("auth")
                    val authFiles =
                        if (authDir.exists()) {
                            authDir
                                .listDirectoryEntries("*.yaml")
                                .filter { !it.isDirectory() }
                                .map { "auth/" + it.fileName.toString() }
                                .sorted()
                        } else emptyList()
                    val all = (files + authFiles).sorted()
                    call.respondText(
                        adminJson.encodeToString(ListSerializer(String.serializer()), all),
                        ContentType.Application.Json)
                }

            get(
                "/api/admin/configs/{filename...}",
                {
                    description = "Raw YAML content of a whitelisted config file"
                    request {
                        pathParameter<String>("filename") { description = "Config file name" }
                    }
                    response {
                        code(HttpStatusCode.OK) {
                            body<String>() { mediaTypes(ContentType.Text.Plain) }
                        }
                        code(HttpStatusCode.Forbidden) {
                            description = "File not in the editable whitelist"
                        }
                        code(HttpStatusCode.NotFound) { description = "File does not exist" }
                    }
                    requireAdminDocs()
                }) {
                    if (!requireAdmin()) return@get
                    val filename =
                        call.parameters.getAll("filename")?.joinToString("/")
                            ?: return@get call.respond(HttpStatusCode.BadRequest)
                    if (!isAllowedConfigFile(filename))
                        return@get call.respond(HttpStatusCode.Forbidden)
                    val file = configDir.resolve(filename)
                    if (!file.exists()) return@get call.respond(HttpStatusCode.NotFound)
                    call.respondText(file.readText(), ContentType.Text.Plain)
                }

            put(
                "/api/admin/configs/{filename...}",
                {
                    description = "Overwrite a whitelisted config file's raw YAML content"
                    request {
                        pathParameter<String>("filename") { description = "Config file name" }
                        body<String>() { mediaTypes(ContentType.Text.Plain) }
                    }
                    response {
                        code(HttpStatusCode.NoContent) {}
                        code(HttpStatusCode.Forbidden) {
                            description = "File not in the editable whitelist"
                        }
                    }
                    requireAdminDocs()
                }) {
                    if (!requireAdmin()) return@put
                    val filename =
                        call.parameters.getAll("filename")?.joinToString("/")
                            ?: return@put call.respond(HttpStatusCode.BadRequest)
                    if (!isAllowedConfigFile(filename))
                        return@put call.respond(HttpStatusCode.Forbidden)
                    val file = configDir.resolve(filename)
                    val content = call.receiveText()
                    file.writeText(content)
                    call.respond(HttpStatusCode.NoContent)
                }

            // ── Classes ──────────────────────────────────────────────────────
            get(
                "/api/admin/classes",
                {
                    description = "RPG class definitions, keyed by class name"
                    response {
                        code(HttpStatusCode.OK) { body<Map<String, ClassDefinitionEntry>>() }
                    }
                    requireAdminDocs()
                }) {
                    if (!requireAdmin()) return@get
                    call.respondText(
                        adminJson.encodeToString(
                            MapSerializer(String.serializer(), ClassDefinitionEntry.serializer()),
                            gameLoop.classRegistry,
                        ),
                        ContentType.Application.Json)
                }

            get(
                "/api/admin/skills",
                {
                    description = "All attack and spell ids"
                    response { code(HttpStatusCode.OK) { body<SkillsResponse>() } }
                    requireAdminDocs()
                }) {
                    if (!requireAdmin()) return@get
                    val payload =
                        adminJson.encodeToString(
                            kotlinx.serialization.json.buildJsonObject {
                                put(
                                    "attacks",
                                    kotlinx.serialization.json.buildJsonArray {
                                        gameLoop.attackRegistry.keys.sorted().forEach {
                                            add(kotlinx.serialization.json.JsonPrimitive(it))
                                        }
                                    })
                                put(
                                    "spells",
                                    kotlinx.serialization.json.buildJsonArray {
                                        gameLoop.spellRegistry.keys.sorted().forEach {
                                            add(kotlinx.serialization.json.JsonPrimitive(it))
                                        }
                                    })
                            })
                    call.respondText(payload, ContentType.Application.Json)
                }

            // ── NPCs ─────────────────────────────────────────────────────────
            get(
                "/api/admin/npcs",
                {
                    description = "Live NPC instances with full animal/combat state"
                    response { code(HttpStatusCode.OK) { body<List<NpcAdminDto>>() } }
                    requireAdminDocs()
                }) {
                    if (!requireAdmin()) return@get
                    val dtos =
                        gameLoop.getNpcInstances().map { npc ->
                            val ad = npc.animalData
                            val maxHp = npc.maxHp
                            val zoneX =
                                Math.floorDiv(
                                    npc.state.pos.x.toInt(), NpcConstants.live.npcZoneSize)
                            val zoneZ =
                                Math.floorDiv(
                                    npc.state.pos.z.toInt(), NpcConstants.live.npcZoneSize)
                            NpcAdminDto(
                                id = npc.state.id,
                                name = npc.state.name,
                                type = npc.state.type,
                                level = npc.instanceLevel,
                                xp = npc.xp,
                                gender = ad?.gender?.name,
                                currentHp = npc.currentHp,
                                maxHp = maxHp,
                                isDead = npc.isDead,
                                aggroMode = npc.definition.aggroMode.name,
                                tier = npc.definition.tier.name,
                                x = npc.state.pos.x,
                                y = npc.state.pos.y,
                                z = npc.state.pos.z,
                                yaw = npc.state.yaw,
                                zone = "$zoneX,$zoneZ",
                                parentIds = ad?.parentIds?.toList() ?: emptyList(),
                                skills =
                                    npc.definition.attacks.map { "${it.attackId} lv${it.level}" },
                                ageGameDays = ad?.ageGameDays,
                                hunger = ad?.hunger,
                                gestationRemainingDays = ad?.gestationRemainingDays,
                                lastReproductionDay = ad?.lastReproductionDay,
                                motherLevel = ad?.motherLevel,
                                animalStats = ad?.stats,
                            )
                        }
                    call.respondText(
                        adminJson.encodeToString(ListSerializer(NpcAdminDto.serializer()), dtos),
                        ContentType.Application.Json)
                }

            // ── Blocks ───────────────────────────────────────────────────────
            get(
                "/api/admin/blocks",
                {
                    description = "All registered block definitions"
                    response { code(HttpStatusCode.OK) { body<List<BlockInfo>>() } }
                    requireAdminDocs()
                }) {
                    if (!requireAdmin()) return@get
                    val blocks =
                        BlockRegistry.all().map { type ->
                            val def = BlockRegistry.get(type)
                            BlockInfo(
                                name = type.id,
                                hardness = def.hardness,
                                solid = def.solid,
                                transparent = def.transparent,
                                minimapColor = def.minimapColor,
                                modelElement = def.modelElement,
                                gltfModel = def.gltfModel,
                                liquid = def.liquid,
                                rotatable = def.rotatable,
                                hasStuds = def.hasStuds,
                                brickSize = def.brickSize,
                                plainColorable = def.plainColorable,
                                isCubic = def.isCubic,
                            )
                        }
                    call.respondText(
                        Json.encodeToString(ListSerializer(BlockInfo.serializer()), blocks),
                        ContentType.Application.Json)
                }

            get(
                "/api/admin/plain-colors",
                {
                    description = "All registered plain paint colors"
                    response { code(HttpStatusCode.OK) { body<List<PlainColorInfo>>() } }
                    requireAdminDocs()
                }) {
                    if (!requireAdmin()) return@get
                    val colors = PlainColorRegistry.all().map { PlainColorInfo(it.name, it.hex()) }
                    call.respondText(
                        Json.encodeToString(ListSerializer(PlainColorInfo.serializer()), colors),
                        ContentType.Application.Json)
                }

            // ── Instances ────────────────────────────────────────────────────
            get(
                "/api/admin/chunks/discovered",
                {
                    description = "All chunk coordinates generated so far (in-memory ∪ persisted)"
                    response { code(HttpStatusCode.OK) { body<List<ChunkPos>>() } }
                    requireAdminDocs()
                }) {
                    if (!requireAdmin()) return@get
                    call.respondText(
                        Json.encodeToString(
                            ListSerializer(ChunkPos.serializer()), generatedChunks().toList()),
                        ContentType.Application.Json)
                }

            get(
                "/api/admin/instances",
                {
                    description = "All instance zones"
                    response { code(HttpStatusCode.OK) { body<List<InstanceZone>>() } }
                    requireAdminDocs()
                }) {
                    if (!requireAdmin()) return@get
                    call.respondText(
                        Json.encodeToString(
                            ListSerializer(InstanceZone.serializer()), gameLoop.instances().all()),
                        ContentType.Application.Json)
                }

            get(
                "/api/admin/instances/{id}",
                {
                    description = "A single instance zone"
                    request { pathParameter<String>("id") { description = "Instance id" } }
                    response {
                        code(HttpStatusCode.OK) { body<InstanceZone>() }
                        code(HttpStatusCode.NotFound) { description = "Instance not found" }
                    }
                    requireAdminDocs()
                }) {
                    if (!requireAdmin()) return@get
                    val id =
                        call.parameters["id"] ?: return@get call.respond(HttpStatusCode.BadRequest)
                    val zone =
                        gameLoop.instances().get(id)
                            ?: return@get call.respond(HttpStatusCode.NotFound)
                    call.respondText(
                        Json.encodeToString(InstanceZone.serializer(), zone),
                        ContentType.Application.Json)
                }

            post(
                "/api/admin/instances",
                {
                    description = "Create an instance zone covering already-generated chunks"
                    request { body<InstanceCreateRequest>() }
                    response {
                        code(HttpStatusCode.OK) { body<InstanceZone>() }
                        code(HttpStatusCode.BadRequest) {
                            description = "Invalid zone, or chunks not yet generated"
                        }
                    }
                    requireAdminDocs()
                }) {
                    if (!requireAdmin()) return@post
                    val body = Json.decodeFromString<InstanceCreateRequest>(call.receiveText())
                    if (body.name.isBlank() || body.chunks.isEmpty() || body.yMin >= body.yMax) {
                        return@post call.respond(HttpStatusCode.BadRequest, "Invalid zone")
                    }
                    val discovered = generatedChunks()
                    if (body.chunks.any { it !in discovered }) {
                        return@post call.respond(
                            HttpStatusCode.BadRequest,
                            "Zone must only cover already-generated chunks")
                    }
                    val zone =
                        gameLoop
                            .instances()
                            .create(
                                name = body.name,
                                yMin = body.yMin,
                                yMax = body.yMax,
                                chunks = body.chunks.toSet(),
                                ownerName = "admin")
                    gameLoop.broadcastInstanceZonesSync()
                    call.respondText(
                        Json.encodeToString(InstanceZone.serializer(), zone),
                        ContentType.Application.Json)
                }

            put(
                "/api/admin/instances/{id}",
                {
                    description = "Rename an instance zone"
                    request {
                        pathParameter<String>("id") { description = "Instance id" }
                        body<InstanceRenameRequest>()
                    }
                    response {
                        code(HttpStatusCode.OK) { body<InstanceZone>() }
                        code(HttpStatusCode.NotFound) { description = "Instance not found" }
                    }
                    requireAdminDocs()
                }) {
                    if (!requireAdmin()) return@put
                    val id =
                        call.parameters["id"] ?: return@put call.respond(HttpStatusCode.BadRequest)
                    val body = Json.decodeFromString<InstanceRenameRequest>(call.receiveText())
                    val zone =
                        gameLoop.instances().rename(id, body.name)
                            ?: return@put call.respond(HttpStatusCode.NotFound)
                    gameLoop.broadcastInstanceZonesSync()
                    call.respondText(
                        Json.encodeToString(InstanceZone.serializer(), zone),
                        ContentType.Application.Json)
                }

            put(
                "/api/admin/instances/{id}/bounds",
                {
                    description = "Update an instance zone's Y bounds"
                    request {
                        pathParameter<String>("id") { description = "Instance id" }
                        body<InstanceBoundsRequest>()
                    }
                    response {
                        code(HttpStatusCode.OK) { body<InstanceZone>() }
                        code(HttpStatusCode.BadRequest) {
                            description = "yMin must be less than yMax"
                        }
                        code(HttpStatusCode.NotFound) { description = "Instance not found" }
                    }
                    requireAdminDocs()
                }) {
                    if (!requireAdmin()) return@put
                    val id =
                        call.parameters["id"] ?: return@put call.respond(HttpStatusCode.BadRequest)
                    val body = Json.decodeFromString<InstanceBoundsRequest>(call.receiveText())
                    if (body.yMin >= body.yMax) {
                        return@put call.respond(
                            HttpStatusCode.BadRequest, "yMin must be less than yMax")
                    }
                    val zone =
                        gameLoop.instances().updateBounds(id, body.yMin, body.yMax)
                            ?: return@put call.respond(HttpStatusCode.NotFound)
                    gameLoop.broadcastInstanceZonesSync()
                    call.respondText(
                        Json.encodeToString(InstanceZone.serializer(), zone),
                        ContentType.Application.Json)
                }

            put(
                "/api/admin/instances/{id}/enabled",
                {
                    description = "Enable/disable an instance zone"
                    request {
                        pathParameter<String>("id") { description = "Instance id" }
                        body<InstanceEnabledRequest>()
                    }
                    response {
                        code(HttpStatusCode.OK) { body<InstanceZone>() }
                        code(HttpStatusCode.NotFound) { description = "Instance not found" }
                    }
                    requireAdminDocs()
                }) {
                    if (!requireAdmin()) return@put
                    val id =
                        call.parameters["id"] ?: return@put call.respond(HttpStatusCode.BadRequest)
                    val body = Json.decodeFromString<InstanceEnabledRequest>(call.receiveText())
                    val zone =
                        gameLoop.instances().setEnabled(id, body.enabled)
                            ?: return@put call.respond(HttpStatusCode.NotFound)
                    gameLoop.broadcastInstanceZonesSync()
                    call.respondText(
                        Json.encodeToString(InstanceZone.serializer(), zone),
                        ContentType.Application.Json)
                }

            put(
                "/api/admin/instances/{id}/chunks",
                {
                    description = "Update the set of chunks covered by an instance zone"
                    request {
                        pathParameter<String>("id") { description = "Instance id" }
                        body<InstanceChunksRequest>()
                    }
                    response {
                        code(HttpStatusCode.OK) { body<InstanceZone>() }
                        code(HttpStatusCode.BadRequest) {
                            description = "Empty chunk set, or chunks not yet generated"
                        }
                        code(HttpStatusCode.NotFound) { description = "Instance not found" }
                    }
                    requireAdminDocs()
                }) {
                    if (!requireAdmin()) return@put
                    val id =
                        call.parameters["id"] ?: return@put call.respond(HttpStatusCode.BadRequest)
                    val body = Json.decodeFromString<InstanceChunksRequest>(call.receiveText())
                    if (body.chunks.isEmpty()) {
                        return@put call.respond(
                            HttpStatusCode.BadRequest, "Zone must have at least one chunk")
                    }
                    val discovered = generatedChunks()
                    if (body.chunks.any { it !in discovered }) {
                        return@put call.respond(
                            HttpStatusCode.BadRequest,
                            "Zone must only cover already-generated chunks")
                    }
                    val zone =
                        gameLoop.instances().updateChunks(id, body.chunks.toSet())
                            ?: return@put call.respond(HttpStatusCode.NotFound)
                    gameLoop.broadcastInstanceZonesSync()
                    call.respondText(
                        Json.encodeToString(InstanceZone.serializer(), zone),
                        ContentType.Application.Json)
                }

            put(
                "/api/admin/instances/{id}/layout",
                {
                    description = "Update an instance zone's clip planes and shortcut bar layout"
                    request {
                        pathParameter<String>("id") { description = "Instance id" }
                        body<InstanceLayoutRequest>()
                    }
                    response {
                        code(HttpStatusCode.OK) { body<InstanceZone>() }
                        code(HttpStatusCode.NotFound) { description = "Instance not found" }
                    }
                    requireAdminDocs()
                }) {
                    if (!requireAdmin()) return@put
                    val id =
                        call.parameters["id"] ?: return@put call.respond(HttpStatusCode.BadRequest)
                    val body = Json.decodeFromString<InstanceLayoutRequest>(call.receiveText())
                    val zone =
                        gameLoop
                            .instances()
                            .updateLayout(id, body.clipPlanes, body.shortcutBarPages)
                            ?: return@put call.respond(HttpStatusCode.NotFound)
                    call.respondText(
                        Json.encodeToString(InstanceZone.serializer(), zone),
                        ContentType.Application.Json)
                }

            delete(
                "/api/admin/instances/{id}",
                {
                    description = "Delete an instance zone"
                    request { pathParameter<String>("id") { description = "Instance id" } }
                    response {
                        code(HttpStatusCode.NoContent) {}
                        code(HttpStatusCode.NotFound) { description = "Instance not found" }
                    }
                    requireAdminDocs()
                }) {
                    if (!requireAdmin()) return@delete
                    val id =
                        call.parameters["id"]
                            ?: return@delete call.respond(HttpStatusCode.BadRequest)
                    if (!gameLoop.instances().delete(id)) {
                        return@delete call.respond(HttpStatusCode.NotFound)
                    }
                    gameLoop.broadcastInstanceZonesSync()
                    call.respond(HttpStatusCode.NoContent)
                }

            get(
                "/api/admin/instances/{id}/blocks",
                {
                    description =
                        "Non-air blocks in an instance zone, streamed as newline-delimited JSON " +
                            "(application/x-ndjson, one InstanceBlockDto per line), capped at $MAX_STREAMED_BLOCKS"
                    request {
                        pathParameter<String>("id") { description = "Instance id" }
                        queryParameter<Int>("cx") {
                            description = "Restrict to one chunk column (with cz)"
                            required = false
                        }
                        queryParameter<Int>("cz") {
                            description = "Restrict to one chunk column (with cx)"
                            required = false
                        }
                    }
                    response {
                        code(HttpStatusCode.OK) {
                            body<InstanceBlockDto>() {
                                mediaTypes(ContentType.parse("application/x-ndjson"))
                            }
                        }
                        code(HttpStatusCode.NotFound) { description = "Instance not found" }
                    }
                    requireAdminDocs()
                }) {
                    if (!requireAdmin()) return@get
                    val id =
                        call.parameters["id"] ?: return@get call.respond(HttpStatusCode.BadRequest)
                    val zone =
                        gameLoop.instances().get(id)
                            ?: return@get call.respond(HttpStatusCode.NotFound)
                    val world = gameLoop.getWorldState()
                    val cx = call.request.queryParameters["cx"]?.toIntOrNull()
                    val cz = call.request.queryParameters["cz"]?.toIntOrNull()
                    // Streamed as newline-delimited JSON, one block per line. With cx/cz given,
                    // restricted to that single chunk column — the editor loads/unloads chunks as
                    // the camera moves instead of pulling the whole zone at once. Without them,
                    // falls back to the whole-zone Y-layer-major order (a single in-memory
                    // list/JSON string for a large zone previously exhausted the heap). Capped
                    // either way so the browser isn't asked to render more cubes than it reasonably
                    // can.
                    call.respondTextWriter(ContentType.parse("application/x-ndjson")) {
                        var streamed = 0
                        var checked = 0
                        val chunkSize = WorldConstants.CHUNK_SIZE
                        val columns =
                            if (cx != null && cz != null)
                                zone.blockColumnsForChunk(chunkSize, cx, cz)
                            else zone.blockColumnsByLayer(chunkSize)
                        columns@ for ((x, y, z) in columns) {
                            if (streamed >= MAX_STREAMED_BLOCKS) break@columns
                            val type = world.getBlock(x, y, z)
                            if (type != BlockType.AIR) {
                                write(
                                    Json.encodeToString(
                                        InstanceBlockDto.serializer(),
                                        InstanceBlockDto(
                                            x = x,
                                            y = y,
                                            z = z,
                                            type = type.id,
                                            state = world.getBlockState(x, y, z))))
                                write("\n")
                                streamed++
                            }
                            checked++
                            // Flushed periodically (rather than per coordinate, which was the
                            // per-chunk cadence before block iteration went Y-layer-major) so the
                            // browser starts rendering before the whole zone is scanned.
                            if (checked % 4096 == 0) flush()
                        }
                        flush()
                    }
                }

            // ── Scenes ───────────────────────────────────────────────────────
            // A bounded X/Y/Z raw block-structure editor buffer — NOT tied to the live
            // world/chunks (unlike an instance zone, which carves a region out of the
            // persistent world). See Scene.kt / SceneRegistry.kt.
            get(
                "/api/admin/scenes",
                {
                    description = "All scenes (bounded off-world block-structure buffers)"
                    response { code(HttpStatusCode.OK) { body<List<SceneDto>>() } }
                    requireAdminDocs()
                }) {
                    if (!requireAdmin()) return@get
                    call.respondText(
                        adminJson.encodeToString(
                            ListSerializer(SceneDto.serializer()),
                            gameLoop.scenes().all().map { it.toDto() }),
                        ContentType.Application.Json)
                }

            get(
                "/api/admin/scenes/{id}",
                {
                    description = "A single scene's metadata"
                    request { pathParameter<String>("id") { description = "Scene id" } }
                    response {
                        code(HttpStatusCode.OK) { body<SceneDto>() }
                        code(HttpStatusCode.NotFound) { description = "Scene not found" }
                    }
                    requireAdminDocs()
                }) {
                    if (!requireAdmin()) return@get
                    val id =
                        call.parameters["id"] ?: return@get call.respond(HttpStatusCode.BadRequest)
                    val scene =
                        gameLoop.scenes().get(id)
                            ?: return@get call.respond(HttpStatusCode.NotFound)
                    call.respondText(
                        adminJson.encodeToString(SceneDto.serializer(), scene.toDto()),
                        ContentType.Application.Json)
                }

            post(
                "/api/admin/scenes",
                {
                    description = "Create a scene"
                    request { body<SceneCreateRequest>() }
                    response {
                        code(HttpStatusCode.Created) { body<SceneDto>() }
                        code(HttpStatusCode.BadRequest) {
                            description = "Invalid name or dimensions"
                        }
                    }
                    requireAdminDocs()
                }) {
                    if (!requireAdmin()) return@post
                    val body = Json.decodeFromString<SceneCreateRequest>(call.receiveText())
                    if (body.name.isBlank() ||
                        body.width <= 0 ||
                        body.height <= 0 ||
                        body.depth <= 0) {
                        return@post call.respond(HttpStatusCode.BadRequest, "Invalid scene")
                    }
                    val scene =
                        gameLoop
                            .scenes()
                            .create(
                                name = body.name,
                                width = body.width,
                                height = body.height,
                                depth = body.depth,
                                ownerName = "admin")
                    call.respond(
                        HttpStatusCode.Created,
                        adminJson.encodeToString(SceneDto.serializer(), scene.toDto()))
                }

            post(
                "/api/admin/scenes/{id}/duplicate",
                {
                    description = "Duplicate a scene (copies name, dimensions, and blocks)"
                    request { pathParameter<String>("id") { description = "Scene id" } }
                    response {
                        code(HttpStatusCode.Created) { body<SceneDto>() }
                        code(HttpStatusCode.NotFound) { description = "Scene not found" }
                    }
                    requireAdminDocs()
                }) {
                    if (!requireAdmin()) return@post
                    val id =
                        call.parameters["id"] ?: return@post call.respond(HttpStatusCode.BadRequest)
                    val scene =
                        gameLoop.scenes().duplicate(id)
                            ?: return@post call.respond(HttpStatusCode.NotFound)
                    call.respond(
                        HttpStatusCode.Created,
                        adminJson.encodeToString(SceneDto.serializer(), scene.toDto()))
                }

            put(
                "/api/admin/scenes/{id}",
                {
                    description = "Rename a scene"
                    request {
                        pathParameter<String>("id") { description = "Scene id" }
                        body<SceneRenameRequest>()
                    }
                    response {
                        code(HttpStatusCode.OK) { body<SceneDto>() }
                        code(HttpStatusCode.NotFound) { description = "Scene not found" }
                    }
                    requireAdminDocs()
                }) {
                    if (!requireAdmin()) return@put
                    val id =
                        call.parameters["id"] ?: return@put call.respond(HttpStatusCode.BadRequest)
                    val body = Json.decodeFromString<SceneRenameRequest>(call.receiveText())
                    val scene =
                        gameLoop.scenes().rename(id, body.name)
                            ?: return@put call.respond(HttpStatusCode.NotFound)
                    call.respondText(
                        adminJson.encodeToString(SceneDto.serializer(), scene.toDto()),
                        ContentType.Application.Json)
                }

            put(
                "/api/admin/scenes/{id}/dimensions",
                {
                    description = "Resize a scene"
                    request {
                        pathParameter<String>("id") { description = "Scene id" }
                        body<SceneDimensionsRequest>()
                    }
                    response {
                        code(HttpStatusCode.OK) { body<SceneDto>() }
                        code(HttpStatusCode.BadRequest) { description = "Invalid dimensions" }
                        code(HttpStatusCode.NotFound) { description = "Scene not found" }
                    }
                    requireAdminDocs()
                }) {
                    if (!requireAdmin()) return@put
                    val id =
                        call.parameters["id"] ?: return@put call.respond(HttpStatusCode.BadRequest)
                    val body = Json.decodeFromString<SceneDimensionsRequest>(call.receiveText())
                    if (body.width <= 0 || body.height <= 0 || body.depth <= 0) {
                        return@put call.respond(HttpStatusCode.BadRequest, "Invalid dimensions")
                    }
                    val scene =
                        gameLoop.scenes().resize(id, body.width, body.height, body.depth)
                            ?: return@put call.respond(HttpStatusCode.NotFound)
                    call.respondText(
                        adminJson.encodeToString(SceneDto.serializer(), scene.toDto()),
                        ContentType.Application.Json)
                }

            put(
                "/api/admin/scenes/{id}/layout",
                {
                    description = "Update a scene's shortcut bar layout"
                    request {
                        pathParameter<String>("id") { description = "Scene id" }
                        body<SceneLayoutRequest>()
                    }
                    response {
                        code(HttpStatusCode.OK) { body<SceneDto>() }
                        code(HttpStatusCode.NotFound) { description = "Scene not found" }
                    }
                    requireAdminDocs()
                }) {
                    if (!requireAdmin()) return@put
                    val id =
                        call.parameters["id"] ?: return@put call.respond(HttpStatusCode.BadRequest)
                    val body = Json.decodeFromString<SceneLayoutRequest>(call.receiveText())
                    val scene =
                        gameLoop.scenes().updateLayout(id, body.shortcutBarPages)
                            ?: return@put call.respond(HttpStatusCode.NotFound)
                    call.respondText(
                        adminJson.encodeToString(SceneDto.serializer(), scene.toDto()),
                        ContentType.Application.Json)
                }

            delete(
                "/api/admin/scenes/{id}",
                {
                    description = "Delete a scene"
                    request { pathParameter<String>("id") { description = "Scene id" } }
                    response {
                        code(HttpStatusCode.NoContent) {}
                        code(HttpStatusCode.NotFound) { description = "Scene not found" }
                    }
                    requireAdminDocs()
                }) {
                    if (!requireAdmin()) return@delete
                    val id =
                        call.parameters["id"]
                            ?: return@delete call.respond(HttpStatusCode.BadRequest)
                    if (!gameLoop.scenes().delete(id)) {
                        return@delete call.respond(HttpStatusCode.NotFound)
                    }
                    call.respond(HttpStatusCode.NoContent)
                }

            // Binary wire format (application/octet-stream), big-endian:
            //   [4 bytes width][4 bytes height][4 bytes depth][blocks: width*height*depth bytes]
            //   [states: width*height*depth bytes]
            // blocks/states use the same wire-index-per-byte convention as chunk buffers
            // (BlockRegistry.wireIndex/byWireIndex) — 0 is always AIR.
            get(
                "/api/admin/scenes/{id}/blocks/raw",
                {
                    description =
                        "Scene block/state buffers as a binary blob: 3×4-byte big-endian " +
                            "dimensions (width,height,depth) followed by the blocks byte array then " +
                            "the states byte array (wire-index-per-byte, 0 = AIR)"
                    request { pathParameter<String>("id") { description = "Scene id" } }
                    response {
                        code(HttpStatusCode.OK) {
                            body<ByteArray>() { mediaTypes(ContentType.Application.OctetStream) }
                        }
                        code(HttpStatusCode.NotFound) { description = "Scene not found" }
                    }
                    requireAdminDocs()
                }) {
                    if (!requireAdmin()) return@get
                    val id =
                        call.parameters["id"] ?: return@get call.respond(HttpStatusCode.BadRequest)
                    val scene =
                        gameLoop.scenes().get(id)
                            ?: return@get call.respond(HttpStatusCode.NotFound)
                    val buffer = ByteArrayOutputStream()
                    DataOutputStream(buffer).use { out ->
                        out.writeInt(scene.width)
                        out.writeInt(scene.height)
                        out.writeInt(scene.depth)
                        out.write(scene.blocks)
                        out.write(scene.states)
                    }
                    call.respondBytes(buffer.toByteArray(), ContentType.Application.OctetStream)
                }

            // ── NPC types ─────────────────────────────────────────────────────
            get(
                "/api/admin/npc-types",
                {
                    description = "NPC type definitions (codex info), keyed by type id"
                    response { code(HttpStatusCode.OK) { body<Map<String, NpcCodexInfo>>() } }
                    requireAdminDocs()
                }) {
                    if (!requireAdmin()) return@get
                    val types =
                        gameLoop.getNpcManager().getDefinitions().mapValues { (_, def) ->
                            NpcCodexInfo(
                                bbmodelFile = def.bbmodelFile,
                                behaviorKey = def.behaviorKey,
                                width = def.width,
                                height = def.height,
                                wanderSpeed = def.wanderSpeed,
                                autoSpawn = def.spawn.autoSpawn,
                            )
                        }
                    call.respondText(
                        Json.encodeToString(
                            MapSerializer(String.serializer(), NpcCodexInfo.serializer()), types),
                        ContentType.Application.Json)
                }

            // ── Items ─────────────────────────────────────────────────────────
            get(
                "/api/admin/items",
                {
                    description = "Item definitions, keyed by item type id"
                    response { code(HttpStatusCode.OK) { body<Map<String, ItemInfo>>() } }
                    requireAdminDocs()
                }) {
                    if (!requireAdmin()) return@get
                    val items =
                        ItemRegistry.keys().associate { type ->
                            val def = ItemRegistry.get(type)
                            type.id to
                                ItemInfo(
                                    buildable = def.buildable,
                                    placesBlock = def.placesBlock?.id,
                                    plainColor = def.plainColor,
                                )
                        }
                    call.respondText(
                        Json.encodeToString(
                            MapSerializer(String.serializer(), ItemInfo.serializer()), items),
                        ContentType.Application.Json)
                }

            // ── Schemas ──────────────────────────────────────────────────────
            get(
                "/api/admin/schemas/{filename}",
                {
                    description =
                        "A JSON Schema file (data/config/schemas/*.schema.json) for the config editor"
                    request {
                        pathParameter<String>("filename") {
                            description = "Schema file name, e.g. server.schema.json"
                        }
                    }
                    response {
                        code(HttpStatusCode.OK) {
                            body<String>() { mediaTypes(ContentType.Application.Json) }
                        }
                        code(HttpStatusCode.Forbidden) {
                            description = "Not a *.schema.json filename"
                        }
                        code(HttpStatusCode.NotFound) { description = "Schema not found" }
                    }
                    requireAdminDocs()
                }) {
                    if (!requireAdmin()) return@get
                    val filename =
                        call.parameters["filename"]
                            ?: return@get call.respond(HttpStatusCode.BadRequest)
                    if (!filename.endsWith(".schema.json") ||
                        filename.contains("/") ||
                        filename.contains(".."))
                        return@get call.respond(HttpStatusCode.Forbidden)
                    val content =
                        AdminController::class
                            .java
                            .classLoader
                            .getResourceAsStream("schemas/$filename")
                            ?.bufferedReader()
                            ?.readText() ?: return@get call.respond(HttpStatusCode.NotFound)
                    call.respondText(content, ContentType.Application.Json)
                }
        }
    }

    private fun listWorlds(): List<WorldStatsDto> {
        if (!worldsDir.exists()) return emptyList()
        return worldsDir
            .toFile()
            .listFiles { f -> f.isDirectory }
            ?.mapNotNull { dir ->
                val wp = WorldPersistence(dir.toPath())
                val meta = wp.loadMetadata() ?: return@mapNotNull null
                val chunkCount =
                    dir.resolve("chunks").listFiles { f -> f.name.endsWith(".mcc.gz") }?.size ?: 0
                val playerCount =
                    dir.resolve("players")
                        .listFiles { f -> f.extension == "yaml" && !f.name.contains("-") }
                        ?.size ?: 0
                WorldStatsDto(
                    name = dir.name,
                    seed = meta.seed,
                    generator = meta.generator,
                    createdAt = meta.createdAt,
                    chunkCount = chunkCount,
                    playerCount = playerCount,
                    isActive = dir.name == activeWorldName,
                )
            }
            ?.sortedWith(compareByDescending<WorldStatsDto> { it.isActive }.thenBy { it.name })
            ?: emptyList()
    }

    private fun String.adminWsJson() = "\"${replace("\\", "\\\\").replace("\"", "\\\"")}\""

    fun registerAdminWs(route: Route) =
        route.webSocket("/api/admin/ws/npcs") {
            if (tokenStore != null) {
                val token = call.request.queryParameters["token"]
                val auth = token?.let { tokenStore.validate(it) }
                if (auth == null || ("*" !in auth.permissions && "admin" !in auth.permissions)) {
                    close(CloseReason(CloseReason.Codes.VIOLATED_POLICY, "Unauthorized"))
                    return@webSocket
                }
            }
            val npcManager = gameLoop.getNpcManager()
            val listener: suspend (String) -> Unit = { json ->
                try {
                    send(json)
                } catch (_: Exception) {}
            }
            val playerListener: suspend (String) -> Unit = { json ->
                try {
                    send(json)
                } catch (_: Exception) {}
            }
            npcManager.addAdminListener(listener)
            gameLoop.addPlayerAdminListener(playerListener)
            try {
                for (npc in gameLoop.getNpcInstances()) {
                    val s = npc.state
                    val maxHp = npc.maxHp
                    send(
                        """{"type":"npcSpawned","id":"${s.id}","name":${s.name.adminWsJson()},"npcType":${s.type.adminWsJson()},"x":${s.pos.x},"y":${s.pos.y},"z":${s.pos.z},"yaw":${s.yaw},"currentHp":${npc.currentHp},"maxHp":$maxHp,"isDead":${npc.isDead}}""")
                }
                for (state in gameLoop.getPlayerStates()) {
                    send(
                        """{"type":"playerJoined","id":"${state.id}","name":${state.name.adminWsJson()},"x":${state.pos.x},"y":${state.pos.y},"z":${state.pos.z},"yaw":${state.orientation.yaw}}""")
                }
                for (frame in incoming) {
                    /* ignore */
                }
            } finally {
                npcManager.removeAdminListener(listener)
                gameLoop.removePlayerAdminListener(playerListener)
            }
        }

    private fun isAllowedConfigFile(filename: String): Boolean {
        if (filename.contains("..")) return false
        val name = filename.removePrefix("auth/")
        return filename in configFileWhitelist ||
            filename.startsWith("auth/") && (name == "users.yaml" || name == "groups.yaml")
    }
}
