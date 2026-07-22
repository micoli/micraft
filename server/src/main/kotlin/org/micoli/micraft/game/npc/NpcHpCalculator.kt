package org.micoli.micraft.game.npc

import org.apache.commons.jexl3.JexlBuilder
import org.apache.commons.jexl3.MapContext
import org.slf4j.LoggerFactory

private val log = LoggerFactory.getLogger("NpcHpCalculator")

object NpcHpCalculator {
    private val jexl = JexlBuilder().silent(false).strict(true).create()

    fun computeMaxHp(def: NpcDefinition, level: Int): Int {
        val ctx = MapContext(mapOf("hp" to def.hp, "level" to level, "minLevel" to def.minLevel))
        return runCatching {
                (jexl.createExpression(def.hpFormula).evaluate(ctx) as? Number)?.toInt() ?: def.hp
            }
            .getOrElse { e ->
                log.warn("NPC hp formula '{}' failed: {}", def.hpFormula, e.message)
                def.hp
            }
            .coerceAtLeast(1)
    }
}
