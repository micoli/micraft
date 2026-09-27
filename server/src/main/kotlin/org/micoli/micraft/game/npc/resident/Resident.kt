package org.micoli.micraft.game.npc.resident

import kotlin.random.Random
import org.micoli.micraft.game.npc.FantasyNameGenerator

/**
 * Which Resident of a Region: the [rank]-th NPC of [npcType] living in the Region keyed
 * [regionKey].
 */
data class ResidentKey(val regionKey: Long, val npcType: String, val rank: Int)

enum class Temperament {
    GRUFF,
    CHEERFUL,
    FEARFUL,
}

/**
 * Identity of a Quest giver or merchant that outlives its runtime NPC: the same World seed and
 * [key] always give the same [name] and [temperament].
 */
data class Resident(val key: ResidentKey, val name: String, val temperament: Temperament) {
    companion object {
        fun of(worldSeed: Long, key: ResidentKey): Resident {
            val random = Random(seedOf(worldSeed, key))
            val temperament = Temperament.entries.random(random)
            return Resident(
                key, FantasyNameGenerator.generate(key.npcType, random = random), temperament)
        }

        private fun seedOf(worldSeed: Long, key: ResidentKey): Long =
            ((worldSeed * 31 + key.regionKey) * 31 + key.npcType.hashCode()) * 31 + key.rank
    }
}
