package org.micoli.micraft.placeable

/**
 * Generic surface shared by every free-standing placed object (siege weapons today, other placeable
 * kinds later) — just enough to resolve and render its model. Kind-specific data (siege attack
 * stats, etc.) stays in that kind's own definition/registry.
 */
data class PlaceableDefinition(val bbmodelFile: String)
