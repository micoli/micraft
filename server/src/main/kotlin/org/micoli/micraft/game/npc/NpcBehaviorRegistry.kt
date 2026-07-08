package org.micoli.micraft.game.npc

import org.micoli.micraft.game.npc.behaviors.InteractionableNpcBehavior
import org.micoli.micraft.game.npc.behaviors.RandomMovableNpcBehavior
import org.micoli.micraft.game.npc.behaviors.StaticNpcBehavior

object NpcBehaviorRegistry {
    private val behaviors: MutableMap<String, NpcBehavior> =
        mutableMapOf(
            "static" to StaticNpcBehavior(),
            "random_movable" to RandomMovableNpcBehavior(),
            "interactionable" to InteractionableNpcBehavior(),
        )

    fun get(key: String): NpcBehavior = behaviors[key] ?: error("Unknown NPC behavior: '$key'")

    fun register(key: String, behavior: NpcBehavior) {
        behaviors[key] = behavior
    }

    fun keys(): Set<String> = behaviors.keys.toSet()
}
