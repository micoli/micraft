package org.micoli.micraft.game.combat

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlinx.coroutines.runBlocking
import org.micoli.micraft.combat.ActiveStatusEffect
import org.micoli.micraft.combat.StatusEffect
import org.micoli.micraft.game.classes.ClassDefinitionEntry
import org.micoli.micraft.game.classes.ClassesConfigData
import org.micoli.micraft.game.classes.RegenSettings
import org.micoli.micraft.game.npc.NpcManager
import org.micoli.micraft.game.rpg.DerivedStatsCalculator
import org.micoli.micraft.player.rpg.BaseStats
import org.micoli.micraft.player.rpg.CharacterClass
import org.micoli.micraft.player.rpg.CharacterData
import org.micoli.micraft.protocol.ServerMessage
import org.micoli.micraft.support.testI18n
import org.micoli.micraft.support.testSession

class RegenProcessorTest {

    private fun testChar(
        hp: Int = 5,
        maxHpCon: Int = 12,
        mana: Int = 5,
        characterClass: CharacterClass = CharacterClass.MAGE,
    ) =
        CharacterData(
            id = "test",
            name = "Alice",
            characterClass = characterClass,
            baseStats = BaseStats(con = maxHpCon, wis = 12),
            currentHp = hp,
            currentMana = mana,
        )

    private fun buildCombatProcessor() =
        CombatProcessor(
            config = CombatConfigData(),
            attackRegistry = emptyMap(),
            armorRegistry = emptyMap(),
            classRegistry = emptyMap(),
            npcManager = NpcManager(broadcast = {}),
            getSessions = { emptyList() },
            broadcastCombatLog = {},
            subscribeToChannel = { _, _ -> },
            i18n = testI18n(),
            savePlayer = {},
        )

    private fun buildProcessor(
        hpFormula: String = "hpRegenPerSec * dt",
        manaFormula: String = "manaRegenPerSec * dt",
        regenIntervalMs: Long = 100L,
        className: String = "MAGE",
    ): RegenProcessor {
        val classDef =
            ClassDefinitionEntry(
                hpFormula = hpFormula,
                manaFormula = manaFormula,
            )
        val config =
            ClassesConfigData(
                regen = RegenSettings(regenIntervalMs = regenIntervalMs),
                classes = mapOf(className to classDef),
            )
        return RegenProcessor(
            config = config,
            maxRage = 100,
            armorRegistry = emptyMap(),
            combatProcessor = buildCombatProcessor(),
        )
    }

    @Test
    fun `hp regenerates when below max`() = runBlocking {
        val session = testSession()
        session.characterData = testChar(hp = 1)
        val processor = buildProcessor(hpFormula = "10.0")
        Thread.sleep(150)
        processor.tick(listOf(session))
        assertTrue(session.characterData!!.currentHp > 1)
    }

    @Test
    fun `mana regenerates when below max`() = runBlocking {
        val session = testSession()
        session.characterData = testChar(mana = 1)
        val processor = buildProcessor(manaFormula = "5.0")
        Thread.sleep(150)
        processor.tick(listOf(session))
        assertTrue(session.characterData!!.currentMana > 1)
    }

    @Test
    fun `hp does not exceed maxHp`() = runBlocking {
        val session = testSession()
        val char = testChar(hp = 10, maxHpCon = 12)
        session.characterData = char
        val processor = buildProcessor(hpFormula = "999.0")
        Thread.sleep(150)
        processor.tick(listOf(session))
        val derived = DerivedStatsCalculator.compute(session.characterData!!)
        assertTrue(session.characterData!!.currentHp <= derived.maxHp)
    }

    @Test
    fun `downed player does not regenerate`() = runBlocking {
        val session = testSession()
        session.characterData = testChar(hp = 1)
        session.combatState = session.combatState.copy(isDowned = true)
        val processor = buildProcessor(hpFormula = "10.0")
        Thread.sleep(150)
        processor.tick(listOf(session))
        assertEquals(1, session.characterData!!.currentHp)
    }

    @Test
    fun `no update sent when values unchanged`() = runBlocking {
        val session = testSession()
        val char = testChar(hp = 1)
        session.characterData = char
        // Zero formula — nothing changes
        val processor = buildProcessor(hpFormula = "0", manaFormula = "0")
        Thread.sleep(150)
        processor.tick(listOf(session))
        assertTrue(session.sent.filterIsInstance<ServerMessage.PlayerStatusUpdate>().isEmpty())
    }

    @Test
    fun `status update sent when hp changes`() = runBlocking {
        val session = testSession()
        session.characterData = testChar(hp = 1)
        val processor = buildProcessor(hpFormula = "10.0", manaFormula = "0")
        Thread.sleep(150)
        processor.tick(listOf(session))
        assertTrue(session.sent.filterIsInstance<ServerMessage.PlayerStatusUpdate>().isNotEmpty())
    }

    @Test
    fun `throttle — immediate second tick is skipped`() = runBlocking {
        val session = testSession()
        session.characterData = testChar(hp = 1)
        val processor = buildProcessor(hpFormula = "10.0", regenIntervalMs = 10_000L)
        // No sleep — interval not elapsed
        processor.tick(listOf(session))
        assertEquals(1, session.characterData!!.currentHp)
    }

    @Test
    fun `invalid formula logs warning and does not change hp`() = runBlocking {
        val session = testSession()
        session.characterData = testChar(hp = 5)
        val processor = buildProcessor(hpFormula = "!!!invalid!!!")
        Thread.sleep(150)
        processor.tick(listOf(session))
        assertEquals(5, session.characterData!!.currentHp)
    }

    @Test
    fun `class-specific formula is used over default`() = runBlocking {
        val session = testSession()
        session.characterData = testChar(hp = 1, characterClass = CharacterClass.MAGE)
        // MAGE gets formula "10.0", no default class defined for others
        val config =
            ClassesConfigData(
                regen = RegenSettings(regenIntervalMs = 100L),
                classes =
                    mapOf("MAGE" to ClassDefinitionEntry(hpFormula = "10.0", manaFormula = "0")),
            )
        val processor =
            RegenProcessor(
                config = config,
                maxRage = 100,
                armorRegistry = emptyMap(),
                combatProcessor = buildCombatProcessor(),
            )
        Thread.sleep(150)
        processor.tick(listOf(session))
        assertTrue(session.characterData!!.currentHp > 1)
    }

    @Test
    fun `activeEffects available in formula context`() = runBlocking {
        val session = testSession()
        session.characterData = testChar(hp = 1)
        session.combatState.activeEffects.add(
            ActiveStatusEffect(
                effect = StatusEffect.Poisoned,
                expiresAtMs = System.currentTimeMillis() + 60_000L,
            ))
        // Formula returns 0 if Poisoned, else 10
        val processor =
            buildProcessor(hpFormula = "activeEffects.contains(\"Poisoned\") ? 0 : 10.0")
        Thread.sleep(150)
        processor.tick(listOf(session))
        // Poisoned → no regen
        assertEquals(1, session.characterData!!.currentHp)
    }
}
