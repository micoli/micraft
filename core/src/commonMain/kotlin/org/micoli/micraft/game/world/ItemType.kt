package org.micoli.micraft.game.world

import kotlin.jvm.JvmInline
import kotlinx.serialization.Serializable

@JvmInline
@Serializable(with = ItemTypeSerializer::class)
value class ItemType(val id: String) {
    val buildable: Boolean
        get() = ItemRegistry.get(this).buildable

    val placesBlock: BlockType?
        get() = ItemRegistry.get(this).placesBlock

    override fun toString(): String = id
}
