package org.micoli.micraft.combat

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import org.micoli.micraft.game.world.ItemType

@Serializable
sealed class ShortcutSlot {
    @Serializable @SerialName("Item") data class Item(val itemType: ItemType) : ShortcutSlot()

    @Serializable
    @SerialName("Attack")
    data class Attack(val attackId: String, val level: Int = 1) : ShortcutSlot()

    @Serializable @SerialName("Macro") data class Macro(val macroName: String) : ShortcutSlot()

    @Serializable @SerialName("Spell") data class Spell(val spellId: String) : ShortcutSlot()
}
