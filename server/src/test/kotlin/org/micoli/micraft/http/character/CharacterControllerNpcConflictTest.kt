package org.micoli.micraft.http.character

import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.server.routing.routing
import io.ktor.server.testing.testApplication
import kotlin.io.path.createTempDirectory
import kotlin.test.Test
import kotlin.test.assertEquals
import org.micoli.micraft.game.npc.NpcDefinition
import org.micoli.micraft.game.npc.NpcManager
import org.micoli.micraft.game.npc.behaviors.StaticNpcBehavior
import org.micoli.micraft.game.world.WorldPersistence
import org.micoli.micraft.http.CharacterController
import org.micoli.micraft.player.Vec3

private fun testNpcManager(): NpcManager {
    val manager = NpcManager(broadcast = {})
    manager.loadDefinitions(
        mapOf(
            "SELLER" to
                NpcDefinition(
                    type = "SELLER",
                    behavior = StaticNpcBehavior(),
                    bbmodelFile = "npc",
                    width = 0.6f,
                    height = 1.8f,
                    wanderSpeed = 0f,
                    wanderRadius = 0f,
                )))
    return manager
}

class CharacterControllerNpcConflictTest {

    @Test
    fun createCharacter_nameTakenByLiveNpc_returnsConflict() = testApplication {
        val worldDir = createTempDirectory("character-controller-npc-conflict")
        val persistence = WorldPersistence(worldDir)
        val npcManager = testNpcManager()
        npcManager.spawnNpc("Elder", "SELLER", Vec3(0f, 0f, 0f))

        application { routing { CharacterController(persistence, npcManager).register(this) } }

        val r =
            client.post("/api/character/create") {
                contentType(ContentType.Application.Json)
                setBody("""{"playerName":"Elder","skin":"articulated"}""")
            }
        assertEquals(HttpStatusCode.Conflict, r.status)
    }

    @Test
    fun createRpgCharacter_nameTakenByLiveNpc_returnsConflict() = testApplication {
        val worldDir = createTempDirectory("character-controller-npc-conflict-rpg")
        val persistence = WorldPersistence(worldDir)
        val npcManager = testNpcManager()
        npcManager.spawnNpc("Elder", "SELLER", Vec3(0f, 0f, 0f))

        application { routing { CharacterController(persistence, npcManager).register(this) } }

        val r =
            client.post("/api/character/rpgcreate") {
                contentType(ContentType.Application.Json)
                setBody(
                    """{"playerName":"Elder","skin":"articulated","characterClass":"WARRIOR",
                    |"str":14,"dex":8,"intel":8,"wis":8,"con":12,"cha":8}"""
                        .trimMargin())
            }
        assertEquals(HttpStatusCode.Conflict, r.status)
    }

    @Test
    fun createCharacter_ownExistingName_notBlockedByNpcCheck() = testApplication {
        val worldDir = createTempDirectory("character-controller-npc-own-name")
        val persistence = WorldPersistence(worldDir)
        val npcManager = testNpcManager()

        application { routing { CharacterController(persistence, npcManager).register(this) } }

        val first =
            client.post("/api/character/create") {
                contentType(ContentType.Application.Json)
                setBody("""{"playerName":"Returning","skin":"articulated"}""")
            }
        assertEquals(HttpStatusCode.OK, first.status)

        val second =
            client.post("/api/character/create") {
                contentType(ContentType.Application.Json)
                setBody("""{"playerName":"Returning","skin":"steve"}""")
            }
        assertEquals(HttpStatusCode.OK, second.status)
    }
}
