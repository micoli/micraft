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

    /** Returns one message per violation found — empty when every drop respects the cap. */
    fun validate(
        npcDefs: Map<String, NpcDefinition>,
        armorDefs: Map<String, ArmorDefinition>,
    ): List<String> {
        val violations = mutableListOf<String>()
        for ((npcType, npc) in npcDefs) {
            for (drop in npc.armorLoot) {
                val armor = armorDefs[drop.armor] ?: continue
                // Long arithmetic: npc.maxLevel defaults to Int.MAX_VALUE (no cap), and adding
                // MAX_LEVELS_ABOVE_MOB to that in Int would overflow into a negative number.
                if (armor.requiredLevel > npc.maxLevel.toLong() + MAX_LEVELS_ABOVE_MOB) {
                    val message =
                        "NPC '$npcType' (maxLevel=${npc.maxLevel}) drops armor '${drop.armor}' " +
                            "requiring level ${armor.requiredLevel} — more than " +
                            "$MAX_LEVELS_ABOVE_MOB levels above the mob"
                    log.warn(message)
                    violations += message
                }
            }
        }
        return violations
    }
}
