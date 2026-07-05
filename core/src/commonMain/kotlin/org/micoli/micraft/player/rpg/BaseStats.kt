package org.micoli.micraft.player.rpg

import kotlinx.serialization.EncodeDefault
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.Serializable

@Serializable
@OptIn(ExperimentalSerializationApi::class)
data class BaseStats(
    @EncodeDefault val str: Int = 8,
    @EncodeDefault val dex: Int = 8,
    @EncodeDefault val intel: Int = 8,
    @EncodeDefault val wis: Int = 8,
    @EncodeDefault val con: Int = 8,
    @EncodeDefault val cha: Int = 8,
)
