package org.micoli.micraft.game

import org.micoli.micraft.placeable.siege.SiegeWeaponState
import org.micoli.micraft.protocol.ServerMessage

/**
 * Thin, render-free tracker of siege-specific state (pitch/power/cooldown) — no mesh or
 * interpolation of its own, since position/orientation live entirely on the linked
 * [PlaceableManager] instance. Phase A only needs this to not lose the data between server pushes;
 * trajectory preview and cooldown UI are later phases.
 */
class SiegeWeaponManager : ServerMessageHandler {
    private val states = mutableMapOf<String, SiegeWeaponState>()
    private val byPlaceableId = mutableMapOf<String, String>()

    override fun handle(msg: ServerMessage) =
        when (msg) {
            is ServerMessage.SiegeWeaponUpdate -> handleUpdate(msg.state)
            else -> Unit
        }

    private fun handleUpdate(state: SiegeWeaponState) {
        states[state.id] = state
        byPlaceableId[state.placeableId] = state.id
    }

    fun get(id: String): SiegeWeaponState? = states[id]

    fun getByPlaceableId(placeableId: String): SiegeWeaponState? =
        byPlaceableId[placeableId]?.let { states[it] }

    fun clear() {
        states.clear()
        byPlaceableId.clear()
    }
}
