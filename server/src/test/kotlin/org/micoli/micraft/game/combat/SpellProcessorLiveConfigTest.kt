package org.micoli.micraft.game.combat

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlinx.coroutines.runBlocking
import org.micoli.micraft.game.classes.ClassesConfig
import org.micoli.micraft.game.npc.AggroMode
import org.micoli.micraft.game.npc.NpcDefinition
import org.micoli.micraft.game.npc.NpcManager
import org.micoli.micraft.game.npc.behaviors.StaticNpcBehavior
import org.micoli.micraft.player.Vec3
import org.micoli.micraft.player.rpg.BaseStats
import org.micoli.micraft.player.rpg.CharacterClass
import org.micoli.micraft.player.rpg.CharacterData
import org.micoli.micraft.protocol.ClientMessage
import org.micoli.micraft.support.testI18n
import org.micoli.micraft.support.testSession

/**
 * Exercises the real shipped config (SkillsConfig + ClassesConfig, not hand-built test fixtures)
 * end to end, to catch a yaml/registry wiring bug that in-memory unit tests would miss.
 */
class SpellProcessorLiveConfigTest {

    @Test
    fun `sparkBolt from the real config damages a targeted NPC`() = runBlocking {
        val skills = SkillsConfig()
        val classes = ClassesConfig()

        val npcManager = NpcManager(broadcast = {})
        val npcDef =
            NpcDefinition(
                type = "zombie",
                behavior = StaticNpcBehavior(),
                bbmodelFile = "zombie.bbmodel",
                width = 0.6f,
                height = 1.8f,
                wanderSpeed = 1f,
                wanderRadius = 5f,
                hp = 30,
                aggroMode = AggroMode.AGGRESSIVE,
            )
        npcManager.loadDefinitions(mapOf("zombie" to npcDef))
        val npc = npcManager.spawnNpc("Zombie", "zombie", Vec3(1f, 0f, 0f))

        val combatProcessor =
            CombatProcessor(
                config = CombatConfigData(),
                attackRegistry = emptyMap(),
                armorRegistry = emptyMap(),
                classRegistry = classes.data.classes,
                npcManager = npcManager,
                getSessions = { emptyList() },
                broadcastCombatLog = {},
                subscribeToChannel = { _, _ -> },
                i18n = testI18n(),
                savePlayer = {},
            )
        val spellProcessor =
            SpellProcessor(
                spellRegistry = skills.data.spells,
                classRegistry = classes.data.classes,
                armorRegistry = emptyMap(),
                combatConfig = CombatConfigData(),
                combatProcessor = combatProcessor,
                getSessions = { emptyList() },
                getNpcs = { npcManager.getAll() },
            )

        val caster = testSession(id = "a", name = "Alice", pos = Vec3(0f, 0f, 0f))
        caster.characterData =
            CharacterData(
                id = "a",
                name = "Alice",
                characterClass = CharacterClass.MAGE,
                baseStats = BaseStats(),
                currentHp = 100,
                currentMana = 100,
            )
        caster.combatState = caster.combatState.copy(targetId = npc.state.id, targetIsNpc = true)

        spellProcessor.handleSpell(caster, ClientMessage.UseSpell("sparkBolt"))

        assertEquals(26, npc.currentHp, "sparkBolt should deal its configured power as damage")
    }
}
