package org.micoli.micraft.game.equipment

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue
import kotlinx.coroutines.runBlocking
import org.micoli.micraft.game.armor.ArmorDefinition
import org.micoli.micraft.game.armor.ArmorType
import org.micoli.micraft.game.armor.WearableSlots
import org.micoli.micraft.game.rpg.CharacterStats
import org.micoli.micraft.game.rpg.StatBonus
import org.micoli.micraft.game.world.EquipmentCategory
import org.micoli.micraft.player.Hand
import org.micoli.micraft.player.rpg.BaseStats
import org.micoli.micraft.player.rpg.CharacterClass
import org.micoli.micraft.player.rpg.CharacterData
import org.micoli.micraft.protocol.ServerMessage
import org.micoli.micraft.support.testPlayerState
import org.micoli.micraft.support.testSession

private val CATALOG =
    EquipmentCatalog(
        armors =
            mapOf(
                "iron_helmet" to
                    ArmorDefinition(
                        wearable = WearableSlots(head = true), armorType = ArmorType.MAIL),
                "steel_helmet" to
                    ArmorDefinition(
                        wearable = WearableSlots(head = true), armorType = ArmorType.MAIL),
                "plate_chest" to
                    ArmorDefinition(
                        wearable = WearableSlots(body = true),
                        armorType = ArmorType.PLATE,
                        requiredLevel = 10)),
        weapons =
            mapOf(
                "iron_sword" to WeaponDefinition(category = EquipmentCategory.SWORD),
                "con_sword" to
                    WeaponDefinition(
                        category = EquipmentCategory.SWORD, statBonus = StatBonus(con = 4))),
        tools = mapOf("iron_axe" to ToolDefinition(category = EquipmentCategory.AXE)),
        weaponCategories =
            mapOf(
                EquipmentCategory.SWORD to
                    WeaponCategoryDefinition(
                        allowedClasses = setOf(CharacterClass.WARRIOR), mainHandOnly = true)),
    )

private fun character(level: Int = 5, characterClass: CharacterClass = CharacterClass.WARRIOR) =
    CharacterData(
        id = "char-1",
        name = "Alice",
        characterClass = characterClass,
        level = level,
        baseStats = BaseStats(con = 10),
        currentHp = 10,
        currentMana = 10,
    )

class LoadoutTest {
    private val broadcasts = mutableListOf<ServerMessage>()
    private val saved = mutableListOf<String>()
    private val loadout =
        Loadout(
            CATALOG,
            CharacterStats(CATALOG),
            broadcast = { broadcasts += it },
            savePlayer = { saved += it.id })

    private fun owning(vararg items: String) =
        testSession().also {
            it.state =
                it.state.copy(
                    ownedArmors = items.filter { i -> i in CATALOG.armors },
                    ownedWeapons = items.filter { i -> i in CATALOG.weapons },
                    ownedTools = items.filter { i -> i in CATALOG.tools })
            it.characterData = character()
        }

    @Test
    fun `grant adds each item kind to its owned list`() {
        val state = testPlayerState()

        val armor = assertIs<GrantResult.Granted>(loadout.grant(state, "iron_helmet"))
        val weapon = assertIs<GrantResult.Granted>(loadout.grant(state, "iron_sword"))
        val tool = assertIs<GrantResult.Granted>(loadout.grant(state, "iron_axe"))

        assertEquals(listOf("iron_helmet"), armor.state.ownedArmors)
        assertEquals(listOf("iron_sword"), weapon.state.ownedWeapons)
        assertEquals(listOf("iron_axe"), tool.state.ownedTools)
    }

    @Test
    fun `grant resolves the name case-insensitively`() {
        val granted = assertIs<GrantResult.Granted>(loadout.grant(testPlayerState(), "IRON_Sword"))

        assertEquals("iron_sword", granted.name)
        assertEquals(listOf("iron_sword"), granted.state.ownedWeapons)
    }

    @Test
    fun `grant refuses an item already owned or unknown`() {
        val state = testPlayerState().copy(ownedArmors = listOf("iron_helmet"))

        assertEquals(GrantResult.AlreadyOwned("iron_helmet"), loadout.grant(state, "iron_helmet"))
        assertEquals(GrantResult.Unknown, loadout.grant(state, "dragon_cape"))
    }

