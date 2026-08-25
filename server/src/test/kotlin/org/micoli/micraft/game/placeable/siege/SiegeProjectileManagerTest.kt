package org.micoli.micraft.game.placeable.siege

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlinx.coroutines.runBlocking
import org.micoli.micraft.game.combat.CombatConfigData
import org.micoli.micraft.game.combat.CombatProcessor
import org.micoli.micraft.game.npc.NpcDefinition
import org.micoli.micraft.game.npc.NpcManager
import org.micoli.micraft.game.npc.behaviors.StaticNpcBehavior
import org.micoli.micraft.game.session.PlayerSession
import org.micoli.micraft.game.world.EntityType
import org.micoli.micraft.player.Vec3
import org.micoli.micraft.player.rpg.BaseStats
import org.micoli.micraft.player.rpg.CharacterClass
import org.micoli.micraft.player.rpg.CharacterData
import org.micoli.micraft.protocol.ServerMessage
import org.micoli.micraft.support.testI18n
import org.micoli.micraft.support.testSession
import org.micoli.micraft.support.testWorld

class SiegeProjectileManagerTest {

    private val boulder = EntityType("TEST_BOULDER")

    private fun testChar(id: String, name: String, hp: Int) =
        CharacterData(
            id = id,
            name = name,
            characterClass = CharacterClass.WARRIOR,
            baseStats = BaseStats(),
            currentHp = hp,
            currentMana = 0,
            currentRage = 0,
            level = 1,
        )

    private fun buildCombatProcessor(
        sessions: List<PlayerSession>,
        npcManager: NpcManager,
        combatLog: MutableList<String> = mutableListOf(),
    ) =
        CombatProcessor(
            config = CombatConfigData(maxCombatRange = 100f),
            attackRegistry = emptyMap(),
            armorRegistry = emptyMap(),
            classRegistry = emptyMap(),
            npcManager = npcManager,
            getSessions = { sessions },
            broadcastCombatLog = { combatLog.add(it) },
            subscribeToChannel = { _, _ -> },
            i18n = testI18n(),
            savePlayer = {},
        )

    /** Runs ticks until the projectile impacts (removed from the manager) or [maxTicks] is hit. */
    private suspend fun runUntilImpact(
        manager: SiegeProjectileManager,
        world: org.micoli.micraft.game.world.WorldState,
        sessions: List<PlayerSession>,
        npcManager: NpcManager,
        combatProcessor: CombatProcessor,
        maxTicks: Int = 500,
    ) {
        repeat(maxTicks) {
            if (manager.getAll().isEmpty()) return
            manager.tick(world, sessions, npcManager, combatProcessor)
        }
    }

    @Test
    fun impact_damagesPlayersWithinRadius_notOutsideRadius() = runBlocking {
        val world = testWorld(Triple(8, 6, 8))
        val npcManager = NpcManager(broadcast = {})
        val near = testSession(id = "near", pos = Vec3(9f, 7f, 9f))
        near.characterData = testChar("near", "Near", hp = 50)
        val far = testSession(id = "far", pos = Vec3(50f, 7f, 50f))
        far.characterData = testChar("far", "Far", hp = 50)
        val sessions = listOf(near, far)
        val combatProcessor = buildCombatProcessor(sessions, npcManager)

        val broadcasts = mutableListOf<ServerMessage>()
        val manager = SiegeProjectileManager({ broadcasts.add(it) })
        manager.spawnProjectile(
            type = boulder,
            pos = Vec3(8.5f, 20f, 8.5f),
            velocity = Vec3(0f, 0f, 0f),
            ownerId = "owner",
            impactRadius = 3f,
            impactDamage = 25,
        )

        runUntilImpact(manager, world, sessions, npcManager, combatProcessor)

        assertTrue(manager.getAll().isEmpty(), "projectile must have impacted")
        assertTrue(
            near.characterData!!.currentHp < 50, "player within blast radius must take damage")
        assertEquals(
            50, far.characterData!!.currentHp, "player outside blast radius must be untouched")
    }

    @Test
    fun impact_damagesNpcsWithinRadius() = runBlocking {
        val world = testWorld(Triple(8, 6, 8))
        val npcManager = NpcManager(broadcast = {})
        npcManager.loadDefinitions(
            mapOf(
                "dummy" to
                    NpcDefinition(
                        type = "dummy",
                        behavior = StaticNpcBehavior(),
                        bbmodelFile = "dummy.bbmodel",
                        width = 0.6f,
                        height = 1.8f,
                        wanderSpeed = 1f,
                        wanderRadius = 5f,
                        hp = 100,
                    )))
        val nearNpc = npcManager.spawnNpc("NearDummy", "dummy", Vec3(9f, 7f, 9f))
        val farNpc = npcManager.spawnNpc("FarDummy", "dummy", Vec3(50f, 7f, 50f))
        val sessions = emptyList<PlayerSession>()
        val combatProcessor = buildCombatProcessor(sessions, npcManager)

        val manager = SiegeProjectileManager({})
        manager.spawnProjectile(
            type = boulder,
            pos = Vec3(8.5f, 20f, 8.5f),
            velocity = Vec3(0f, 0f, 0f),
            ownerId = "owner",
            impactRadius = 3f,
            impactDamage = 40,
        )

        runUntilImpact(manager, world, sessions, npcManager, combatProcessor)

        assertTrue(manager.getAll().isEmpty(), "projectile must have impacted")
        assertTrue(
            npcManager.getInstance(nearNpc.state.id)!!.currentHp < 100,
            "NPC within blast radius must take damage")
        assertEquals(
            100,
            npcManager.getInstance(farNpc.state.id)!!.currentHp,
            "NPC outside blast radius must be untouched")
    }

    @Test
    fun impact_broadcastsImpactMessage() = runBlocking {
        val world = testWorld(Triple(8, 6, 8))
        val npcManager = NpcManager(broadcast = {})
        val sessions = emptyList<PlayerSession>()
        val combatProcessor = buildCombatProcessor(sessions, npcManager)

        val broadcasts = mutableListOf<ServerMessage>()
        val manager = SiegeProjectileManager({ broadcasts.add(it) })
        manager.spawnProjectile(
            type = boulder,
            pos = Vec3(8.5f, 20f, 8.5f),
            velocity = Vec3(0f, 0f, 0f),
            ownerId = "owner",
            impactRadius = 3f,
            impactDamage = 25,
        )

        runUntilImpact(manager, world, sessions, npcManager, combatProcessor)

        assertTrue(broadcasts.filterIsInstance<ServerMessage.SiegeProjectileSpawned>().isNotEmpty())
        assertTrue(broadcasts.filterIsInstance<ServerMessage.SiegeProjectileImpact>().isNotEmpty())
    }
}
