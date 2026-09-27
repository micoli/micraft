package org.micoli.micraft.game.npc

import kotlin.random.Random

/**
 * Wraps [FantasyNameGenerator] with a uniqueness guarantee against an externally supplied predicate
 * — the caller decides what "taken" means (other NPCs, players, or both).
 */
object NpcNameGenerator {
    private const val MAX_ATTEMPTS = 30
    private const val MAX_SUFFIX = 1000

    fun generate(
        type: String,
        isAnimal: Boolean = false,
        random: Random = Random,
        isTaken: (String) -> Boolean,
    ): String {
        repeat(MAX_ATTEMPTS) {
            val candidate = FantasyNameGenerator.generate(type, isAnimal, random)
            if (!isTaken(candidate)) return candidate
        }
        val base = FantasyNameGenerator.generate(type, isAnimal, random)
        for (suffix in 2..MAX_SUFFIX) {
            val candidate = "$base $suffix"
            if (!isTaken(candidate)) return candidate
        }
        return "$base ${MAX_SUFFIX + 1}"
    }
}
