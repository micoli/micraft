package org.micoli.micraft.placeable

/**
 * Generic surface shared by every free-standing placed object (siege weapons, furniture, …) — just
 * enough to resolve and render its model and know whether it can be turned. Kind-specific data
 * (siege attack stats, etc.) stays in that kind's own definition/registry.
 *
 * [bbmodelPath] is the model location relative to `resources/`, without extension — e.g.
 * `siege/weapons/CANON` or `furnitures/TABLE`; the client fetches
 * `/api/models/<bbmodelPath>/<lastSegment>.bbmodel`.
 */
data class PlaceableDefinition(val bbmodelPath: String, val rotatable: Boolean = true)
