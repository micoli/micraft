package org.micoli.micraft.social

/**
 * Faction id → display colour, fed from `ServerMessage.FactionSync`, read by nameplate rendering.
 */
object FactionColors {
    private val colors = mutableMapOf<String, String>()

    fun update(defs: Map<String, String>) {
        colors.clear()
        colors.putAll(defs)
    }

    fun colorOf(factionId: String?): String? = factionId?.let { colors[it] }
}
