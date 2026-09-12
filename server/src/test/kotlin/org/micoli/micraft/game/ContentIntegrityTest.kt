package org.micoli.micraft.game

import kotlin.test.Test
import kotlin.test.assertTrue
import org.micoli.micraft.game.armor.ArmorRegistryLoader
import org.micoli.micraft.game.npc.NpcLootValidator
import org.micoli.micraft.game.npc.NpcRegistryLoader
import org.micoli.micraft.game.quest.QuestRegistryLoader

/**
 * Loads the real `resources/` content (not test fixtures) to catch yaml that silently drops on a
 * typo'd field or references a missing id — the kind of mistake that never shows up as a compile or
 * unit-test failure, only as a warn log nobody reads.
 */
class ContentIntegrityTest {
    @Test
    fun allNpcsLoadAndQuestGiverOffersResolve() {
        val npcs = NpcRegistryLoader().load()
        assertTrue(npcs.size > 40, "expected the full NPC roster, found ${npcs.size}")

        val quests = QuestRegistryLoader().load()
        val hermitMan = npcs["hermit_man"] ?: error("hermit_man NPC should exist")
        assertTrue(
            hermitMan.behaviorKey == "quest_giver", "hermit_man should be the quest-giver NPC")
        for (questId in hermitMan.offersQuests) {
            assertTrue(questId in quests, "hermit_man offers unknown quest '$questId'")
        }
    }

    @Test
    fun questDependsOnChainResolves() {
        val quests = QuestRegistryLoader().load()
        for ((id, def) in quests) {
            for (prereq in def.dependsOn) {
                assertTrue(prereq in quests, "quest '$id' depends on unknown quest '$prereq'")
            }
        }
    }

    @Test
    fun armorLootAndRewardsResolveAndRespectLevelCap() {
        val npcs = NpcRegistryLoader().load()
        val armors = ArmorRegistryLoader().load()
        for ((npcType, npc) in npcs) {
            for (drop in npc.armorLoot) {
                assertTrue(
                    drop.armor in armors, "NPC '$npcType' drops unknown armor '${drop.armor}'")
            }
        }
        val quests = QuestRegistryLoader().load()
        for ((id, def) in quests) {
            for (armorId in def.rewards.armorRewards) {
                assertTrue(armorId in armors, "quest '$id' rewards unknown armor '$armorId'")
            }
        }
        val violations = NpcLootValidator.validate(npcs, armors)
        assertTrue(violations.isEmpty(), "loot level cap violations: $violations")
    }
}
