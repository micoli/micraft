package org.micoli.micraft.game.npc.animal

import kotlinx.serialization.Serializable

@Serializable
enum class NpcDiet {
    HERBIVORE,
    CARNIVORE,
    OMNIVORE
}
