package org.micoli.micraft.game.combat

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlinx.coroutines.runBlocking
import org.micoli.micraft.combat.ActiveStatusEffect
import org.micoli.micraft.combat.StatusEffect
import org.micoli.micraft.game.session.PlayerSession
import org.micoli.micraft.player.rpg.BaseStats
import org.micoli.micraft.player.rpg.CharacterClass
import org.micoli.micraft.player.rpg.CharacterData
import org.micoli.micraft.protocol.ServerMessage
import org.micoli.micraft.support.testSession
import org.micoli.micraft.support.testWorld

class StatusEffectProcessorTest {

    /** Virtual clock: lets tests advance time without sleeping. */
    private var clock = 1_000L

    private fun testChar(name: String, hp: Int = 20) =
        CharacterData(
            id = "test",
            name = name,
            characterClass = CharacterClass.WARRIOR,
            baseStats = BaseStats(),
            currentHp = hp,
            currentMana = 0,
        )

    private fun buildProcessor(
        combatLog: MutableList<String> = mutableListOf(),
        subscribed: MutableList<Pair<PlayerSession, String>> = mutableListOf(),
        healthUpdates: MutableList<Triple<String, Int, Int>> = mutableListOf(),
    ) =
        StatusEffectProcessor(
            armorRegistry = emptyMap(),
            world = testWorld(),
            broadcastHealthUpdate = { id, _, hp, maxHp ->
                healthUpdates.add(Triple(id, hp, maxHp))
            },
            broadcastCombatLog = { combatLog.add(it) },
            subscribeToChannel = { s, ch -> subscribed.add(s to ch) },
            nowMs = { clock },
        )

    private fun activeEffect(effect: StatusEffect, durationMs: Long = 60_000L) =
        ActiveStatusEffect(effect = effect, expiresAtMs = clock + durationMs)

    // Create processor, advance 600ms so dtSec ≈ 0.6 → hpDelta = -1.2f → dmg = 1 > 0 on first tick.
    private fun agedProcessor(
        combatLog: MutableList<String> = mutableListOf(),
        subscribed: MutableList<Pair<PlayerSession, String>> = mutableListOf(),
        healthUpdates: MutableList<Triple<String, Int, Int>> = mutableListOf(),
    ): StatusEffectProcessor {
        val p = buildProcessor(combatLog, subscribed, healthUpdates)
        clock += 600
        return p
    }

    @Test
    fun `poisoned player takes HP damage on tick`() = runBlocking {
        val session = testSession(id = "a", name = "Alice")
        session.characterData = testChar("Alice", hp = 20)
        session.combatState.activeEffects.add(activeEffect(StatusEffect.Poisoned))

        agedProcessor().tick(listOf(session))

        assertTrue(session.characterData!!.currentHp < 20)
    }

    @Test
    fun `poisoned player is subscribed to combat channel`() = runBlocking {
        val session = testSession(id = "a", name = "Alice")
        session.characterData = testChar("Alice", hp = 20)
        session.combatState.activeEffects.add(activeEffect(StatusEffect.Poisoned))

        val subscribed = mutableListOf<Pair<PlayerSession, String>>()
        agedProcessor(subscribed = subscribed).tick(listOf(session))

        assertTrue(subscribed.any { (s, ch) -> s.id == "a" && ch == "combat" })
    }

    @Test
    fun `poisoned player combat log contains player name and poison`() = runBlocking {
        val session = testSession(id = "a", name = "Alice")
        session.characterData = testChar("Alice", hp = 20)
        session.combatState.activeEffects.add(activeEffect(StatusEffect.Poisoned))

        val combatLog = mutableListOf<String>()
        agedProcessor(combatLog = combatLog).tick(listOf(session))

        assertTrue(combatLog.isNotEmpty())
        assertTrue(combatLog[0].contains("Alice"))
        assertTrue(combatLog[0].contains("poison"))
    }

