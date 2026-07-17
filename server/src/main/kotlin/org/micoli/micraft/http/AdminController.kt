package org.micoli.micraft.http

import io.ktor.http.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
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
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.micoli.micraft.auth.LocalAuthProvider
import org.micoli.micraft.auth.TokenStore
import org.micoli.micraft.game.GameLoop
import org.micoli.micraft.game.TICKS_PER_DAY
import org.micoli.micraft.game.world.PlayerFile
import org.micoli.micraft.game.world.WorldPersistence
import org.micoli.micraft.player.rpg.CharacterClass

@Serializable
data class UserDto(val email: String, val displayName: String, val groups: List<String>)

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
        "attack.yaml",
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
            get("/admin/{...}") {
                val html =
                    AdminController::class
                        .java
                        .classLoader
                        .getResourceAsStream("admin.html")!!
                        .bufferedReader()
                        .readText()
                call.respondText(html, ContentType.Text.Html)
            }
            get("/admin") {
                val html =
                    AdminController::class
                        .java
                        .classLoader
                        .getResourceAsStream("admin.html")!!
                        .bufferedReader()
                        .readText()
                call.respondText(html, ContentType.Text.Html)
            }
            get("/admin.js") {
                val js =
                    AdminController::class
                        .java
                        .classLoader
                        .getResourceAsStream("admin.js")
                        ?.bufferedReader()
                        ?.readText() ?: ""
                call.respondText(js, ContentType.Text.JavaScript)
            }
            get("/admin.css") {
                val css =
                    AdminController::class
                        .java
                        .classLoader
                        .getResourceAsStream("admin.css")
                        ?.bufferedReader()
                        ?.readText() ?: ""
                call.respondText(css, ContentType.Text.CSS)
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

    private fun isAllowedConfigFile(filename: String): Boolean {
        if (filename.contains("..")) return false
        val name = filename.removePrefix("auth/")
        return filename in configFileWhitelist ||
            filename.startsWith("auth/") && (name == "users.yaml" || name == "groups.yaml")
    }
}
