package org.micoli.micraft.http

import io.ktor.http.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.server.websocket.*
import io.ktor.websocket.*
import java.io.File
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.attribute.FileTime
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
import org.micoli.micraft.game.world.BlockRegistry
import org.micoli.micraft.game.world.ItemRegistry
import org.micoli.micraft.game.world.PlayerFile
import org.micoli.micraft.game.world.WorldMetadata
import org.micoli.micraft.game.world.WorldPersistence
import org.micoli.micraft.player.rpg.BaseStats
import org.micoli.micraft.player.rpg.CharacterClass
import org.micoli.micraft.protocol.BlockInfo
import org.micoli.micraft.protocol.ItemInfo
import org.micoli.micraft.protocol.NpcCodexInfo

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

    fun register(route: Route) =
        route.apply {
            // ── Static assets ────────────────────────────────────────────────
            get("/admin/{...}") { call.respondFile(File("server/src/main/resources/admin.html")) }
            get("/admin") { call.respondFile(File("server/src/main/resources/admin.html")) }
            get("/admin.js") {
                val f = File("server/src/main/resources/admin.js")
                if (f.exists()) call.respondFile(f) else call.respond(HttpStatusCode.NotFound)
            }
            get("/admin.css") {
                val f = File("server/src/main/resources/admin.css")
                if (f.exists()) call.respondFile(f) else call.respond(HttpStatusCode.NotFound)
            }

            // ── Status ───────────────────────────────────────────────────────
            get("/api/admin/status") {
                if (!requireAdmin()) return@get
                val snapshot = buildStatusSnapshot(gameLoop)
                call.respondText(
                    adminJson.encodeToString(StatusSnapshot.serializer(), snapshot),
                    ContentType.Application.Json)
            }

            post("/api/admin/restart") {
                if (!requireAdmin()) return@post
                val lock = Path.of("run.lock")
                if (!lock.exists()) Files.createFile(lock)
                Files.setLastModifiedTime(lock, FileTime.fromMillis(System.currentTimeMillis()))
                call.respond(HttpStatusCode.NoContent)
            }

            put("/api/admin/gametime") {
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
            get("/api/admin/users") {
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

            post("/api/admin/users") {
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

            delete("/api/admin/users/{email}") {
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

            put("/api/admin/users/{email}") {
                if (!requireAdmin()) return@put
                val auth = localAuth ?: return@put call.respond(HttpStatusCode.ServiceUnavailable)
                val email =
                    call.parameters["email"] ?: return@put call.respond(HttpStatusCode.BadRequest)
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
            get("/api/admin/players") {
                if (!requireAdmin()) return@get
                val p = persistence ?: return@get call.respond(HttpStatusCode.ServiceUnavailable)
                val names = p.listPlayers()
                call.respondText(
                    adminJson.encodeToString(ListSerializer(String.serializer()), names),
                    ContentType.Application.Json)
            }

            get("/api/admin/players/{name}") {
                if (!requireAdmin()) return@get
                val p = persistence ?: return@get call.respond(HttpStatusCode.ServiceUnavailable)
                val name =
                    call.parameters["name"] ?: return@get call.respond(HttpStatusCode.BadRequest)
                val file =
                    p.loadPlayerFile(name) ?: return@get call.respond(HttpStatusCode.NotFound)
                call.respondText(
                    adminJson.encodeToString(PlayerFile.serializer(), file),
                    ContentType.Application.Json)
            }

            put("/api/admin/players/{name}/keybindings") {
                if (!requireAdmin()) return@put
                val p = persistence ?: return@put call.respond(HttpStatusCode.ServiceUnavailable)
                val name =
                    call.parameters["name"] ?: return@put call.respond(HttpStatusCode.BadRequest)
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

            put("/api/admin/players/{name}/preferences") {
                if (!requireAdmin()) return@put
                val p = persistence ?: return@put call.respond(HttpStatusCode.ServiceUnavailable)
                val name =
                    call.parameters["name"] ?: return@put call.respond(HttpStatusCode.BadRequest)
                val existing =
                    p.loadPlayerFile(name) ?: return@put call.respond(HttpStatusCode.NotFound)
                val body =
                    runCatching { Json.parseToJsonElement(call.receiveText()).jsonObject }
                        .getOrNull() ?: return@put call.respond(HttpStatusCode.BadRequest)
                fun str(key: String) = body[key]?.jsonPrimitive?.content
                fun bool(key: String) = body[key]?.jsonPrimitive?.content?.toBooleanStrictOrNull()
                fun int(key: String) = body[key]?.jsonPrimitive?.content?.toIntOrNull()
                val updated =
                    existing.state.copy(
                        skin = str("skin") ?: existing.state.skin,
                        language = str("language") ?: existing.state.language,
                        fieldOfView = int("fieldOfView") ?: existing.state.fieldOfView,
                        shadersEnabled = bool("shadersEnabled") ?: existing.state.shadersEnabled,
                        animatedFavicon = bool("animatedFavicon") ?: existing.state.animatedFavicon,
                        godMode = bool("godMode") ?: existing.state.godMode,
                        lightBoostEnabled =
                            bool("lightBoostEnabled") ?: existing.state.lightBoostEnabled,
                    )
                p.savePlayerState(name, updated)
                call.respond(HttpStatusCode.NoContent)
            }

            post("/api/admin/players/{name}/rename") {
                if (!requireAdmin()) return@post
                val p = persistence ?: return@post call.respond(HttpStatusCode.ServiceUnavailable)
                val name =
                    call.parameters["name"] ?: return@post call.respond(HttpStatusCode.BadRequest)
                val body =
                    runCatching { Json.parseToJsonElement(call.receiveText()).jsonObject }
                        .getOrNull() ?: return@post call.respond(HttpStatusCode.BadRequest)
                val newName =
                    body["newName"]?.jsonPrimitive?.content
                        ?: return@post call.respond(HttpStatusCode.BadRequest)
                p.renamePlayer(name, newName)
                call.respond(HttpStatusCode.NoContent)
            }

            put("/api/admin/players/{name}/rpg") {
                if (!requireAdmin()) return@put
                val p = persistence ?: return@put call.respond(HttpStatusCode.ServiceUnavailable)
                val name =
                    call.parameters["name"] ?: return@put call.respond(HttpStatusCode.BadRequest)
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
            get("/api/admin/worlds") {
                if (!requireAdmin()) return@get
                val worlds = listWorlds()
                call.respondText(
                    adminJson.encodeToString(ListSerializer(WorldStatsDto.serializer()), worlds),
                    ContentType.Application.Json)
            }

            post("/api/admin/worlds") {
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
            get("/api/admin/configs") {
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

            get("/api/admin/configs/{filename...}") {
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

            put("/api/admin/configs/{filename...}") {
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
            get("/api/admin/classes") {
                if (!requireAdmin()) return@get
                call.respondText(
                    adminJson.encodeToString(
                        MapSerializer(String.serializer(), ClassDefinitionEntry.serializer()),
                        gameLoop.classRegistry,
                    ),
                    ContentType.Application.Json)
            }

            get("/api/admin/skills") {
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
            get("/api/admin/npcs") {
                if (!requireAdmin()) return@get
                val dtos =
                    gameLoop.getNpcInstances().map { npc ->
                        val ad = npc.animalData
                        val maxHp = npc.maxHp
                        val zoneX =
                            Math.floorDiv(npc.state.pos.x.toInt(), NpcConstants.NPC_ZONE_SIZE)
                        val zoneZ =
                            Math.floorDiv(npc.state.pos.z.toInt(), NpcConstants.NPC_ZONE_SIZE)
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
                            skills = npc.definition.attacks.map { "${it.attackId} lv${it.level}" },
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
            get("/api/admin/blocks") {
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
                            plainColorable = def.plainColorable,
                        )
                    }
                call.respondText(
                    Json.encodeToString(ListSerializer(BlockInfo.serializer()), blocks),
                    ContentType.Application.Json)
            }

            // ── NPC types ─────────────────────────────────────────────────────
            get("/api/admin/npc-types") {
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
            get("/api/admin/items") {
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
            get("/api/admin/schemas/{filename}") {
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
