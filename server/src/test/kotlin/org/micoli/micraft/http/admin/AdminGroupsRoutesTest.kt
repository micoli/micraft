package org.micoli.micraft.http.admin

import com.charleskorn.kaml.Yaml
import io.ktor.client.request.HttpRequestBuilder
import io.ktor.client.request.delete
import io.ktor.client.request.get
import io.ktor.client.request.headers
import io.ktor.client.request.post
import io.ktor.client.request.put
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.server.routing.application
import io.ktor.server.routing.delete
import io.ktor.server.routing.get
import io.ktor.server.routing.path
import io.ktor.server.routing.post
import io.ktor.server.routing.put
import io.ktor.server.routing.routing
import io.ktor.server.testing.testApplication
import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.micoli.micraft.auth.AuthResult
import org.micoli.micraft.auth.GroupEntry
import org.micoli.micraft.auth.GroupsConfig
import org.micoli.micraft.auth.LocalAuthProvider
import org.micoli.micraft.auth.Permission
import org.micoli.micraft.auth.TokenStore
import org.micoli.micraft.game.GameLoop
import org.micoli.micraft.http.AdminController
import org.micoli.micraft.support.testAuthProvider
import org.micoli.micraft.support.testSession
import org.micoli.micraft.support.testWorld

class AdminGroupsRoutesTest {

    private data class Fixture(
        val controller: AdminController,
        val gameLoop: GameLoop,
        val provider: LocalAuthProvider,
        val token: String,
    )

    /** Test-only stand-in for the disk reload the real `reloadRbac` lambda performs. */
    private fun reloadFromDisk(path: Path, provider: LocalAuthProvider) {
        provider.groupsConfig =
            runCatching {
                    Yaml.default.decodeFromString(
                        GroupsConfig.serializer(), path.toFile().readText())
                }
                .getOrDefault(provider.groupsConfig)
    }

    private fun setup(
        initialGroups: GroupsConfig = GroupsConfig(),
        persistence: org.micoli.micraft.game.world.WorldPersistence? = null,
    ): Fixture {
        val usersFile =
            Files.createTempFile("micraft-users", ".yaml").also {
                it.toFile().writeText("users: []\n")
            }
        val groupsFile = Files.createTempFile("micraft-groups", ".yaml")
        val provider = testAuthProvider(usersFile, initialGroups)
        val store = TokenStore(CoroutineScope(Dispatchers.Default))
        val token =
            store.issue(
                AuthResult(
                    playerId = "admin",
                    displayName = "Admin",
                    permissions = setOf(Permission("admin"))))
        val gameLoop = GameLoop(testWorld(), persistence)
        val controller =
            AdminController(
                provider,
                persistence,
                gameLoop,
                store,
                authProvider = provider,
                groupsFilePath = groupsFile,
                reloadRbac = { reloadFromDisk(groupsFile, provider) })
        return Fixture(controller, gameLoop, provider, token)
    }

    private fun HttpRequestBuilder.auth(token: String) {
        headers.append(HttpHeaders.Authorization, "Bearer $token")
    }

    @Test
    fun `get permissions includes discovered command and standalone permissions`() =
        testApplication {
            val fx = setup()
            application { routing { fx.controller.register(this) } }

            val r = client.get("/api/admin/permissions") { auth(fx.token) }
            assertEquals(HttpStatusCode.OK, r.status)
            val permissions =
                Json.parseToJsonElement(r.bodyAsText()).jsonArray.map { it.jsonPrimitive.content }
            assertTrue("admin" in permissions, "admin commands' permission should be discovered")
            assertTrue(
                "actionblock:edit" in permissions, "standalone permission should be included")
            assertEquals(permissions, permissions.sorted(), "response should be sorted")
            assertEquals(permissions.distinct(), permissions, "response should be deduplicated")
        }

    @Test
    fun `get groups includes virtual admin group as non editable`() = testApplication {
        val fx = setup(GroupsConfig(groups = listOf(GroupEntry("player", listOf("move")))))
        application { routing { fx.controller.register(this) } }

        val r = client.get("/api/admin/groups") { auth(fx.token) }
        assertEquals(HttpStatusCode.OK, r.status)
        val groups = Json.parseToJsonElement(r.bodyAsText()).jsonObject["groups"]!!.jsonArray
        val admin = groups.first { it.jsonObject["name"]!!.jsonPrimitive.content == "admin" }
        assertFalse(admin.jsonObject["editable"]!!.jsonPrimitive.boolean)
        val player = groups.first { it.jsonObject["name"]!!.jsonPrimitive.content == "player" }
        assertTrue(player.jsonObject["editable"]!!.jsonPrimitive.boolean)
    }

