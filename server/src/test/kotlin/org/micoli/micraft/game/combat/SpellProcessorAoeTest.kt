package org.micoli.micraft.game.combat

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlinx.coroutines.runBlocking
import org.micoli.micraft.combat.StatusEffect
import org.micoli.micraft.game.classes.ClassDefinitionEntry
import org.micoli.micraft.game.npc.AggroMode
import org.micoli.micraft.game.npc.NpcAttackSlot
import org.micoli.micraft.game.npc.NpcDefinition
import org.micoli.micraft.game.npc.NpcInstance
import org.micoli.micraft.game.npc.NpcManager
import org.micoli.micraft.game.npc.behaviors.StaticNpcBehavior
import org.micoli.micraft.game.session.PlayerSession
import org.micoli.micraft.npc.NpcState
import org.micoli.micraft.player.Vec3
import org.micoli.micraft.player.rpg.BaseStats
import org.micoli.micraft.player.rpg.CharacterClass
import org.micoli.micraft.player.rpg.CharacterData
import org.micoli.micraft.protocol.ClientMessage
import org.micoli.micraft.protocol.ServerMessage
import org.micoli.micraft.support.testI18n
import org.micoli.micraft.support.testSession

class SpellProcessorAoeTest {

    private val miasmeSpell =
        SpellDefinition(
            type = SpellType.NECROTIC_AOE,
            enabled = true,
            manaCost = 20,
            cooldownMs = 6000L,
            aoeRadius = 3f,
            maxRange = 15f,
        )

    private fun buildCombatProcessor(sessions: () -> List<PlayerSession> = { emptyList() }) =
        CombatProcessor(
            config = CombatConfigData(maxCombatRange = 20f),
            attackRegistry = emptyMap(),
            armorRegistry = emptyMap(),
            classRegistry = emptyMap(),
            npcManager = NpcManager(broadcast = {}),
            getSessions = sessions,
            broadcastCombatLog = {},
            subscribeToChannel = { _, _ -> },
            i18n = testI18n(),
            savePlayer = {},
        )

    private fun buildProcessor(
        sessions: List<PlayerSession> = emptyList(),
        npcs: List<NpcInstance> = emptyList(),
        classRegistry: Map<String, ClassDefinitionEntry> = emptyMap(),
    ) =
        SpellProcessor(
            spellRegistry = mapOf("miasme" to miasmeSpell),
            classRegistry = classRegistry,
            armorRegistry = emptyMap(),
            combatConfig = CombatConfigData(),
            combatProcessor = buildCombatProcessor { sessions },
            getSessions = { sessions },
            getNpcs = { npcs },
        )

    private fun testChar(name: String, hp: Int = 100, mana: Int = 100) =
        CharacterData(
            id = "test",
            name = name,
            characterClass = CharacterClass.MAGE,
            baseStats = BaseStats(),
            currentHp = hp,
            currentMana = mana,
        )

    private fun fakeNpc(pos: Vec3): NpcInstance {
        val def =
            NpcDefinition(
                type = "zombie",
                behavior = StaticNpcBehavior(),
                bbmodelFile = "zombie.bbmodel",
                width = 0.6f,
                height = 1.8f,
                wanderSpeed = 1f,
                wanderRadius = 5f,
                attacks = listOf(NpcAttackSlot("slash", 1)),
                hp = 30,
                aggroMode = AggroMode.AGGRESSIVE,
            )
        val state =
            NpcState(
                id = "npc-${pos.x.toInt()}",
                name = "Zombie",
                type = "zombie",
                pos = pos,
                yaw = 0f,
                currentHp = 30,
                maxHp = 30,
            )
        return NpcInstance(state = state, definition = def, spawnPos = pos)
    }

    @Test
    fun `miasme within range applies Withering to NPC in radius`() = runBlocking {
        val caster = testSession(id = "a", name = "Alice", pos = Vec3(0f, 0f, 0f))
        caster.characterData = testChar("Alice")
        val npc = fakeNpc(Vec3(2f, 0f, 0f))

        buildProcessor(sessions = listOf(caster), npcs = listOf(npc))
            .handleCastAoeSpell(
                caster,
                ClientMessage.CastAoeSpell(
                    spellId = "miasme", targetX = 2f, targetY = 0f, targetZ = 0f))

        assertTrue(npc.activeEffects.any { it.effect is StatusEffect.Withering })
    }

