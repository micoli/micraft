package org.micoli.micraft.game.combat

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlinx.coroutines.runBlocking
import org.micoli.micraft.game.npc.AggroMode
import org.micoli.micraft.game.npc.NpcDefinition
import org.micoli.micraft.game.npc.NpcInstance
import org.micoli.micraft.game.npc.NpcManager
import org.micoli.micraft.game.npc.behaviors.StaticNpcBehavior
import org.micoli.micraft.game.session.PlayerSession
import org.micoli.micraft.player.Vec3
import org.micoli.micraft.player.rpg.BaseStats
import org.micoli.micraft.player.rpg.CharacterClass
import org.micoli.micraft.player.rpg.CharacterData
import org.micoli.micraft.protocol.ClientMessage
import org.micoli.micraft.protocol.ServerMessage
import org.micoli.micraft.support.testI18n
import org.micoli.micraft.support.testSession

class SpellProcessorDirectDamageTest {

    private val boltSpell =
        SpellDefinition(type = SpellType.DIRECT_DAMAGE, power = 4, cooldownMs = 0, maxRange = 10f)

    private fun buildCombatProcessor(
        sessions: () -> List<PlayerSession>,
        npcManager: NpcManager = NpcManager(broadcast = {}),
    ) =
        CombatProcessor(
            config = CombatConfigData(globalCooldownMs = 1500),
            attackRegistry = emptyMap(),
            armorRegistry = emptyMap(),
            classRegistry = emptyMap(),
            npcManager = npcManager,
            getSessions = sessions,
            broadcastCombatLog = {},
            subscribeToChannel = { _, _ -> },
            i18n = testI18n(),
            savePlayer = {},
        )

    private fun buildProcessor(
        sessions: List<PlayerSession> = emptyList(),
        npcs: List<NpcInstance> = emptyList(),
        npcManager: NpcManager = NpcManager(broadcast = {}),
    ) =
        SpellProcessor(
            spellRegistry = mapOf("quickStrike" to boltSpell),
            classRegistry = emptyMap(),
            armorRegistry = emptyMap(),
            combatConfig = CombatConfigData(globalCooldownMs = 1500),
            combatProcessor = buildCombatProcessor({ sessions }, npcManager),
            getSessions = { sessions },
            getNpcs = { npcs },
        )

    private fun testChar(name: String, hp: Int = 100) =
        CharacterData(
            id = "test",
            name = name,
            characterClass = CharacterClass.WARRIOR,
            baseStats = BaseStats(),
            currentHp = hp,
            currentMana = 50,
        )

    private suspend fun spawnFakeNpc(npcManager: NpcManager, pos: Vec3, hp: Int = 30): NpcInstance {
        val def =
            NpcDefinition(
                type = "zombie",
                behavior = StaticNpcBehavior(),
                bbmodelFile = "zombie.bbmodel",
                width = 0.6f,
                height = 1.8f,
                wanderSpeed = 1f,
                wanderRadius = 5f,
                hp = hp,
                aggroMode = AggroMode.AGGRESSIVE,
            )
        npcManager.loadDefinitions(mapOf("zombie" to def))
        return npcManager.spawnNpc("Zombie", "zombie", pos)
    }

    @Test
    fun `DIRECT_DAMAGE damages the selected player target`() = runBlocking {
        val caster = testSession(id = "a", name = "Alice", pos = Vec3(0f, 0f, 0f))
        caster.characterData = testChar("Alice")
        val target = testSession(id = "b", name = "Bob", pos = Vec3(1f, 0f, 0f))
        target.characterData = testChar("Bob")
        caster.combatState = caster.combatState.copy(targetId = "b", targetIsNpc = false)

        buildProcessor(sessions = listOf(caster, target))
            .handleSpell(caster, ClientMessage.UseSpell("quickStrike"))

        assertEquals(96, target.characterData!!.currentHp)
    }

    @Test
    fun `DIRECT_DAMAGE damages the selected NPC target`() = runBlocking {
        val caster = testSession(id = "a", name = "Alice", pos = Vec3(0f, 0f, 0f))
        caster.characterData = testChar("Alice")
        val npcManager = NpcManager(broadcast = {})
        val npc = spawnFakeNpc(npcManager, Vec3(1f, 0f, 0f))
        caster.combatState = caster.combatState.copy(targetId = npc.state.id, targetIsNpc = true)

        buildProcessor(sessions = listOf(caster), npcs = listOf(npc), npcManager = npcManager)
            .handleSpell(caster, ClientMessage.UseSpell("quickStrike"))

        assertEquals(26, npc.currentHp)
    }