    @Test
    fun `create group persists and is retrievable`() = testApplication {
        val fx = setup()
        application { routing { fx.controller.register(this) } }

        val create =
            client.post("/api/admin/groups") {
                auth(fx.token)
                contentType(ContentType.Application.Json)
                setBody("""{"name":"moderator","permissions":["give","kick"]}""")
            }
        assertEquals(HttpStatusCode.Created, create.status)

        val r = client.get("/api/admin/groups") { auth(fx.token) }
        val groups = Json.parseToJsonElement(r.bodyAsText()).jsonObject["groups"]!!.jsonArray
        val moderator =
            groups.first { it.jsonObject["name"]!!.jsonPrimitive.content == "moderator" }
        assertEquals(
            setOf("give", "kick"),
            moderator.jsonObject["permissions"]!!
                .jsonArray
                .map { it.jsonPrimitive.content }
                .toSet())
    }

    @Test
    fun `create group named admin is rejected`() = testApplication {
        val fx = setup()
        application { routing { fx.controller.register(this) } }

        val r =
            client.post("/api/admin/groups") {
                auth(fx.token)
                contentType(ContentType.Application.Json)
                setBody("""{"name":"admin","permissions":[]}""")
            }
        assertEquals(HttpStatusCode.BadRequest, r.status)
    }

    @Test
    fun `delete admin group is rejected`() = testApplication {
        val fx = setup()
        application { routing { fx.controller.register(this) } }

        val r = client.delete("/api/admin/groups/admin") { auth(fx.token) }
        assertEquals(HttpStatusCode.BadRequest, r.status)
    }

    @Test
    fun `updating a group's permissions applies live to a connected session immediately`() =
        testApplication {
            val fx = setup(GroupsConfig(groups = listOf(GroupEntry("player", listOf("move")))))
            application { routing { fx.controller.register(this) } }

            val session = testSession(id = "bob-id", name = "Bob")
            session.state = session.state.copy(groups = listOf("player"))
            session.permissions = fx.provider.groupsConfig.resolvePermissions(listOf("player"))
            fx.gameLoop.gameWorldRegistry.defaultWorld.sessions["bob-id"] = session
            assertTrue(Permission("build") !in session.permissions)

            val r =
                client.put("/api/admin/groups/player") {
                    auth(fx.token)
                    contentType(ContentType.Application.Json)
                    setBody("""{"permissions":["move","build"]}""")
                }
            assertEquals(HttpStatusCode.NoContent, r.status)

            assertTrue(
                Permission("build") in session.permissions,
                "Bob's connected session should gain the new permission without reconnecting")
        }

    @Test
    fun `deleting a group unassigns it from users that had it`() = testApplication {
        val fx = setup(GroupsConfig(groups = listOf(GroupEntry("moderator", listOf("give")))))
        application { routing { fx.controller.register(this) } }
        fx.provider.addUser("bob@test.com", "pass", "Bob", listOf("moderator"))

        val r = client.delete("/api/admin/groups/moderator") { auth(fx.token) }
        assertEquals(HttpStatusCode.NoContent, r.status)

        assertEquals(emptyList(), fx.provider.getUserGroups("bob@test.com"))
    }

    @Test
    fun `deleting a group unassigns it from offline characters that had it`() = testApplication {
        val persistence =
            org.micoli.micraft.game.world.WorldPersistence(
                java.nio.file.Files.createTempDirectory("admin-groups-cascade-test"))
        persistence.savePlayerState(
            "Carol",
            org.micoli.micraft.player.PlayerState(
                id = "carol-id",
                name = "Carol",
                pos = org.micoli.micraft.player.Vec3(0f, 0f, 0f),
                orientation = org.micoli.micraft.player.Orientation(0f, 0f),
                groups = listOf("moderator"),
            ))
        val fx =
            setup(
                GroupsConfig(groups = listOf(GroupEntry("moderator", listOf("give")))), persistence)
        application { routing { fx.controller.register(this) } }

        val r = client.delete("/api/admin/groups/moderator") { auth(fx.token) }
        assertEquals(HttpStatusCode.NoContent, r.status)

        assertEquals(emptyList(), persistence.loadPlayerState("Carol")?.groups)
    }
}
