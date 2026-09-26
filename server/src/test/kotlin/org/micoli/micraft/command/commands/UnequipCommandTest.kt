package org.micoli.micraft.command.commands

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlinx.coroutines.runBlocking
import org.micoli.micraft.game.armor.ArmorDefinition
import org.micoli.micraft.game.armor.WearableSlots
import org.micoli.micraft.game.rpg.StatBonus
import org.micoli.micraft.player.rpg.BaseStats
import org.micoli.micraft.player.rpg.CharacterClass
import org.micoli.micraft.player.rpg.CharacterData
import org.micoli.micraft.protocol.ServerMessage
import org.micoli.micraft.support.testContext
import org.micoli.micraft.support.testSession

private val CON_CHEST =
    ArmorDefinition(wearable = WearableSlots(body = true), statBonus = StatBonus(con = 4))

class UnequipCommandTest {
    private val cmd = UnequipCommand()

    @Test
    fun `unequipping CON armor at full hp clamps hp to the new max`() = runBlocking {
        val session = testSession()
        session.state =
            session.state.copy(armors = listOf("con_chest"), ownedArmors = listOf("con_chest"))
        session.characterData =
            CharacterData(
                id = "char-1",
                name = "Alice",
                characterClass = CharacterClass.MAGE,
                level = 5,
                baseStats = BaseStats(con = 10),
                currentHp = 20, // max with the chest: floor((14-10)/2) * 5 + 10
                currentMana = 10,
            )

        cmd.execute(
            session, "con_chest", testContext(armorRegistry = { mapOf("con_chest" to CON_CHEST) }))

        assertEquals(10, session.characterData!!.currentHp)
        val sync = session.sent.filterIsInstance<ServerMessage.CharacterSync>().single()
        assertEquals(10, sync.derived.maxHp)
        assertEquals(
            10, session.sent.filterIsInstance<ServerMessage.PlayerStatusUpdate>().single().maxHp)
    }
}
