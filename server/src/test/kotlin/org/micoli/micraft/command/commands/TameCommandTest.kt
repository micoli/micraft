package org.micoli.micraft.command.commands

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlinx.coroutines.runBlocking
import org.micoli.micraft.combat.CombatState
import org.micoli.micraft.game.npc.AggroMode
import org.micoli.micraft.game.npc.NpcDefinition
import org.micoli.micraft.game.npc.NpcManager
import org.micoli.micraft.game.npc.behaviors.RandomMovableNpcBehavior
import org.micoli.micraft.game.pet.PetManager
import org.micoli.micraft.game.rpg.ExperienceConfig
import org.micoli.micraft.game.rpg.ExperienceProcessor
import org.micoli.micraft.player.Vec3
import org.micoli.micraft.player.rpg.BaseStats
import org.micoli.micraft.player.rpg.CharacterClass
import org.micoli.micraft.player.rpg.CharacterData
import org.micoli.micraft.protocol.ServerMessage
import org.micoli.micraft.support.FakePlayerSession
import org.micoli.micraft.support.testContext
import org.micoli.micraft.support.testI18n
import org.micoli.micraft.support.testSession

private fun def(
    type: String,
    tameable: Boolean = true,
    chance: Float = 0.5f,
    aggro: AggroMode = AggroMode.PASSIVE,
) =
    NpcDefinition(
        type = type,
        behavior = RandomMovableNpcBehavior(),
        bbmodelFile = "npc",
        width = 0.5f,
        height = 0.9f,
        wanderSpeed = 2f,
        wanderRadius = 20f,
        hp = 20,
        aggroMode = aggro,
        tameable = tameable,
        tameBaseChance = chance,
    )

private class Ctx {
    val sessions = mutableListOf<FakePlayerSession>()
    val npcManager =
        NpcManager(broadcast = {}, getSessions = { sessions }).apply {
            loadDefinitions(
                mapOf(
                    "cat" to def("cat", chance = 1f),
                    "dragon" to def("dragon", chance = 0f),
                    "rock" to def("rock", tameable = false),
                    "bear" to def("bear", chance = 0f, aggro = AggroMode.AGGRESSIVE),
                ))
        }
    val petManager =
        PetManager(
            npcManager = npcManager,
            experienceProcessor = ExperienceProcessor(ExperienceConfig().data, { sessions }, {}),
            getSessions = { sessions },
            savePlayer = {},
            i18n = testI18n(),
        )
    val cmd = TameCommand()

    fun withRoll(v: Float) = cmd.also { it.roll = { v } }

    fun context() =
        testContext(sessions = sessions, npcManager = npcManager, petManager = petManager)

    fun player(level: Int = 5): FakePlayerSession =
        testSession(pos = Vec3(0f, 5f, 0f)).also {
            it.characterData =
                CharacterData(
                    id = it.id,
                    name = it.state.name,
                    characterClass = CharacterClass.WARRIOR,
                    level = level,
                    baseStats = BaseStats(),
                    currentHp = 30,
                    currentMana = 0,
                )
            sessions.add(it)
        }

    suspend fun target(type: String, level: Int = 1): String {
        val inst = npcManager.spawnNpc(type, type, Vec3(1f, 5f, 0f), instanceLevel = level)
        return inst.state.id
    }
}

class TameCommandTest {
    @Test
    fun `no target notifies`() = runBlocking {
        val c = Ctx()
        val p = c.player()
        c.cmd.execute(p, "", c.context())
        assertTrue(p.sent.filterIsInstance<ServerMessage.Notification>().isNotEmpty())
        assertTrue(p.state.pets.isEmpty())
    }

    @Test
    fun `untameable creature refused`() = runBlocking {
        val c = Ctx()
        val p = c.player()
        p.combatState = CombatState(targetId = c.target("rock"), targetIsNpc = true)
        c.cmd.execute(p, "", c.context())
        assertTrue(p.state.pets.isEmpty())
    }

    @Test
    fun `creature above player level refused`() = runBlocking {
        val c = Ctx()
        val p = c.player(level = 2)
        p.combatState = CombatState(targetId = c.target("cat", level = 5), targetIsNpc = true)
        c.cmd.execute(p, "", c.context())
        assertTrue(p.state.pets.isEmpty())
    }

    @Test
    fun `guaranteed success tames, despawns wild npc and auto-summons`() = runBlocking {
        val c = Ctx()
        val p = c.player()
        val id = c.target("cat", level = 1)
        p.combatState = CombatState(targetId = id, targetIsNpc = true)

        c.withRoll(0f).execute(p, "", c.context())

        assertEquals(1, p.state.pets.size)
        assertEquals("cat", p.state.pets[0].npcType)
        assertEquals(null, c.npcManager.getInstance(id))
        assertEquals(1, c.npcManager.ownedPets().size)
        assertEquals(p.state.pets[0].id, p.state.activePetId)
    }

    @Test
    fun `failed tame on aggressive mob aggros it`() = runBlocking {
        val c = Ctx()
        val p = c.player()
        val id = c.target("bear", level = 1)
        p.combatState = CombatState(targetId = id, targetIsNpc = true)

        c.withRoll(1f).execute(p, "", c.context())

        assertTrue(p.state.pets.isEmpty())
        assertEquals(p.id, c.npcManager.getInstance(id)?.aggroTarget)
    }

    @Test
    fun `already tamed creature refused`() = runBlocking {
        val c = Ctx()
        val p = c.player()
        val inst = c.npcManager.spawnNpc("cat", "cat", Vec3(1f, 5f, 0f), instanceLevel = 1)
        inst.ownerId = "someone"
        p.combatState = CombatState(targetId = inst.state.id, targetIsNpc = true)

        c.cmd.execute(p, "", c.context())

        assertTrue(p.state.pets.isEmpty())
    }
}