    @Test
    fun `grantArmors grants exact armor names only, skipping owned ones`() {
        val state = testPlayerState().copy(ownedArmors = listOf("iron_helmet"))

        val updated =
            loadout.grantArmors(
                state, listOf("iron_helmet", "steel_helmet", "iron_sword", "IRON_HELMET"))

        assertEquals(listOf("iron_helmet", "steel_helmet"), updated.ownedArmors)
        assertEquals(emptyList(), updated.ownedWeapons)
    }

    @Test
    fun `equip wears the armor, broadcasts, saves and resyncs`() = runBlocking {
        val session = owning("iron_helmet")

        assertEquals(EquipResult.Equipped, loadout.equip(session, "iron_helmet"))

        assertEquals(listOf("iron_helmet"), session.state.armors)
        assertTrue(broadcasts.single() is ServerMessage.PlayerUpdate)
        assertEquals(listOf(session.id), saved)
        assertTrue(session.sent.any { it is ServerMessage.CharacterSync })
    }

    @Test
    fun `equip refusals leave the loadout untouched`() = runBlocking {
        val session = owning("iron_helmet", "steel_helmet", "plate_chest")
        session.state = session.state.copy(armors = listOf("iron_helmet"))

        assertEquals(EquipResult.Unknown, loadout.equip(session, "dragon_cape"))
        assertEquals(EquipResult.NotOwned, owning().let { loadout.equip(it, "iron_helmet") })
        assertEquals(EquipResult.AlreadyWorn, loadout.equip(session, "iron_helmet"))
        assertEquals(EquipResult.Overlap("iron_helmet"), loadout.equip(session, "steel_helmet"))
        assertEquals(
            EquipResult.LevelTooLow(required = 10, actual = 5),
            loadout.equip(session, "plate_chest"))
        session.characterData = character(level = 10, characterClass = CharacterClass.MAGE)
        assertEquals(
            EquipResult.WrongArmorType(ArmorType.PLATE), loadout.equip(session, "plate_chest"))

        assertEquals(listOf("iron_helmet"), session.state.armors)
        assertTrue(saved.isEmpty())
    }

    @Test
    fun `unequip removes the armor and resyncs`() = runBlocking {
        val session = owning("iron_helmet")
        session.state = session.state.copy(armors = listOf("iron_helmet"))

        assertEquals(UnequipResult.Unequipped, loadout.unequip(session, "iron_helmet"))
        assertEquals(UnequipResult.NotWorn, loadout.unequip(session, "iron_helmet"))

        assertEquals(emptyList(), session.state.armors)
        assertTrue(session.sent.any { it is ServerMessage.CharacterSync })
    }

    @Test
    fun `wielding a weapon with a CON bonus syncs stats that include it`() = runBlocking {
        val session = owning("con_sword")

        assertEquals(WieldResult.Wielded(Hand.RIGHT), loadout.wield(session, "con_sword"))

        assertEquals("con_sword", session.state.rightHandItem)
        val sync = session.sent.filterIsInstance<ServerMessage.CharacterSync>().single()
        assertEquals(14, sync.effectiveBaseStats.con)
    }

    @Test
    fun `wield refusals`() = runBlocking {
        val session = owning("iron_sword", "iron_axe")

        assertEquals(WieldResult.Unknown, loadout.wield(session, "dragon_cape"))
        assertEquals(WieldResult.NotOwned, owning().let { loadout.wield(it, "iron_sword") })
        assertEquals(WieldResult.WrongHand, loadout.wield(session, "iron_sword", Hand.LEFT))
        session.characterData = character(characterClass = CharacterClass.MAGE)
        assertEquals(WieldResult.WrongClass, loadout.wield(session, "iron_sword"))
        session.state = session.state.copy(rightHandItem = "iron_axe", leftHandItem = "iron_axe")
        assertEquals(WieldResult.HandsFull, loadout.wield(session, "iron_axe"))
    }

    @Test
    fun `unwield empties the hand and resyncs`() = runBlocking {
        val session = owning("con_sword")
        session.state = session.state.copy(rightHandItem = "con_sword")

        assertEquals(UnwieldResult.Unwielded("con_sword"), loadout.unwield(session, Hand.RIGHT))
        assertEquals(UnwieldResult.Empty, loadout.unwield(session, Hand.RIGHT))

        assertEquals(null, session.state.rightHandItem)
        assertEquals(
            10,
            session.sent
                .filterIsInstance<ServerMessage.CharacterSync>()
                .first()
                .effectiveBaseStats
                .con)
    }
}
