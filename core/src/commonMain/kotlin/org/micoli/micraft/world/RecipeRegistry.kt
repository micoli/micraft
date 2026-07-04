package org.micoli.micraft.world

object RecipeRegistry {
    private val defs: MutableMap<String, RecipeDefinition> = mutableMapOf()

    fun load(incoming: Map<String, RecipeDefinition>) {
        defs.clear()
        defs.putAll(incoming)
    }

    fun all(): Map<String, RecipeDefinition> = defs.toMap()

    fun get(id: String): RecipeDefinition? = defs[id]

    fun keys(): Set<String> = defs.keys.toSet()
}
