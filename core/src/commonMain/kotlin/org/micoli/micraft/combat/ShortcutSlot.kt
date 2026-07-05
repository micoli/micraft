package org.micoli.micraft.combat

import kotlinx.serialization.Serializable
import org.micoli.micraft.world.ItemType

@Serializable
sealed class ShortcutSlot {
    @Serializable data class Item(val itemType: ItemType) : ShortcutSlot()

    @Serializable data class Attack(val attackId: String) : ShortcutSlot()
}