    @Test
    fun `expired effects are removed on tick`() = runBlocking {
        val session = testSession(id = "a", name = "Alice")
        session.characterData = testChar("Alice", hp = 20)
        // Effect already expired
        session.combatState.activeEffects.add(
            ActiveStatusEffect(effect = StatusEffect.Poisoned, expiresAtMs = 1L))

        val processor = buildProcessor()
        processor.tick(listOf(session))

        assertTrue(session.combatState.activeEffects.isEmpty())
        // StatusEffectUpdate sent to notify client of cleared effects
        assertTrue(session.sent.filterIsInstance<ServerMessage.StatusEffectUpdate>().isNotEmpty())
    }

    @Test
    fun `session with no effects is skipped`() = runBlocking {
        val session = testSession(id = "a", name = "Alice")
        session.characterData = testChar("Alice", hp = 20)

        val combatLog = mutableListOf<String>()
        val processor = buildProcessor(combatLog = combatLog)
        processor.tick(listOf(session))

        assertEquals(0, combatLog.size)
        assertEquals(20, session.characterData!!.currentHp)
    }

    @Test
    fun `session with no characterData is skipped`() = runBlocking {
        val session = testSession(id = "a", name = "Alice")
        session.characterData = null
        session.combatState.activeEffects.add(activeEffect(StatusEffect.Poisoned))

        val combatLog = mutableListOf<String>()
        val processor = buildProcessor(combatLog = combatLog)

        // Should not throw
        processor.tick(listOf(session))

        assertEquals(0, combatLog.size)
    }

    @Test
    fun `poisoned player with godMode keeps full HP`() = runBlocking {
        val session = testSession(id = "a", name = "Alice")
        session.characterData = testChar("Alice", hp = 20)
        session.state = session.state.copy(godMode = true)
        session.combatState.activeEffects.add(activeEffect(StatusEffect.Poisoned))

        agedProcessor().tick(listOf(session))

        assertEquals(20, session.characterData!!.currentHp)
    }

    @Test
    fun `pyre player takes HP damage on tick`() = runBlocking {
        val session = testSession(id = "a", name = "Alice")
        session.characterData = testChar("Alice", hp = 20)
        session.combatState.activeEffects.add(activeEffect(StatusEffect.Pyre))

        agedProcessor().tick(listOf(session))

        assertTrue(session.characterData!!.currentHp < 20)
    }

    @Test
    fun `pyre player combat log contains pyre`() = runBlocking {
        val session = testSession(id = "a", name = "Alice")
        session.characterData = testChar("Alice", hp = 20)
        session.combatState.activeEffects.add(activeEffect(StatusEffect.Pyre))

        val combatLog = mutableListOf<String>()
        agedProcessor(combatLog = combatLog).tick(listOf(session))

        assertTrue(combatLog.isNotEmpty())
        assertTrue(combatLog[0].contains("pyre"))
    }

    @Test
    fun `pyre accumulates fractional damage across fast ticks`() = runBlocking {
        val session = testSession(id = "a", name = "Alice")
        session.characterData = testChar("Alice", hp = 20)
        session.combatState.activeEffects.add(activeEffect(StatusEffect.Pyre))

        // Simulate many 50ms ticks — each deals well under 1 HP, but fractional damage must
        // accumulate and eventually deal ≥1 damage.
        val processor = buildProcessor()
        repeat(30) {
            clock += 50
            processor.tick(listOf(session))
        }

        assertTrue(session.characterData!!.currentHp < 20)
    }

    @Test
    fun `withering player takes HP damage on tick`() = runBlocking {
        val session = testSession(id = "a", name = "Alice")
        session.characterData = testChar("Alice", hp = 20)
        session.combatState.activeEffects.add(activeEffect(StatusEffect.Withering))

        agedProcessor().tick(listOf(session))

        assertTrue(session.characterData!!.currentHp < 20)
    }

    @Test
    fun `withering player combat log contains wither`() = runBlocking {
        val session = testSession(id = "a", name = "Alice")
        session.characterData = testChar("Alice", hp = 20)
        session.combatState.activeEffects.add(activeEffect(StatusEffect.Withering))

        val combatLog = mutableListOf<String>()
        agedProcessor(combatLog = combatLog).tick(listOf(session))

        assertTrue(combatLog.isNotEmpty())
        assertTrue(combatLog[0].contains("wither"))
    }
}