    @Test
    fun `miasme does not affect NPC outside aoe radius`() = runBlocking {
        val caster = testSession(id = "a", name = "Alice", pos = Vec3(0f, 0f, 0f))
        caster.characterData = testChar("Alice")
        val npc = fakeNpc(Vec3(10f, 0f, 0f))

        buildProcessor(sessions = listOf(caster), npcs = listOf(npc))
            .handleCastAoeSpell(
                caster,
                ClientMessage.CastAoeSpell(
                    spellId = "miasme", targetX = 0f, targetY = 0f, targetZ = 0f))

        assertFalse(npc.activeEffects.any { it.effect is StatusEffect.Withering })
    }

    @Test
    fun `miasme beyond maxRange sends out of range notification`() = runBlocking {
        val caster = testSession(id = "a", name = "Alice", pos = Vec3(0f, 0f, 0f))
        caster.characterData = testChar("Alice")

        buildProcessor(sessions = listOf(caster))
            .handleCastAoeSpell(
                caster,
                ClientMessage.CastAoeSpell(
                    spellId = "miasme", targetX = 20f, targetY = 0f, targetZ = 0f))

        assertTrue(
            caster.sent.filterIsInstance<ServerMessage.Notification>().any {
                it.message.contains("range", ignoreCase = true)
            })
    }

    @Test
    fun `miasme deducts mana from caster`() = runBlocking {
        val caster = testSession(id = "a", name = "Alice", pos = Vec3(0f, 0f, 0f))
        caster.characterData = testChar("Alice", mana = 50)

        buildProcessor(sessions = listOf(caster))
            .handleCastAoeSpell(
                caster,
                ClientMessage.CastAoeSpell(
                    spellId = "miasme", targetX = 0f, targetY = 0f, targetZ = 0f))

        assertTrue((caster.characterData?.currentMana ?: 50) < 50)
    }

    @Test
    fun `miasme with insufficient mana sends notification`() = runBlocking {
        val caster = testSession(id = "a", name = "Alice", pos = Vec3(0f, 0f, 0f))
        caster.characterData = testChar("Alice", mana = 5)

        buildProcessor(sessions = listOf(caster))
            .handleCastAoeSpell(
                caster,
                ClientMessage.CastAoeSpell(
                    spellId = "miasme", targetX = 0f, targetY = 0f, targetZ = 0f))

        assertTrue(
            caster.sent.filterIsInstance<ServerMessage.Notification>().any {
                it.message.contains("mana", ignoreCase = true)
            })
    }

    @Test
    fun `miasme applies Withering to player session in radius`() = runBlocking {
        val caster = testSession(id = "a", name = "Alice", pos = Vec3(0f, 0f, 0f))
        caster.characterData = testChar("Alice")
        val target = testSession(id = "b", name = "Bob", pos = Vec3(1f, 0f, 0f))
        target.characterData = testChar("Bob")

        buildProcessor(sessions = listOf(caster, target))
            .handleCastAoeSpell(
                caster,
                ClientMessage.CastAoeSpell(
                    spellId = "miasme", targetX = 1f, targetY = 0f, targetZ = 0f))

        assertTrue(target.combatState.activeEffects.any { it.effect is StatusEffect.Withering })
    }

    @Test
    fun `miasme respects cooldown on second cast`() = runBlocking {
        val caster = testSession(id = "a", name = "Alice", pos = Vec3(0f, 0f, 0f))
        caster.characterData = testChar("Alice", mana = 200)
        val proc = buildProcessor(sessions = listOf(caster))
        val msg =
            ClientMessage.CastAoeSpell(spellId = "miasme", targetX = 0f, targetY = 0f, targetZ = 0f)

        proc.handleCastAoeSpell(caster, msg)
        caster.sent.clear()
        proc.handleCastAoeSpell(caster, msg)

        assertTrue(
            caster.sent.filterIsInstance<ServerMessage.Notification>().any {
                it.message.contains("cooldown", ignoreCase = true)
            })
    }
}
