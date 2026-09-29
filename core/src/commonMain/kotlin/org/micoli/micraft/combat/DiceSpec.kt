package org.micoli.micraft.combat

/**
 * Parsed "NdM" dice notation (e.g. "2d6"): [count] dice of [sides] faces each. [isValid] is false
 * for a spec that isn't "NdM" — [count]/[sides] then hold the fallback (1d4), but callers decide
 * their own fallback behaviour for the invalid case (see the server's `CombatProcessor.rollDice`
 * and `ProtectionSimulator.diceMean`, the only two parsers of weapon-dice specs — kept in sync
 * here).
 */
data class DiceSpec(val count: Int, val sides: Int, val isValid: Boolean) {
    val mean: Double
        get() = count * (sides + 1) / 2.0

    companion object {
        fun parse(spec: String): DiceSpec {
            val parts = spec.lowercase().split("d")
            if (parts.size != 2) return DiceSpec(1, 4, isValid = false)
            val count = parts[0].toIntOrNull() ?: 1
            val sides = parts[1].toIntOrNull() ?: 4
            return DiceSpec(count, sides, isValid = true)
        }
    }
}
