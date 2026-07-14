package org.micoli.micraft.game.world.proceduralGenerator

object FantasyNameGenerator {
    private val prefixes =
        listOf(
            "Al",
            "Bor",
            "Cal",
            "Dor",
            "El",
            "Fen",
            "Gor",
            "Hel",
            "Ir",
            "Jor",
            "Kal",
            "Lor",
            "Mor",
            "Nor",
            "Or",
            "Per",
            "Qal",
            "Rin",
            "Sol",
            "Tor",
            "Ul",
            "Val",
            "Wer",
            "Xal",
            "Yor",
            "Zan",
            "Aer",
            "Bael",
            "Ceth",
            "Del",
            "Eth",
            "Fael",
            "Gael",
            "Haer",
        )
    private val middles =
        listOf(
            "a",
            "an",
            "ar",
            "ash",
            "en",
            "eth",
            "im",
            "in",
            "is",
            "on",
            "or",
            "oth",
            "um",
            "un",
            "ur",
            "ael",
            "ain",
            "aith",
            "amn",
            "eon",
            "ian",
            "iel",
            "ion",
            "oan",
        )
    private val suffixes =
        listOf(
            "dor",
            "eth",
            "fen",
            "gar",
            "heim",
            "iel",
            "ion",
            "ith",
            "mar",
            "mir",
            "mor",
            "nas",
            "rath",
            "ren",
            "ron",
            "shan",
            "thal",
            "thor",
            "vel",
            "wen",
            "wyn",
            "zar",
            "del",
            "dras",
            "fell",
            "gard",
            "hold",
            "mere",
            "moor",
            "vale",
            "ward",
            "wood",
        )

    fun generate(seed: Long, cellX: Int, cellZ: Int): String {
        var h =
            seed xor
                (cellX.toLong() * -7046029254386353131L) xor
                (cellZ.toLong() * 0x6C62272E07BB0142L)
        h = h xor (h ushr 30)
        h *= -4658895341019938895L
        h = h xor (h ushr 27)
        h *= -7723592293110705685L
        h = h xor (h ushr 31)

        val pi = ((h and 0x7FFFFFFFL) % prefixes.size).toInt()
        h = h xor (h ushr 17)
        h *= -1524234663L
        val mi = ((h and 0x7FFFFFFFL) % middles.size).toInt()
        h = h xor (h ushr 13)
        h *= 6364136223846793005L
        val si = ((h and 0x7FFFFFFFL) % suffixes.size).toInt()

        return prefixes[pi] + middles[mi] + suffixes[si]
    }
}
