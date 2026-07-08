package org.micoli.micraft.game.rpg

import kotlinx.serialization.Serializable

@Serializable
data class ProgressionConfig(
    val thresholds: List<Int> =
        listOf(
            300,
            900,
            2700,
            6500,
            11700,
            21060,
            37908,
            68234,
            122821,
            171950,
            240730,
            337022,
            471831,
            660563,
            924789,
            1294704,
            1812586,
            2537620,
            3552668),
)