    @Test
    fun `DIRECT_DAMAGE without a selected target sends notification`() = runBlocking {
        val caster = testSession(id = "a", name = "Alice", pos = Vec3(0f, 0f, 0f))
        caster.characterData = testChar("Alice")

        buildProcessor(sessions = listOf(caster))
            .handleSpell(caster, ClientMessage.UseSpell("quickStrike"))

        assertTrue(
            caster.sent.filterIsInstance<ServerMessage.Notification>().any {
                it.message.contains("target", ignoreCase = true)
            })
    }

    @Test
    fun `DIRECT_DAMAGE beyond maxRange sends out of range notification`() = runBlocking {
        val caster = testSession(id = "a", name = "Alice", pos = Vec3(0f, 0f, 0f))
        caster.characterData = testChar("Alice")
        val target = testSession(id = "b", name = "Bob", pos = Vec3(50f, 0f, 0f))
        target.characterData = testChar("Bob")
        caster.combatState = caster.combatState.copy(targetId = "b", targetIsNpc = false)

        buildProcessor(sessions = listOf(caster, target))
            .handleSpell(caster, ClientMessage.UseSpell("quickStrike"))

        assertEquals(100, target.characterData!!.currentHp)
        assertTrue(
            caster.sent.filterIsInstance<ServerMessage.Notification>().any {
                it.message.contains("range", ignoreCase = true)
            })
    }

    @Test
    fun `zero-cooldown spell is still blocked by the global cooldown right after another cast`() =
        runBlocking {
            val caster = testSession(id = "a", name = "Alice", pos = Vec3(0f, 0f, 0f))
            caster.characterData = testChar("Alice")
            val target = testSession(id = "b", name = "Bob", pos = Vec3(1f, 0f, 0f))
            target.characterData = testChar("Bob")
            caster.combatState = caster.combatState.copy(targetId = "b", targetIsNpc = false)
            val proc = buildProcessor(sessions = listOf(caster, target))
            val msg = ClientMessage.UseSpell("quickStrike")

            proc.handleSpell(caster, msg)
            proc.handleSpell(caster, msg) // blocked by global cooldown despite cooldownMs = 0

            assertEquals(96, target.characterData!!.currentHp)
        }

    @Test
    fun `an attack puts a following spell cast on global cooldown`() = runBlocking {
        val caster = testSession(id = "a", name = "Alice", pos = Vec3(0f, 0f, 0f))
        caster.characterData = testChar("Alice")
        val target = testSession(id = "b", name = "Bob", pos = Vec3(1f, 0f, 0f))
        target.characterData = testChar("Bob")
        caster.combatState = caster.combatState.copy(targetId = "b", targetIsNpc = false)

        val combatProcessor =
            CombatProcessor(
                config = CombatConfigData(globalCooldownMs = 1500),
                attackRegistry =
                    mapOf(
                        "basic_attack" to
                            org.micoli.micraft.combat.AttackDefinition(
                                damageType = org.micoli.micraft.combat.DamageType.PHYSICAL,
                                levels =
                                    mapOf(
                                        1 to
                                            org.micoli.micraft.combat.AttackLevelDefinition(
                                                power = 0, weaponDice = "1d4", cooldownMs = 0)))),
                armorRegistry = emptyMap(),
                classRegistry = emptyMap(),
                npcManager = NpcManager(broadcast = {}),
                getSessions = { listOf(caster, target) },
                broadcastCombatLog = {},
                subscribeToChannel = { _, _ -> },
                i18n = testI18n(),
                savePlayer = {},
            )
        val spellProc =
            SpellProcessor(
                spellRegistry = mapOf("quickStrike" to boltSpell),
                classRegistry = emptyMap(),
                armorRegistry = emptyMap(),
                combatConfig = CombatConfigData(globalCooldownMs = 1500),
                combatProcessor = combatProcessor,
                getSessions = { listOf(caster, target) },
                getNpcs = { emptyList() },
            )

        combatProcessor.handleAttack(
            caster,
            ClientMessage.AttackTarget(
                attackId = "basic_attack", targetId = "b", isNpc = false, attackLevel = 1))
        val hpAfterAttack = target.characterData!!.currentHp
        spellProc.handleSpell(caster, ClientMessage.UseSpell("quickStrike"))

        assertEquals(hpAfterAttack, target.characterData!!.currentHp, "spell blocked by attack GCD")
        assertTrue(
            caster.sent.filterIsInstance<ServerMessage.Notification>().any {
                it.message.contains("cooldown", ignoreCase = true)
            })
    }
}
