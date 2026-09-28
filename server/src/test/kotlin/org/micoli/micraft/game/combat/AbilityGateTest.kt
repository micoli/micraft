package org.micoli.micraft.game.combat

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlinx.coroutines.runBlocking
import org.micoli.micraft.combat.AttackDefinition
import org.micoli.micraft.combat.AttackRankDefinition
import org.micoli.micraft.combat.DamageType
import org.micoli.micraft.game.classes.ClassAttackAccess
import org.micoli.micraft.game.classes.ClassDefinitionEntry
import org.micoli.micraft.game.classes.ClassLevelEntry
import org.micoli.micraft.game.classes.ClassSpellAccess
import org.micoli.micraft.game.npc.NpcManager
import org.micoli.micraft.game.session.PlayerSession
import org.micoli.micraft.player.Vec3
import org.micoli.micraft.player.rpg.BaseStats
import org.micoli.micraft.player.rpg.CharacterClass
import org.micoli.micraft.player.rpg.CharacterData
import org.micoli.micraft.protocol.ClientMessage
import org.micoli.micraft.protocol.ServerMessage
import org.micoli.micraft.support.testI18n
import org.micoli.micraft.support.testSession

class AbilityGateTest {

    private val strike =
        AttackDefinition(
            damageType = DamageType.PHYSICAL,
            ranks = mapOf(1 to AttackRankDefinition(manaCost = 10, cooldownMs = 60_000L)))
    private val miasme =
        SpellDefinition(
            type = SpellType.NECROTIC_AOE,
            enabled = true,
            ranks =
                mapOf(
                    1 to
                        SpellRankDefinition(
                            manaCost = 10, cooldownMs = 60_000L, aoeRadius = 3f, maxRange = 15f)))

    private fun mage(mana: Int = 100) =
        CharacterData(
            id = "c",
            name = "Alice",
            characterClass = CharacterClass.MAGE,
            baseStats = BaseStats(),
            currentHp = 50,
            currentMana = mana,
        )

    private fun setup(
        classRegistry: Map<String, ClassDefinitionEntry> = emptyMap(),
        others: List<PlayerSession> = emptyList(),
    ): Pair<CombatProcessor, SpellProcessor> {
        val combat =
            CombatProcessor(
                config = CombatConfigData(maxCombatRange = 20f),
                attackRegistry = mapOf("strike" to strike),
                classRegistry = classRegistry,
                npcManager = NpcManager(broadcast = {}),
                getSessions = { others },
                broadcastCombatLog = {},
                subscribeToChannel = { _, _ -> },
                i18n = testI18n(),
                savePlayer = {},
            )
        val spells =
            SpellProcessor(
                spellRegistry = mapOf("miasme" to miasme),
                classRegistry = classRegistry,
                combatConfig = CombatConfigData(),
                combatProcessor = combat,
                getSessions = { others },
            )
        return combat to spells
    }

    private fun attack(rank: Int = 1) =
        ClientMessage.AttackTarget(
            attackId = "strike", targetId = "victim", isNpc = false, attackRank = rank)

    private fun cast(rank: Int = 1, x: Float = 1f) =
        ClientMessage.CastAoeSpell(
            spellId = "miasme", targetX = x, targetY = 0f, targetZ = 0f, spellRank = rank)

    private fun victim() =
        testSession(id = "victim", name = "Bob", pos = Vec3(2f, 0f, 0f)).also {
            it.characterData = mage().copy(id = "victim", name = "Bob")
        }

    private fun caster(data: CharacterData = mage()) =
        testSession(id = "caster", name = "Alice", pos = Vec3(0f, 0f, 0f)).also {
            it.characterData = data
        }

    private fun PlayerSession.notifications() =
        (this as org.micoli.micraft.support.FakePlayerSession)
            .sent
            .filterIsInstance<ServerMessage.Notification>()
            .map { it.message }

    private suspend fun refusals(
        data: CharacterData,
        registry: Map<String, ClassDefinitionEntry>
    ): Pair<String, String> {
        val attacker = caster(data)
        val spellCaster = caster(data)
        val (combat, spells) = setup(registry, listOf(victim()))
        combat.handleAttack(attacker, attack())
        spells.handleCastAoeSpell(spellCaster, cast())
        return attacker.notifications().single() to spellCaster.notifications().single()
    }

    @Test
    fun `attack and spell refused for missing unlock name the ability and rank the same way`() =
        runBlocking {
            val registry =
                mapOf(
                    "MAGE" to
                        ClassDefinitionEntry(
                            levels =
                                mapOf(
                                    1 to
                                        ClassLevelEntry(
                                            attacks = listOf(ClassAttackAccess("other", 1)),
                                            spells = listOf(ClassSpellAccess("other", 1))))))

            val (attackMsg, spellMsg) = refusals(mage(), registry)

            assertEquals("Your class cannot use strike rank 1", attackMsg)
            assertEquals("Your class cannot use miasme rank 1", spellMsg)
        }

