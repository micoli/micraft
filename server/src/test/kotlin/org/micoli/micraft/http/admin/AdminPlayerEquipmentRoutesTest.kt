package org.micoli.micraft.http.admin

import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.server.routing.*
import io.ktor.server.testing.*
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import org.micoli.micraft.di.SessionRegistry
import org.micoli.micraft.game.GameLoop
import org.micoli.micraft.game.world.ItemType
import org.micoli.micraft.game.world.WorldPersistence
import org.micoli.micraft.http.AdminController
import org.micoli.micraft.player.Hand
import org.micoli.micraft.player.rpg.BaseStats
import org.micoli.micraft.player.rpg.CharacterClass
import org.micoli.micraft.player.rpg.CharacterData
import org.micoli.micraft.protocol.ServerMessage
import org.micoli.micraft.support.testPlayerState
import org.micoli.micraft.support.testSession
import org.micoli.micraft.support.testWorld

class AdminPlayerEquipmentRoutesTest {

    private fun persistence() = WorldPersistence(Files.createTempDirectory("admin-equipment-test"))

    private fun controller(persistence: WorldPersistence) =
        AdminController(null, null, persistence, GameLoop(testWorld()), "data", null)

    private fun controllerWithSession(
        persistence: WorldPersistence,
        sessionRegistry: SessionRegistry
    ) =
        AdminController(
            null,
            null,
            persistence,
            GameLoop(testWorld(), persistence = persistence, sessionRegistry = sessionRegistry),
            "data",
            null)

    @Test
    fun `give_unknown_name_returns_404`() = testApplication {
        val p = persistence()
        p.savePlayerState("bob", testPlayerState(name = "bob"))
        application { routing { controller(p).register(this) } }

        val r =
            client.post("/api/admin/players/bob/give") {
                contentType(ContentType.Application.Json)
                setBody("""{"name":"totally_unknown_thing"}""")
            }
        assertEquals(HttpStatusCode.NotFound, r.status)
    }

    @Test
    fun `give_item_adds_to_inventory`() = testApplication {
        val p = persistence()
        p.savePlayerState("bob", testPlayerState(name = "bob"))
        application { routing { controller(p).register(this) } }

        val r =
            client.post("/api/admin/players/bob/give") {
                contentType(ContentType.Application.Json)
                setBody("""{"name":"cobblestone","count":3}""")
            }
        assertEquals(HttpStatusCode.NoContent, r.status)
        val updated = p.loadPlayerFile("bob")!!
        assertEquals(3, updated.state.inventory[ItemType("COBBLESTONE")])
    }

    @Test
    fun `give_armor_grants_ownership`() = testApplication {
        val p = persistence()
        p.savePlayerState("bob", testPlayerState(name = "bob"))
        application { routing { controller(p).register(this) } }

        val r =
            client.post("/api/admin/players/bob/give") {
                contentType(ContentType.Application.Json)
                setBody("""{"name":"helmet"}""")
            }
        assertEquals(HttpStatusCode.NoContent, r.status)
        val updated = p.loadPlayerFile("bob")!!
        assertTrue("helmet" in updated.state.ownedArmors)
    }

    @Test
    fun `give_already_owned_armor_is_noop`() = testApplication {
        val p = persistence()
        p.savePlayerState("bob", testPlayerState(name = "bob").copy(ownedArmors = listOf("helmet")))
        application { routing { controller(p).register(this) } }

        val r =
            client.post("/api/admin/players/bob/give") {
                contentType(ContentType.Application.Json)
                setBody("""{"name":"helmet"}""")
            }
        assertEquals(HttpStatusCode.NoContent, r.status)
        val updated = p.loadPlayerFile("bob")!!
        assertEquals(1, updated.state.ownedArmors.size)
    }

    @Test
    fun `equipment_updates_owned_worn_and_hands`() = testApplication {
        val p = persistence()
        p.savePlayerState("bob", testPlayerState(name = "bob"))
        application { routing { controller(p).register(this) } }

        val r =
            client.put("/api/admin/players/bob/equipment") {
                contentType(ContentType.Application.Json)
                setBody(
                    """{"ownedArmors":["helmet"],"armors":["helmet"],"ownedWeapons":["iron_sword"],"rightHandItem":"iron_sword","dominantHand":"RIGHT"}""")
            }
        assertEquals(HttpStatusCode.NoContent, r.status)
        val updated = p.loadPlayerFile("bob")!!.state
        assertEquals(listOf("helmet"), updated.ownedArmors)
        assertEquals(listOf("helmet"), updated.armors)
        assertEquals(listOf("iron_sword"), updated.ownedWeapons)
        assertEquals("iron_sword", updated.rightHandItem)
        assertEquals(Hand.RIGHT, updated.dominantHand)
    }

