package org.micoli.micraft.game.world

import kotlinx.serialization.Serializable

private const val HEX_DIGITS = "0123456789ABCDEF"

// Kotlin/Wasm has no String.format — build the two hex digits by hand.
private fun hex2(value: Int): String {
    val c = value.coerceIn(0, 255)
    return "${HEX_DIGITS[(c shr 4) and 0xF]}${HEX_DIGITS[c and 0xF]}"
}

/** A palette entry usable as a plain (texture-less) block color. */
@Serializable
data class PlainColor(val name: String, val r: Int, val g: Int, val b: Int) {
    /** Uppercase RRGGBB, no leading '#'. */
    fun hex(): String = hex2(r) + hex2(g) + hex2(b)

    companion object {
        /** Parses `RRGGBB` or `#RRGGBB`; null when malformed. */
        fun fromHex(name: String, hex: String): PlainColor? {
            val digits = hex.removePrefix("#")
            if (digits.length != 6) return null
            val rgb = (0..2).map { digits.substring(it * 2, it * 2 + 2).toIntOrNull(16) }
            if (rgb.any { it == null }) return null
            return PlainColor(name, rgb[0]!!, rgb[1]!!, rgb[2]!!)
        }
    }
}

/**
 * Ordered palette of plain colors. Index 0 is the reserved "untinted" sentinel — a block whose
 * state carries color index 0 renders with its own texture.
 */
object PlainColorRegistry {
    private val UNTINTED = PlainColor("", 255, 255, 255)

    private val colors = mutableListOf(UNTINTED)

    /** Replaces the palette. [incoming] must not contain the sentinel — it is prepended here. */
    fun load(incoming: List<PlainColor>) {
        colors.clear()
        colors.add(UNTINTED)
        colors.addAll(incoming.take(BlockState.MAX_COLOR_INDEX))
    }

    /** Palette without the sentinel, in declaration order (index 1 = first element). */
    fun all(): List<PlainColor> = colors.drop(1)

    fun size(): Int = colors.size - 1

    fun byIndex(index: Int): PlainColor? = if (index <= 0) null else colors.getOrNull(index)

    /** 0 when unknown or blank — meaning "untinted". */
    fun indexOf(name: String?): Int {
        if (name.isNullOrBlank()) return 0
        val idx = colors.indexOfFirst { it.name.equals(name, ignoreCase = true) }
        return if (idx <= 0) 0 else idx
    }

    fun hex(index: Int): String? = byIndex(index)?.hex()
}
