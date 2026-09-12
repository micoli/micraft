package org.micoli.micraft.command.commands

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlinx.coroutines.runBlocking
import org.micoli.micraft.game.armor.ArmorDefinition
import org.micoli.micraft.game.armor.ArmorType
import org.micoli.micraft.game.armor.WearableSlots
import org.micoli.micraft.game.session.PlayerSession
import org.micoli.micraft.player.rpg.BaseStats
import org.micoli.micraft.player.rpg.CharacterClass
import org.micoli.micraft.player.rpg.CharacterData
import org.micoli.micraft.protocol.ServerMessage
import org.micoli.micraft.support.testContext
import org.micoli.micraft.support.testSession

private val HELMET = ArmorDefinition(wearable = WearableSlots(head = true))
private val HELMET2 = ArmorDefinition(wearable = WearableSlots(head = true))
private val CHEST = ArmorDefinition(wearable = WearableSlots(body = true))
private val PLATE_CHEST =
    ArmorDefinition(
        wearable = WearableSlots(body = true), armorType = ArmorType.PLATE, requiredLevel = 10)
private val TEST_ARMORS =
    mapOf(
        "iron_helmet" to HELMET,
        "steel_helmet" to HELMET2,
        "iron_chest" to CHEST,
        "plate_chest" to PLATE_CHEST,
    )

private fun charData(level: Int, characterClass: CharacterClass) =
    CharacterData(
        id = "char-1",
        name = "Alice",
        characterClass = characterClass,
        level = level,
        baseStats = BaseStats(),
        currentHp = 10,
        currentMana = 10,
    )

class EquipCommandTest {
    private val cmd = EquipCommand()

    @Test
    fun blankArgs_sendsUsage() = runBlocking {
        val session = testSession()
        cmd.execute(session, "", testContext(armorRegistry = { TEST_ARMORS }))
        assertTrue(session.sent.filterIsInstance<ServerMessage.Notification>().isNotEmpty())
    }

    @Test
    fun unknownArmor_sendsUnknown() = runBlocking {
        val session = testSession()
        cmd.execute(session, "dragon_cape", testContext(armorRegistry = { TEST_ARMORS }))
        val notifs = session.sent.filterIsInstance<ServerMessage.Notification>()
        assertTrue(
            notifs.any {
                it.message.contains("dragon_cape") ||
                    it.message.contains("unknown") ||
                    it.message.contains("inconnu")
            })
    }

    @Test
    fun notOwned_sendsNotOwned() = runBlocking {
        val session = testSession()
        cmd.execute(session, "iron_helmet", testContext(armorRegistry = { TEST_ARMORS }))
        val notifs = session.sent.filterIsInstance<ServerMessage.Notification>()
        assertTrue(notifs.any { it.message.contains("iron_helmet") })
        assertTrue("iron_helmet" !in session.state.armors)
    }

    @Test
    fun alreadyWearing_sendsAlready() = runBlocking {
        val session = testSession()
        session.state =
            session.state.copy(armors = listOf("iron_helmet"), ownedArmors = listOf("iron_helmet"))
        cmd.execute(session, "iron_helmet", testContext(armorRegistry = { TEST_ARMORS }))
        val notifs = session.sent.filterIsInstance<ServerMessage.Notification>()
        assertTrue(
            notifs.any {
                it.message.contains("iron_helmet") ||
                    it.message.contains("already") ||
                    it.message.contains("déjà")
            })
    }

    @Test
    fun slotConflict_sendsOverlap() = runBlocking {
        val session = testSession()
        session.state =
            session.state.copy(
                armors = listOf("iron_helmet"), ownedArmors = listOf("iron_helmet", "steel_helmet"))
        cmd.execute(session, "steel_helmet", testContext(armorRegistry = { TEST_ARMORS }))
        val notifs = session.sent.filterIsInstance<ServerMessage.Notification>()
        assertTrue(
            notifs.any {
                it.message.contains("steel_helmet") ||
                    it.message.contains("iron_helmet") ||
                    it.message.contains("overlap") ||
                    it.message.contains("conflit")
            })
    }

    @Test
    fun success_addsArmorToState() = runBlocking {
        val session = testSession()
        session.state = session.state.copy(ownedArmors = listOf("iron_helmet"))
        val saved = mutableListOf<PlayerSession>()
        cmd.execute(
            session,
            "iron_helmet",
            testContext(armorRegistry = { TEST_ARMORS }, savePlayer = { saved.add(it) }))
        assertTrue("iron_helmet" in session.state.armors)
    }

    @Test
    fun success_callsSavePlayer() = runBlocking {
        val session = testSession()
        session.state = session.state.copy(ownedArmors = listOf("iron_helmet"))
        val saved = mutableListOf<PlayerSession>()
        cmd.execute(
            session,
            "iron_helmet",
            testContext(armorRegistry = { TEST_ARMORS }, savePlayer = { saved.add(it) }))
        assertEquals(1, saved.size)
    }

    @Test
    fun levelTooLow_refusesEquip() = runBlocking {
        val session = testSession()
        session.state = session.state.copy(ownedArmors = listOf("plate_chest"))
        session.characterData = charData(level = 5, characterClass = CharacterClass.WARRIOR)
        cmd.execute(session, "plate_chest", testContext(armorRegistry = { TEST_ARMORS }))
        assertFalse("plate_chest" in session.state.armors)
    }

    @Test
    fun wrongArmorTypeForClass_refusesEquip() = runBlocking {
        val session = testSession()
        session.state = session.state.copy(ownedArmors = listOf("plate_chest"))
        session.characterData = charData(level = 20, characterClass = CharacterClass.MAGE)
        cmd.execute(session, "plate_chest", testContext(armorRegistry = { TEST_ARMORS }))
        assertFalse("plate_chest" in session.state.armors)
    }

    @Test
    fun levelAndClassOk_allowsEquip() = runBlocking {
        val session = testSession()
        session.state = session.state.copy(ownedArmors = listOf("plate_chest"))
        session.characterData = charData(level = 20, characterClass = CharacterClass.WARRIOR)
        cmd.execute(session, "plate_chest", testContext(armorRegistry = { TEST_ARMORS }))
        assertTrue("plate_chest" in session.state.armors)
    }

    @Test
    fun success_broadcastsPlayerUpdate() = runBlocking {
        val session = testSession()
        session.state = session.state.copy(ownedArmors = listOf("iron_helmet"))
        val broadcasts = mutableListOf<ServerMessage>()
        cmd.execute(
            session,
            "iron_helmet",
            testContext(armorRegistry = { TEST_ARMORS }, broadcast = { broadcasts.add(it) }))
        assertTrue(broadcasts.filterIsInstance<ServerMessage.PlayerUpdate>().isNotEmpty())
    }
}