    @Test
    fun `equipment_blank_hand_item_clears_it`() = testApplication {
        val p = persistence()
        p.savePlayerState("bob", testPlayerState(name = "bob").copy(rightHandItem = "iron_sword"))
        application { routing { controller(p).register(this) } }

        val r =
            client.put("/api/admin/players/bob/equipment") {
                contentType(ContentType.Application.Json)
                setBody("""{"rightHandItem":""}""")
            }
        assertEquals(HttpStatusCode.NoContent, r.status)
        val updated = p.loadPlayerFile("bob")!!.state
        assertNull(updated.rightHandItem)
    }

    @Test
    fun `give_item_finds_live_session_by_sanitized_name_when_display_name_has_a_space`() =
        testApplication {
            val p = persistence()
            val sessionRegistry = SessionRegistry()
            // The URL/admin "name" is the filesystem-sanitized form (spaces -> "_"), but a
            // connected player's live PlayerSession.state.name is the real display name
            // ("Bob Bobson") — findSession() must sanitize before comparing, or it silently
            // misses the live session and falls back to the (stale, until reconnect) disk path.
            val session = testSession(name = "Bob Bobson")
            sessionRegistry["conn1"] = session
            application { routing { controllerWithSession(p, sessionRegistry).register(this) } }

            val r =
                client.post("/api/admin/players/Bob_Bobson/give") {
                    contentType(ContentType.Application.Json)
                    setBody("""{"name":"cobblestone","count":2}""")
                }
            assertEquals(HttpStatusCode.NoContent, r.status)
            assertEquals(2, session.inventory[ItemType("COBBLESTONE")])
            assertTrue(session.sent.any { it is ServerMessage.InventoryUpdate })
        }

    @Test
    fun `give_item_updates_live_session_and_persists_when_player_online`() = testApplication {
        val p = persistence()
        val sessionRegistry = SessionRegistry()
        val session = testSession(name = "bob")
        sessionRegistry["conn1"] = session
        application { routing { controllerWithSession(p, sessionRegistry).register(this) } }

        val r =
            client.post("/api/admin/players/bob/give") {
                contentType(ContentType.Application.Json)
                setBody("""{"name":"cobblestone","count":3}""")
            }
        assertEquals(HttpStatusCode.NoContent, r.status)
        assertEquals(3, session.inventory[ItemType("COBBLESTONE")])
        assertTrue(session.sent.any { it is ServerMessage.InventoryUpdate })
        assertEquals(3, p.loadPlayerFile("bob")!!.state.inventory[ItemType("COBBLESTONE")])
    }

    @Test
    fun `give_armor_grants_ownership_on_live_session_when_online`() = testApplication {
        val p = persistence()
        val sessionRegistry = SessionRegistry()
        val session = testSession(name = "bob")
        sessionRegistry["conn1"] = session
        application { routing { controllerWithSession(p, sessionRegistry).register(this) } }

        val r =
            client.post("/api/admin/players/bob/give") {
                contentType(ContentType.Application.Json)
                setBody("""{"name":"helmet"}""")
            }
        assertEquals(HttpStatusCode.NoContent, r.status)
        assertTrue("helmet" in session.state.ownedArmors)
        assertTrue("helmet" in p.loadPlayerFile("bob")!!.state.ownedArmors)
    }

    @Test
    fun `equipment_updates_live_session_and_broadcasts_when_online`() = testApplication {
        val p = persistence()
        val sessionRegistry = SessionRegistry()
        val session = testSession(name = "bob")
        sessionRegistry["conn1"] = session
        application { routing { controllerWithSession(p, sessionRegistry).register(this) } }

        val r =
            client.put("/api/admin/players/bob/equipment") {
                contentType(ContentType.Application.Json)
                setBody("""{"ownedWeapons":["iron_sword"],"rightHandItem":"iron_sword"}""")
            }
        assertEquals(HttpStatusCode.NoContent, r.status)
        assertEquals("iron_sword", session.state.rightHandItem)
        assertTrue(session.sent.any { it is ServerMessage.PlayerUpdate })
        assertEquals("iron_sword", p.loadPlayerFile("bob")!!.state.rightHandItem)
    }

    @Test
    fun `equipment_online_sends_character_sync_when_player_has_rpg_character`() = testApplication {
        val p = persistence()
        val sessionRegistry = SessionRegistry()
        val session = testSession(name = "bob")
        session.characterData =
            CharacterData(
                id = "c1",
                name = "bob",
                characterClass = CharacterClass.WARRIOR,
                baseStats = BaseStats(),
                currentHp = 10,
                currentMana = 10,
            )
        sessionRegistry["conn1"] = session
        application { routing { controllerWithSession(p, sessionRegistry).register(this) } }

        val r =
            client.put("/api/admin/players/bob/equipment") {
                contentType(ContentType.Application.Json)
                setBody("""{"ownedArmors":["helmet"],"armors":["helmet"]}""")
            }
        assertEquals(HttpStatusCode.NoContent, r.status)
        assertTrue(session.sent.any { it is ServerMessage.CharacterSync })
    }
}
