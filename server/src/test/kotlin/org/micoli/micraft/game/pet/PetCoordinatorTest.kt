package org.micoli.micraft.game.pet

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.runBlocking
import org.micoli.micraft.combat.CombatState
import org.micoli.micraft.game.combat.CombatConfigData
import org.micoli.micraft.game.combat.CombatProcessor
import org.micoli.micraft.game.npc.NpcDefinition
import org.micoli.micraft.game.npc.NpcManager
import org.micoli.micraft.game.npc.behaviors.RandomMovableNpcBehavior
import org.micoli.micraft.player.Vec3
import org.micoli.micraft.support.FakePlayerSession
import org.micoli.micraft.support.testI18n
import org.micoli.micraft.support.testSession

private fun d(type: String) =
    NpcDefinition(
        type = type,
        behavior = RandomMovableNpcBehavior(),
        bbmodelFile = "npc",
        width = 0.5f,
        height = 0.9f,
        wanderSpeed = 2f,
        wanderRadius = 20f,
        hp = 30,
    )

private fun combat(m: NpcManager) =
    CombatProcessor(
        config = CombatConfigData(),
        attackRegistry = emptyMap(),
        armorRegistry = emptyMap(),
        classRegistry = emptyMap(),
        npcManager = m,
        getSessions = { emptyList() },
        broadcastCombatLog = {},
        subscribeToChannel = { _, _ -> },
        i18n = testI18n(),
        savePlayer = {},
    )

private class Fx {
    val sessions = mutableListOf<FakePlayerSession>()
    val npcManager =
        NpcManager(broadcast = {}, getSessions = { sessions }).apply {
            loadDefinitions(mapOf("wolf" to d("wolf"), "boar" to d("boar")))
        }
    val coordinator = PetCoordinator(npcManager, CombatConfigData())

    fun owner() = testSession(pos = Vec3(0f, 5f, 0f)).also { sessions.add(it) }

    suspend fun pet(owner: FakePlayerSession): org.micoli.micraft.game.npc.NpcInstance {
        val inst = npcManager.spawnNpc("Rex", "wolf", Vec3(0f, 5f, 1f))
        inst.ownerId = owner.id
        inst.petRecordId = "rec"
        return inst
    }

    suspend fun wild(pos: Vec3) = npcManager.spawnNpc("Boar", "boar", pos)
}

class PetCoordinatorTest {
    @Test
    fun `pet chases the owner's combat target`() = runBlocking {
        val f = Fx()
        val o = f.owner()
        val pet = f.pet(o)
        val target = f.wild(Vec3(2f, 5f, 0f))
        o.combatState = CombatState(targetId = target.state.id, targetIsNpc = true)

        f.coordinator.tick(f.sessions, combat(f.npcManager))

        // Pet is sent to a point in front of the mob, on the side facing the owner.
        val dest = assertNotNull(pet.chaseTargetPos)
        assertTrue(dest.x < target.state.pos.x, "pet should be on the owner's side of the mob")
        assertTrue(target.damageContributors.containsKey(o.id))
    }

    @Test
    fun `pet targets a mob aggroing its owner when no combat target`() = runBlocking {
        val f = Fx()
        val o = f.owner()
        val pet = f.pet(o)
        val mob = f.wild(Vec3(3f, 5f, 0f))
        mob.aggroTarget = o.id

        f.coordinator.tick(f.sessions, combat(f.npcManager))

        val dest = assertNotNull(pet.chaseTargetPos)
        assertTrue(dest.x < mob.state.pos.x, "pet should be in front of the mob toward the owner")
    }

    @Test
    fun `pet never targets its owner or another pet`() = runBlocking {
        val f = Fx()
        val o = f.owner()
        val pet = f.pet(o)
        val friend = f.pet(o)
        friend.state = friend.state.copy(pos = Vec3(50f, 5f, 50f))
        o.combatState = CombatState(targetId = friend.state.id, targetIsNpc = true)

        f.coordinator.tick(f.sessions, combat(f.npcManager))

        // friend is owned → not a valid target → pet falls back to its idle station near the owner.
        assertTrue(pet.chaseTargetPos == null || pet.chaseTargetPos!!.x < 1f)
    }

    @Test
    fun `idle pet holds a station to the owner's left`() = runBlocking {
        val f = Fx()
        val o = f.owner() // pos (0,5,0), yaw 0 → left = (-1, 0)
        val pet = f.pet(o)
        pet.state = pet.state.copy(pos = Vec3(10f, 5f, 0f))

        f.coordinator.tick(f.sessions, combat(f.npcManager))

        val dest = assertNotNull(pet.chaseTargetPos)
        assertEquals(-1.3f, dest.x, 0.01f)
        assertEquals(0f, dest.z, 0.01f)
    }

    @Test
    fun `stranded pet teleports to its station`() = runBlocking {
        val f = Fx()
        val o = f.owner()
        val pet = f.pet(o)
        pet.state = pet.state.copy(pos = Vec3(200f, 5f, 200f))

        f.coordinator.tick(f.sessions, combat(f.npcManager))

        assertEquals(-1.3f, pet.state.pos.x, 0.01f)
        assertEquals(0f, pet.state.pos.z, 0.01f)
        assertNull(pet.chaseTargetPos)
    }
}
