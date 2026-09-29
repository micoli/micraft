package org.micoli.micraft.game.combat

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.runBlocking
import org.micoli.micraft.combat.StatusEffect
import org.micoli.micraft.game.classes.ClassDefinitionEntry
import org.micoli.micraft.game.classes.ClassLevelEntry
import org.micoli.micraft.game.classes.ClassSpellAccess
import org.micoli.micraft.game.npc.NpcManager
import org.micoli.micraft.game.rpg.CharacterStats
import org.micoli.micraft.game.session.PlayerSession
import org.micoli.micraft.player.Vec3
import org.micoli.micraft.player.rpg.BaseStats
import org.micoli.micraft.player.rpg.CharacterClass
import org.micoli.micraft.player.rpg.CharacterData
import org.micoli.micraft.protocol.ServerMessage
import org.micoli.micraft.support.testI18n
import org.micoli.micraft.support.testSession

class SpellProcessorProtectionTest {

    private val ironSkinSpell =
        SpellDefinition(
            type = SpellType.PROTECTION,
            ranks =
                (1..5).associateWith { rank ->
                    SpellRankDefinition(
                        durationSec = 60f,
                        cooldownMs = 90_000L,
                        manaCost = 5 + rank * 5,
                        acBonus = 3 + rank,
                    )
                },
        )

    private val warriorClass =
        ClassDefinitionEntry(
            levels =
                mapOf(
                    1 to
                        ClassLevelEntry(spells = (1..5).map { ClassSpellAccess("iron_skin", it) })))

    private fun buildCombatProcessor(sessions: () -> List<PlayerSession>) =
        CombatProcessor(
            config = CombatConfigData(),
            attackRegistry = emptyMap(),
            classRegistry = emptyMap(),
            npcManager = NpcManager(broadcast = {}),
            getSessions = sessions,
            broadcastCombatLog = {},
            subscribeToChannel = { _, _ -> },
            i18n = testI18n(),
            savePlayer = {},
            characterStats = CharacterStats(protectionSpells = mapOf("iron_skin" to ironSkinSpell)),
        )

    private fun buildProcessor(
        combatProcessor: CombatProcessor,
        sessions: List<PlayerSession> = emptyList(),
        classRegistry: Map<String, ClassDefinitionEntry> = mapOf("WARRIOR" to warriorClass),
    ) =
        SpellProcessor(
            spellRegistry = mapOf("iron_skin" to ironSkinSpell),
            classRegistry = classRegistry,
            combatConfig = CombatConfigData(),
            combatProcessor = combatProcessor,
            getSessions = { sessions },
        )

    private fun testChar(name: String, level: Int = 1) =
        CharacterData(
            id = "test",
            name = name,
            characterClass = CharacterClass.WARRIOR,
            baseStats = BaseStats(),
            currentHp = 20,
            currentMana = 100,
            level = level,
        )

    @Test
    fun `castOwnProtection applies Iron Skin at the caster's Rank and raises Armor class`() =
        runBlocking {
            val caster = testSession(id = "a", name = "Alice", pos = Vec3(0f, 0f, 0f))
            caster.characterData = testChar("Alice", level = 1)
            val combatProcessor = buildCombatProcessor { listOf(caster) }
            val baselineAc =
                combatProcessor.characterStats.derived(caster, caster.characterData!!).armorClass

            buildProcessor(combatProcessor, sessions = listOf(caster)).castOwnProtection(caster)

            val active = caster.combatState.activeEffects.single()
            assertTrue(active.effect is StatusEffect.Protected)
            assertEquals("iron_skin", active.protectionId)
            assertEquals(1, active.rank)
            val derived = combatProcessor.characterStats.derived(caster, caster.characterData!!)
            assertEquals(baselineAc + 4, derived.armorClass) // Rank 1: acBonus = 3 + 1
        }

    @Test
    fun `castOwnProtection picks the Rank from the caller's Level band`() = runBlocking {
        val caster = testSession(id = "a", name = "Alice", pos = Vec3(0f, 0f, 0f))
        caster.characterData = testChar("Alice", level = 6)
        val combatProcessor = buildCombatProcessor { listOf(caster) }

        buildProcessor(combatProcessor, sessions = listOf(caster)).castOwnProtection(caster)

        val active = caster.combatState.activeEffects.single()
        assertEquals(2, active.rank)
    }

    @Test
    fun `castOwnProtection costs 0 rage — Warrior's classResource`() = runBlocking {
        val caster = testSession(id = "a", name = "Alice", pos = Vec3(0f, 0f, 0f))
        caster.characterData = testChar("Alice", level = 1)
        val combatProcessor = buildCombatProcessor { listOf(caster) }
        buildProcessor(combatProcessor, sessions = listOf(caster)).castOwnProtection(caster)

        assertEquals(0, caster.characterData!!.currentRage) // unchanged: rageCost = 0
    }

    @Test
    fun `castOwnProtection respects Cooldown on immediate re-cast`() = runBlocking {
        val caster = testSession(id = "a", name = "Alice", pos = Vec3(0f, 0f, 0f))
        caster.characterData = testChar("Alice", level = 1)
        val combatProcessor = buildCombatProcessor { listOf(caster) }
        val proc = buildProcessor(combatProcessor, sessions = listOf(caster))

        proc.castOwnProtection(caster)
        val rankAfterFirstCast = caster.combatState.activeEffects.single().rank
        // A second cast within the Cooldown must be refused entirely — no re-application, no
        // second Notification path taken beyond the Refused verdict.
        val effectsCountBefore = caster.combatState.activeEffects.size
        proc.castOwnProtection(caster)

        assertEquals(effectsCountBefore, caster.combatState.activeEffects.size)
        assertEquals(rankAfterFirstCast, caster.combatState.activeEffects.single().rank)
    }

    @Test
    fun `castOwnProtection sends a translated notification when the Class has no Protection`() =
        runBlocking {
            val caster = testSession(id = "a", name = "Alice", pos = Vec3(0f, 0f, 0f))
            caster.characterData = testChar("Alice", level = 1)
            val combatProcessor = buildCombatProcessor { listOf(caster) }
            buildProcessor(combatProcessor, sessions = listOf(caster), classRegistry = emptyMap())
                .castOwnProtection(caster)

            assertTrue(caster.combatState.activeEffects.isEmpty())
            assertTrue(caster.sent.filterIsInstance<ServerMessage.Notification>().isNotEmpty())
        }

    @Test
    fun `castOwnProtection without a character sends no_character notification`() = runBlocking {
        val caster = testSession(id = "a", name = "Alice", pos = Vec3(0f, 0f, 0f))
        assertNull(caster.characterData)
        val combatProcessor = buildCombatProcessor { listOf(caster) }
        buildProcessor(combatProcessor, sessions = listOf(caster)).castOwnProtection(caster)

        assertTrue(caster.sent.filterIsInstance<ServerMessage.Notification>().isNotEmpty())
    }
}
