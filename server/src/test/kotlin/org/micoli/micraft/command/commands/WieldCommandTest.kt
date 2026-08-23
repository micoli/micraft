package org.micoli.micraft.command.commands

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.runBlocking
import org.micoli.micraft.game.equipment.ToolCategoryDefinition
import org.micoli.micraft.game.equipment.ToolDefinition
import org.micoli.micraft.game.equipment.WeaponCategoryDefinition
import org.micoli.micraft.game.equipment.WeaponDefinition
import org.micoli.micraft.game.world.EquipmentCategory
import org.micoli.micraft.player.Hand
import org.micoli.micraft.player.rpg.BaseStats
import org.micoli.micraft.player.rpg.CharacterClass
import org.micoli.micraft.player.rpg.CharacterData
import org.micoli.micraft.protocol.ServerMessage
import org.micoli.micraft.support.testContext
import org.micoli.micraft.support.testSession

private val WEAPONS = mapOf("iron_sword" to WeaponDefinition(category = EquipmentCategory.SWORD))
private val WEAPON_CATEGORIES =
    mapOf(
        EquipmentCategory.SWORD to
            WeaponCategoryDefinition(
                allowedClasses = setOf(CharacterClass.WARRIOR), mainHandOnly = false),
        EquipmentCategory.STAFF to
            WeaponCategoryDefinition(
                allowedClasses = setOf(CharacterClass.CLERIC), mainHandOnly = true),
    )
private val TOOLS = mapOf("iron_axe" to ToolDefinition(category = EquipmentCategory.AXE))
private val TOOL_CATEGORIES =
    mapOf(EquipmentCategory.AXE to ToolCategoryDefinition(mainHandOnly = false))

private fun charData(characterClass: CharacterClass) =
    CharacterData(
        id = "c1",
        name = "Alice",
        characterClass = characterClass,
        baseStats = BaseStats(),
        currentHp = 10,
        currentMana = 10,
    )

class WieldCommandTest {
    private val cmd = WieldCommand()

    @Test
    fun blankArgs_sendsUsage() = runBlocking {
        val session = testSession()
        cmd.execute(session, "", testContext())
        assertTrue(session.sent.filterIsInstance<ServerMessage.Notification>().isNotEmpty())
    }

    @Test
    fun unknownItem_sendsUnknown() = runBlocking {
        val session = testSession()
        cmd.execute(
            session,
            "dragon_blade",
            testContext(weaponRegistry = { WEAPONS }, toolRegistry = { TOOLS }))
        val notifs = session.sent.filterIsInstance<ServerMessage.Notification>()
        assertTrue(notifs.any { it.message.contains("dragon_blade") })
    }

    @Test
    fun notOwned_refused() = runBlocking {
        val session = testSession()
        session.characterData = charData(CharacterClass.WARRIOR)
        cmd.execute(
            session,
            "iron_sword",
            testContext(
                weaponRegistry = { WEAPONS },
                toolRegistry = { TOOLS },
                weaponCategories = { WEAPON_CATEGORIES },
                toolCategories = { TOOL_CATEGORIES }))
        assertNull(session.state.rightHandItem)
        assertNull(session.state.leftHandItem)
        val notifs = session.sent.filterIsInstance<ServerMessage.Notification>()
        assertTrue(notifs.any { it.message.contains("iron_sword") })
    }

    @Test
    fun wrongClass_refused() = runBlocking {
        val session = testSession()
        session.characterData = charData(CharacterClass.MAGE)
        cmd.execute(
            session,
            "iron_sword",
            testContext(
                weaponRegistry = { WEAPONS },
                toolRegistry = { TOOLS },
                weaponCategories = { WEAPON_CATEGORIES },
                toolCategories = { TOOL_CATEGORIES }))
        assertNull(session.state.rightHandItem)
        assertNull(session.state.leftHandItem)
    }

    @Test
    fun tool_noClassRestriction_succeedsForAnyClass() = runBlocking {
        val session = testSession()
        session.characterData = charData(CharacterClass.MAGE)
        session.state = session.state.copy(ownedTools = listOf("iron_axe"))
        cmd.execute(
            session,
            "iron_axe",
            testContext(
                weaponRegistry = { WEAPONS },
                toolRegistry = { TOOLS },
                weaponCategories = { WEAPON_CATEGORIES },
                toolCategories = { TOOL_CATEGORIES }))
        assertEquals("iron_axe", session.state.leftHandItem)
    }

    @Test
    fun defaultHand_isOffHand_whenDominantIsRight() = runBlocking {
        val session = testSession()
        session.characterData = charData(CharacterClass.WARRIOR)
        session.state = session.state.copy(ownedWeapons = listOf("iron_sword"))
        cmd.execute(
            session,
            "iron_sword",
            testContext(
                weaponRegistry = { WEAPONS },
                toolRegistry = { TOOLS },
                weaponCategories = { WEAPON_CATEGORIES },
                toolCategories = { TOOL_CATEGORIES }))
        assertEquals("iron_sword", session.state.leftHandItem)
        assertNull(session.state.rightHandItem)
    }

    @Test
    fun mainHandOnly_explicitOffHand_refused() = runBlocking {
        val session = testSession()
        session.state = session.state.copy(dominantHand = Hand.RIGHT)
        session.characterData = charData(CharacterClass.CLERIC)
        cmd.execute(
            session,
            "holy_staff left",
            testContext(
                weaponRegistry = {
                    mapOf("holy_staff" to WeaponDefinition(category = EquipmentCategory.STAFF))
                },
                weaponCategories = { WEAPON_CATEGORIES },
                toolCategories = { TOOL_CATEGORIES }))
        assertNull(session.state.leftHandItem)
        assertNull(session.state.rightHandItem)
    }

    @Test
    fun mainHandOnly_dominantHand_succeeds() = runBlocking {
        val session = testSession()
        session.state =
            session.state.copy(dominantHand = Hand.RIGHT, ownedWeapons = listOf("holy_staff"))
        session.characterData = charData(CharacterClass.CLERIC)
        cmd.execute(
            session,
            "holy_staff",
            testContext(
                weaponRegistry = {
                    mapOf("holy_staff" to WeaponDefinition(category = EquipmentCategory.STAFF))
                },
                weaponCategories = { WEAPON_CATEGORIES },
                toolCategories = { TOOL_CATEGORIES }))
        assertEquals("holy_staff", session.state.rightHandItem)
    }

    @Test
    fun success_callsSavePlayerAndBroadcasts() = runBlocking {
        val session = testSession()
        session.characterData = charData(CharacterClass.WARRIOR)
        session.state = session.state.copy(ownedWeapons = listOf("iron_sword"))
        var saved = 0
        val broadcasts = mutableListOf<ServerMessage>()
        cmd.execute(
            session,
            "iron_sword",
            testContext(
                weaponRegistry = { WEAPONS },
                toolRegistry = { TOOLS },
                weaponCategories = { WEAPON_CATEGORIES },
                toolCategories = { TOOL_CATEGORIES },
                savePlayer = { saved++ },
                broadcast = { broadcasts.add(it) }))
        assertEquals(1, saved)
        assertTrue(broadcasts.filterIsInstance<ServerMessage.PlayerUpdate>().isNotEmpty())
    }
}
