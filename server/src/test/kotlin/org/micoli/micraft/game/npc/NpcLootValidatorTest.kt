package org.micoli.micraft.game.npc

import kotlin.test.Test
import org.micoli.micraft.game.armor.ArmorDefinition
import org.micoli.micraft.game.armor.ArmorDropEntry
import org.micoli.micraft.game.armor.ArmorType
import org.micoli.micraft.game.armor.WearableSlots
import org.micoli.micraft.game.npc.behaviors.StaticNpcBehavior

private fun npcDef(maxLevel: Int, armorLoot: List<ArmorDropEntry>) =
    NpcDefinition(
        type = "wolf",
        behavior = StaticNpcBehavior(),
        bbmodelFile = "npc",
        width = 0.6f,
        height = 1.8f,
        wanderSpeed = 0f,
        wanderRadius = 0f,
        maxLevel = maxLevel,
        armorLoot = armorLoot,
    )

class NpcLootValidatorTest {
    @Test
    fun doesNotThrowOnArmorWithinFiveLevelsOfMob() {
        val armor = ArmorDefinition(wearable = WearableSlots(body = true), requiredLevel = 10)
        val npc = npcDef(maxLevel = 5, armorLoot = listOf(ArmorDropEntry(armor = "leather_chest")))
        NpcLootValidator.validate(mapOf("wolf" to npc), mapOf("leather_chest" to armor))
    }

    @Test
    fun doesNotThrowOnArmorAboveFiveLevelsOfMob_onlyWarns() {
        val armor =
            ArmorDefinition(
                wearable = WearableSlots(body = true),
                armorType = ArmorType.PLATE,
                requiredLevel = 20)
        val npc = npcDef(maxLevel = 5, armorLoot = listOf(ArmorDropEntry(armor = "plate_chest")))
        // A warn-only validator must never throw — a bad data entry should never block startup.
        NpcLootValidator.validate(mapOf("wolf" to npc), mapOf("plate_chest" to armor))
    }
}
