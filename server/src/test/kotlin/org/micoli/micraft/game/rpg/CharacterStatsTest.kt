package org.micoli.micraft.game.rpg

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlinx.coroutines.runBlocking
import org.micoli.micraft.combat.StatusEffect
import org.micoli.micraft.game.armor.ArmorDefinition
import org.micoli.micraft.game.armor.WearableSlots
import org.micoli.micraft.player.rpg.BaseStats
import org.micoli.micraft.player.rpg.CharacterClass
import org.micoli.micraft.player.rpg.CharacterData
import org.micoli.micraft.protocol.ServerMessage
import org.micoli.micraft.support.testPlayerState
import org.micoli.micraft.support.testSession

private val CON_CHEST =
    ArmorDefinition(
        wearable = WearableSlots(body = true), statBonus = StatBonus(con = 4, acBonus = 3))

class CharacterStatsTest {

    private val stats = CharacterStats(armorRegistry = mapOf("con_chest" to CON_CHEST))

    private fun character(level: Int = 5) =
        CharacterData(
            id = "c1",
            name = "Hero",
            characterClass = CharacterClass.WARRIOR,
            level = level,
            baseStats = BaseStats(10, 10, 10, 10, 10, 10),
            currentHp = 10,
            currentMana = 10)

    @Test
    fun `worn armor adds its bonuses to effective stats and derived stats`() {
        val state = testPlayerState().copy(armors = listOf("con_chest"))

        val sheet = stats.of(character(level = 5), state)

        assertEquals(14, sheet.effective.con)
        assertEquals(20, sheet.derived.maxHp) // floor((14-10)/2) * 5 + 10
        assertEquals(13, sheet.derived.armorClass) // 10 + acBonus 3 + dex mod 0
    }

    @Test
    fun `stat-affecting effects raise max hp, max mana and regen`() {
        val none = stats.of(character(), testPlayerState())
        val boosted =
            stats.of(
                character(),
                testPlayerState(),
                listOf(
                    StatusEffect.HpBoost,
                    StatusEffect.ManaBoost,
                    StatusEffect.HpRegenBoost,
                    StatusEffect.ManaRegenBoost))

        assertEquals(none.derived.maxHp + 20, boosted.derived.maxHp)
        assertEquals(none.derived.maxMana + 20, boosted.derived.maxMana)
        assertEquals(1.1f, boosted.derived.hpRegenPerSec) // 10 / 10 * 1.1
        assertEquals(0.55f, boosted.derived.manaRegenPerSec) // 10 / 20 * 1.1
    }

    @Test
    fun `effects that do not touch stats leave derived stats unchanged`() {
        val none = stats.of(character(), testPlayerState())
        val poisoned = stats.of(character(), testPlayerState(), listOf(StatusEffect.Poisoned))

        assertEquals(none.derived, poisoned.derived)
    }

    @Test
    fun `resync sends character sync and status update built from the same stats`() = runBlocking {
        val session = testSession()
        session.state = session.state.copy(armors = listOf("con_chest"))
        session.characterData = character(level = 5)

        stats.resync(session)

        val sync = session.sent.filterIsInstance<ServerMessage.CharacterSync>().single()
        val status = session.sent.filterIsInstance<ServerMessage.PlayerStatusUpdate>().single()
        assertEquals(14, sync.effectiveBaseStats.con)
        assertEquals(20, sync.derived.maxHp)
        assertEquals(20, status.maxHp)
    }

    @Test
    fun `resync clamps current hp and mana to the new max and saves`() = runBlocking {
        val saved = mutableListOf<String>()
        val stats = CharacterStats(savePlayer = { saved += it.id })
        val session = testSession()
        session.characterData = character(level = 5).copy(currentHp = 40, currentMana = 999)

        stats.resync(session)

        assertEquals(10, session.characterData!!.currentHp)
        assertEquals(50, session.characterData!!.currentMana)
        assertEquals(listOf(session.id), saved)
        assertEquals(
            10,
            session.sent
                .filterIsInstance<ServerMessage.CharacterSync>()
                .single()
                .character
                .currentHp)
    }

    @Test
    fun `resync within max does not save`() = runBlocking {
        val saved = mutableListOf<String>()
        val stats = CharacterStats(savePlayer = { saved += it.id })
        val session = testSession()
        session.characterData = character()

        stats.resync(session)

        assertTrue(saved.isEmpty())
    }
}
