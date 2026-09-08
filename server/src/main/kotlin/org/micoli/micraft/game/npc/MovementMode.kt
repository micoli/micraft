package org.micoli.micraft.game.npc

import kotlinx.serialization.Serializable

/**
 * How an NPC gets around. A definition lists one or more modes:
 * - `[WALKING]` — default land creature, subject to gravity.
 * - `[SWIMMING]` — pure water dweller: never drowns, holds depth while submerged, spawns in the
 *   water column of a `liquid` biome.
 * - `[SWIMMING, WALKING]` — amphibious: never drowns, but spawns and walks on land.
 * - `[FLYING]` / `[FLYING, WALKING]` — airborne (flight AI not implemented yet; a `FLYING`-only NPC
 *   just ignores gravity so it stays at its spawn height).
 */
@Serializable
enum class MovementMode {
    WALKING,
    SWIMMING,
    FLYING,
}
