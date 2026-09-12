package org.micoli.micraft.game.npc

import org.micoli.micraft.game.armor.ArmorDefinition
import org.slf4j.LoggerFactory

private val log = LoggerFactory.getLogger(NpcLootValidator::class.java)

/**
 * A mob may only drop armor up to 5 levels above its own max level — keeps loot power in line with
 * the mob that dropped it. Warns (does not fail load) so a bad data entry never blocks server
 * startup, consistent with other registry loaders.
 */
object NpcLootValidator {
    private const val MAX_LEVELS_ABOVE_MOB = 5

    fun validate(npcDefs: Map<String, NpcDefinition>, armorDefs: Map<String, ArmorDefinition>) {
        for ((npcType, npc) in npcDefs) {
            for (drop in npc.armorLoot) {
                val armor = armorDefs[drop.armor] ?: continue
                if (armor.requiredLevel > npc.maxLevel + MAX_LEVELS_ABOVE_MOB) {
                    log.warn(
                        "NPC '{}' (maxLevel={}) drops armor '{}' requiring level {} — more than {} levels above the mob",
                        npcType,
                        npc.maxLevel,
                        drop.armor,
                        armor.requiredLevel,
                        MAX_LEVELS_ABOVE_MOB)
                }
            }
        }
    }
}
