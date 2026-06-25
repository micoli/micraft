package org.micoli.micraft.world

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
enum class VegetationType {
    @SerialName("oak_tree") OAK_TREE,
    @SerialName("pine_tree") PINE_TREE,
    @SerialName("pine_tree_snow") PINE_TREE_SNOW,
    @SerialName("flower") FLOWER,
    @SerialName("weed") WEED,
}
