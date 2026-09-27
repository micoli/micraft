package org.micoli.micraft.game.npc.resident

import kotlin.random.Random
import org.micoli.micraft.game.npc.NpcNameGenerator

/**
 * Which Resident of a Region: the [ordinal]-th NPC of [npcType] living in the Region keyed
 * [regionKey].
 */
data class ResidentKey(val regionKey: Long, val npcType: String, val ordinal: Int)

enum class Temperament {
    GRUFF,
    CHEERFUL,
    FEARFUL,
}

/**
 * Identity of a Quest giver or merchant that outlives its runtime NPC: the same World seed and
 * [key] always give the same [name] and [temperament], as long as the same names are taken.
 */
data class Resident(val key: ResidentKey, val name: String, val temperament: Temperament) {
    companion object {
        fun of(
            worldSeed: Long,
            key: ResidentKey,
            isTaken: (String) -> Boolean = { false },
        ): Resident {
            val random = Random(seedOf(worldSeed, key))
            val temperament = Temperament.entries.random(random)
            val name = NpcNameGenerator.generate(key.npcType, random = random, isTaken = isTaken)
            return Resident(key, name, temperament)
        }

        private fun seedOf(worldSeed: Long, key: ResidentKey): Long =
            ((worldSeed * 31 + key.regionKey) * 31 + key.npcType.hashCode()) * 31 + key.ordinal
    }
}
