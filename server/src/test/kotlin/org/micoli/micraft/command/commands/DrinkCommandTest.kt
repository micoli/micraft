package org.micoli.micraft.command.commands

import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlinx.coroutines.runBlocking
import org.micoli.micraft.game.armor.ArmorDefinition
import org.micoli.micraft.game.armor.WearableSlots
import org.micoli.micraft.game.rpg.StatBonus
import org.micoli.micraft.game.world.ItemDefinition
import org.micoli.micraft.game.world.ItemRegistry
import org.micoli.micraft.game.world.ItemType
import org.micoli.micraft.player.rpg.BaseStats
import org.micoli.micraft.player.rpg.CharacterClass
import org.micoli.micraft.player.rpg.CharacterData
import org.micoli.micraft.protocol.ServerMessage
import org.micoli.micraft.support.testContext
import org.micoli.micraft.support.testSession
import org.micoli.micraft.support.testWorld

private val POTION = ItemType("TEST_HEALTH_POTION")
private val CON_CHEST =
    ArmorDefinition(wearable = WearableSlots(body = true), statBonus = StatBonus(con = 4))

class DrinkCommandTest {
    private lateinit var savedItems: Map<ItemType, ItemDefinition>

    @BeforeTest
    fun setUp() {
        testWorld()
        savedItems = ItemRegistry.keys().associateWith { ItemRegistry.get(it) }
        ItemRegistry.load(
            savedItems + mapOf(POTION to ItemDefinition(healthRestore = 50, consumable = true)))
    }

    @AfterTest
    fun tearDown() {
        ItemRegistry.load(savedItems)
    }

    @Test
    fun `drinking while wearing CON armor heals up to the armored max hp`() = runBlocking {
        val session = testSession()
        session.state = session.state.copy(armors = listOf("con_chest"))
        session.inventory[POTION] = 1
        session.characterData =
            CharacterData(
                id = "char-1",
                name = "Alice",
                characterClass = CharacterClass.MAGE,
                level = 5,
                baseStats = BaseStats(con = 10),
                currentHp = 1,
                currentMana = 10,
            )

        DrinkCommand()
            .execute(
                session,
                POTION.id,
                testContext(armorRegistry = { mapOf("con_chest" to CON_CHEST) }))

        assertEquals(20, session.characterData!!.currentHp) // floor((14-10)/2) * 5 + 10
        assertEquals(
            20, session.sent.filterIsInstance<ServerMessage.PlayerStatusUpdate>().single().maxHp)
    }
}
