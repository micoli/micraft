package org.micoli.micraft.game.npc

/**
 * Wraps [FantasyNameGenerator] with a uniqueness guarantee against an externally supplied predicate
 * — the caller decides what "taken" means (other NPCs, players, or both).
 */
object NpcNameGenerator {
    private const val MAX_ATTEMPTS = 30
    private const val MAX_SUFFIX = 1000

    fun generate(type: String, isTaken: (String) -> Boolean): String {
        repeat(MAX_ATTEMPTS) {
            val candidate = FantasyNameGenerator.generate(type)
            if (!isTaken(candidate)) return candidate
        }
        val base = FantasyNameGenerator.generate(type)
        for (suffix in 2..MAX_SUFFIX) {
            val candidate = "$base $suffix"
            if (!isTaken(candidate)) return candidate
        }
        return "$base ${MAX_SUFFIX + 1}"
    }
}
