package org.micoli.micraft.world

object ItemRegistry {
    private val defs: MutableMap<ItemType, ItemDefinition> = mutableMapOf()

    fun load(incoming: Map<ItemType, ItemDefinition>) {
        defs.clear()
        defs.putAll(incoming)
    }

    fun keys(): Set<ItemType> = defs.keys.toSet()

    fun get(type: ItemType): ItemDefinition = defs[type] ?: ItemDefinition()
}
