package org.micoli.micraft.game.pet

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.runBlocking
import org.micoli.micraft.game.npc.NpcDefinition
import org.micoli.micraft.game.npc.NpcManager
import org.micoli.micraft.game.npc.behaviors.RandomMovableNpcBehavior
import org.micoli.micraft.game.rpg.ExperienceConfig
import org.micoli.micraft.game.rpg.ExperienceProcessor
import org.micoli.micraft.protocol.ServerMessage
import org.micoli.micraft.support.FakePlayerSession
import org.micoli.micraft.support.testI18n
import org.micoli.micraft.support.testSession

private fun petDef(type: String = "wolf") =
    NpcDefinition(
        type = type,
        behavior = RandomMovableNpcBehavior(),
        bbmodelFile = "npc",
        width = 0.5f,
        height = 0.9f,
        wanderSpeed = 2f,
        wanderRadius = 20f,
        hp = 20,
        tameable = true,
    )

private class Fixture {
    val sessions = mutableListOf<FakePlayerSession>()
    val npcManager =
        NpcManager(broadcast = {}, getSessions = { sessions }).apply {
            loadDefinitions(mapOf("wolf" to petDef()))
        }
    val xp = ExperienceProcessor(ExperienceConfig().data, { sessions }, {})
    val saved = mutableListOf<String>()
    val manager =
        PetManager(
            npcManager = npcManager,
            experienceProcessor = xp,
            getSessions = { sessions },
            savePlayer = { saved.add(it.id) },
            i18n = testI18n(),
        )

    fun session(): FakePlayerSession = testSession().also { sessions.add(it) }
}

class PetManagerTest {
    @Test
    fun `summon marks pet active and spawns one npc`() = runBlocking {
        val f = Fixture()
        val s = f.session()
        val record = f.manager.addTamed(s, "wolf", "Rex", 3, 0)

        f.manager.summon(s, "Rex")

        assertEquals(record.id, s.state.activePetId)
        val pets = f.npcManager.ownedPets()
        assertEquals(1, pets.size)
        assertEquals(s.id, pets[0].ownerId)
        assertEquals(3, pets[0].instanceLevel)
    }

    @Test
    fun `summon retires the previously active pet`() = runBlocking {
        val f = Fixture()
        val s = f.session()
        f.manager.addTamed(s, "wolf", "Rex", 2, 0)
        f.manager.addTamed(s, "wolf", "Fido", 2, 0)

        f.manager.summon(s, "Rex")
        f.manager.summon(s, "Fido")

        assertEquals(1, f.npcManager.ownedPets().size)
        assertEquals("Fido", f.npcManager.ownedPets()[0].state.name)
    }

    @Test
    fun `dismiss persists level xp and hp to the record`() = runBlocking {
        val f = Fixture()
        val s = f.session()
        f.manager.addTamed(s, "wolf", "Rex", 3, 10)
        f.manager.summon(s, "Rex")
        val inst = f.npcManager.ownedPets()[0]
        inst.currentHp = 7
        inst.xp = 42

        f.manager.dismiss(s)

        assertNull(s.state.activePetId)
        val rec = s.state.pets.first { it.name == "Rex" }
        assertEquals(7, rec.currentHp)
        assertEquals(42, rec.xp)
        assertTrue(f.npcManager.ownedPets().isEmpty())
    }

    @Test
    fun `re-summon restores saved hp instead of healing`() = runBlocking {
        val f = Fixture()
        val s = f.session()
        f.manager.addTamed(s, "wolf", "Rex", 3, 0)
        f.manager.summon(s, "Rex")
        f.npcManager.ownedPets()[0].currentHp = 5
        f.manager.dismiss(s)

        f.manager.summon(s, "Rex")

        assertEquals(5, f.npcManager.ownedPets()[0].currentHp)
    }

    @Test
    fun `pet death starts resurrect cooldown and clears active slot`() = runBlocking {
        val f = Fixture()
        val s = f.session()
        f.manager.addTamed(s, "wolf", "Rex", 3, 0)
        f.manager.summon(s, "Rex")
        val inst = f.npcManager.ownedPets()[0]

        f.manager.onPetDied(inst)

        val rec = s.state.pets.first { it.name == "Rex" }
        assertTrue(rec.dead)
        assertTrue(rec.resurrectReadyAtMs > System.currentTimeMillis())
        assertNull(s.state.activePetId)
    }

    @Test
    fun `resurrect refused while on cooldown, allowed after`() = runBlocking {
        val f = Fixture()
        val s = f.session()
        f.manager.addTamed(s, "wolf", "Rex", 3, 0)
        s.state =
            s.state.copy(
                pets =
                    s.state.pets.map {
                        it.copy(
                            dead = true, resurrectReadyAtMs = System.currentTimeMillis() + 60_000)
                    })
        s.sent.clear()
        f.manager.resurrect(s, "Rex")
        assertTrue(s.state.pets.first().dead)

        s.state = s.state.copy(pets = s.state.pets.map { it.copy(resurrectReadyAtMs = 1L) })
        f.manager.resurrect(s, "Rex")
        assertFalse(s.state.pets.first().dead)
    }

    @Test
    fun `rename updates record and live instance`() = runBlocking {
        val f = Fixture()
        val s = f.session()
        f.manager.addTamed(s, "wolf", "Rex", 3, 0)
        f.manager.summon(s, "Rex")

        f.manager.rename(s, "Rex", "Bones")

        assertEquals("Bones", s.state.pets.first().name)
        assertEquals("Bones", f.npcManager.ownedPets()[0].state.name)
    }

    @Test
    fun `rename rejects a duplicate name`() = runBlocking {
        val f = Fixture()
        val s = f.session()
        f.manager.addTamed(s, "wolf", "Rex", 3, 0)
        f.manager.addTamed(s, "wolf", "Fido", 3, 0)

        f.manager.rename(s, "Rex", "Fido")

        assertEquals("Rex", s.state.pets.first { it.id == s.state.pets[0].id }.name)
    }

    @Test
    fun `disconnect dismisses the active pet`() = runBlocking {
        val f = Fixture()
        val s = f.session()
        f.manager.addTamed(s, "wolf", "Rex", 3, 0)
        f.manager.summon(s, "Rex")

        f.manager.onPlayerDisconnected(s)

        assertNull(s.state.activePetId)
        assertTrue(f.npcManager.ownedPets().isEmpty())
    }

    @Test
    fun `roster sync lists every pet`() = runBlocking {
        val f = Fixture()
        val s = f.session()
        f.manager.addTamed(s, "wolf", "Rex", 3, 0)
        s.sent.clear()

        f.manager.rosterSyncFor(s)

        val sync = s.sent.filterIsInstance<ServerMessage.PetRosterSync>().last()
        assertEquals(1, sync.pets.size)
        assertEquals("Rex", sync.pets[0].name)
    }
}
