package org.micoli.micraft.game.armor

import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlinx.coroutines.runBlocking
import org.micoli.micraft.game.npc.NpcDefinition
import org.micoli.micraft.game.npc.NpcInstance
import org.micoli.micraft.game.npc.behaviors.StaticNpcBehavior
import org.micoli.micraft.npc.NpcState
import org.micoli.micraft.player.Vec3
import org.micoli.micraft.protocol.ServerMessage
import org.micoli.micraft.support.testSession

private val ARMOR = mapOf("iron_chest" to ArmorDefinition(wearable = WearableSlots(body = true)))

private fun killedNpc(contributorId: String, armorLoot: List<ArmorDropEntry>): NpcInstance {
    val def =
        NpcDefinition(
            type = "wolf",
            behavior = StaticNpcBehavior(),
            bbmodelFile = "npc",
            width = 0.6f,
            height = 1.8f,
            wanderSpeed = 0f,
            wanderRadius = 0f,
            armorLoot = armorLoot,
        )
    val pos = Vec3(0f, 0f, 0f)
    val instance =
        NpcInstance(
            state = NpcState(id = "npc-1", name = "Wolf", type = "wolf", pos = pos, yaw = 0f),
            definition = def,
            spawnPos = pos,
        )
    instance.damageContributors[contributorId] = 10
    return instance
}

class ArmorLootGranterTest {
    @Test
    fun guaranteedDrop_grantsArmorToContributor() = runBlocking {
        val session = testSession()
        val npc =
            killedNpc(session.id, listOf(ArmorDropEntry(armor = "iron_chest", dropRate = 100)))
        ArmorLootGranter.grant(npc, ARMOR, { listOf(session) }, savePlayer = {})
        assertTrue("iron_chest" in session.state.ownedArmors)
        assertTrue(session.sent.filterIsInstance<ServerMessage.Notification>().isNotEmpty())
    }

    @Test
    fun zeroDropRate_grantsNothing() = runBlocking {
        val session = testSession()
        val npc = killedNpc(session.id, listOf(ArmorDropEntry(armor = "iron_chest", dropRate = 0)))
        ArmorLootGranter.grant(npc, ARMOR, { listOf(session) }, savePlayer = {}, random = Random(1))
        assertTrue("iron_chest" !in session.state.ownedArmors)
    }

    @Test
    fun alreadyOwned_doesNotDuplicate() = runBlocking {
        val session = testSession()
        session.state = session.state.copy(ownedArmors = listOf("iron_chest"))
        val npc =
            killedNpc(session.id, listOf(ArmorDropEntry(armor = "iron_chest", dropRate = 100)))
        ArmorLootGranter.grant(npc, ARMOR, { listOf(session) }, savePlayer = {})
        assertEquals(1, session.state.ownedArmors.count { it == "iron_chest" })
    }

    @Test
    fun noContributor_grantsNothing() = runBlocking {
        val session = testSession()
        val def =
            NpcDefinition(
                type = "wolf",
                behavior = StaticNpcBehavior(),
                bbmodelFile = "npc",
                width = 0.6f,
                height = 1.8f,
                wanderSpeed = 0f,
                wanderRadius = 0f,
                armorLoot = listOf(ArmorDropEntry(armor = "iron_chest", dropRate = 100)),
            )
        val pos = Vec3(0f, 0f, 0f)
        val npc =
            NpcInstance(
                state = NpcState(id = "npc-1", name = "Wolf", type = "wolf", pos = pos, yaw = 0f),
                definition = def,
                spawnPos = pos,
            )
        ArmorLootGranter.grant(npc, ARMOR, { listOf(session) }, savePlayer = {})
        assertTrue("iron_chest" !in session.state.ownedArmors)
    }
}