    @Test
    fun `attack and spell refused for missing mana share one message`() = runBlocking {
        val (attackMsg, spellMsg) = refusals(mage(mana = 0), emptyMap())

        assertEquals("Not enough mana", attackMsg)
        assertEquals(attackMsg, spellMsg)
    }

    @Test
    fun `attack and spell refused on cooldown name the rank`() = runBlocking {
        val onCooldown =
            mage()
                .copy(
                    cooldownsUntilMs =
                        mapOf(
                            "strike:1" to System.currentTimeMillis() + 60_000L,
                            "miasme:1" to System.currentTimeMillis() + 60_000L))

        val (attackMsg, spellMsg) = refusals(onCooldown, emptyMap())

        assertEquals("strike (rank 1) on cooldown", attackMsg)
        assertEquals("miasme (rank 1) on cooldown", spellMsg)
    }

    @Test
    fun `attack and spell refused on global cooldown share one message`() = runBlocking {
        val attacker = caster()
        val spellCaster = caster()
        val until = System.currentTimeMillis() + 60_000L
        attacker.combatState = attacker.combatState.copy(globalCooldownUntilMs = until)
        spellCaster.combatState = spellCaster.combatState.copy(globalCooldownUntilMs = until)
        val (combat, spells) = setup(others = listOf(victim()))

        combat.handleAttack(attacker, attack())
        spells.handleCastAoeSpell(spellCaster, cast())

        assertEquals(attacker.notifications(), spellCaster.notifications())
        assertEquals(listOf("On global cooldown"), attacker.notifications())
    }

    @Test
    fun `refused for resource starts no cooldown`() = runBlocking {
        val attacker = caster(mage(mana = 5))
        val (combat, _) = setup(others = listOf(victim()))

        combat.handleAttack(attacker, attack())

        assertTrue(attacker.characterData!!.cooldownsUntilMs.isEmpty())
        assertEquals(0L, attacker.combatState.globalCooldownUntilMs)
    }

    @Test
    fun `refused for range pays nothing and starts no cooldown on attack and area spell`() =
        runBlocking {
            val far = testSession(id = "victim", name = "Bob", pos = Vec3(500f, 0f, 0f))
            far.characterData = mage().copy(id = "victim")
            val attacker = caster()
            val spellCaster = caster()
            val (combat, spells) = setup(others = listOf(far))

            combat.handleAttack(attacker, attack())
            spells.handleCastAoeSpell(spellCaster, cast(x = 500f))

            for (s in listOf(attacker, spellCaster)) {
                assertEquals(100, s.characterData!!.currentMana)
                assertTrue(s.characterData!!.cooldownsUntilMs.isEmpty())
                assertEquals(0L, s.combatState.globalCooldownUntilMs)
            }
        }

    @Test
    fun `cooldown survives reconnect but global cooldown does not`() = runBlocking {
        val first = caster()
        val (_, spells) = setup(others = listOf(first))
        spells.handleCastAoeSpell(first, cast())
        assertTrue(first.combatState.globalCooldownUntilMs > 0)

        val reconnected =
            caster(first.characterData!!.withoutExpiredCooldowns(System.currentTimeMillis()))
        spells.handleCastAoeSpell(reconnected, cast())

        assertEquals(0L, reconnected.combatState.globalCooldownUntilMs)
        assertEquals(listOf("miasme (rank 1) on cooldown"), reconnected.notifications())
    }

    @Test
    fun `status update lists spell cooldowns`() = runBlocking {
        val session = caster()
        val (combat, spells) = setup(others = listOf(session))

        spells.handleCastAoeSpell(session, cast())

        val status =
            (session as org.micoli.micraft.support.FakePlayerSession)
                .sent
                .filterIsInstance<ServerMessage.PlayerStatusUpdate>()
                .last()
        assertTrue((status.cooldownsRemainingMs["miasme:1"] ?: 0L) > 0L)
    }

    @Test
    fun `expired cooldowns are dropped on write and on load`() = runBlocking {
        val now = System.currentTimeMillis()
        val session =
            caster(
                mage()
                    .copy(cooldownsUntilMs = mapOf("old:1" to now - 1, "live:1" to now + 60_000L)))
        val (_, spells) = setup(others = listOf(session))

        spells.handleCastAoeSpell(session, cast())

        assertEquals(setOf("live:1", "miasme:1"), session.characterData!!.cooldownsUntilMs.keys)
        assertEquals(
            setOf("live:1"),
            mage()
                .copy(cooldownsUntilMs = mapOf("old:1" to now - 1, "live:1" to now + 60_000L))
                .withoutExpiredCooldowns(now)
                .cooldownsUntilMs
                .keys)
    }
}
