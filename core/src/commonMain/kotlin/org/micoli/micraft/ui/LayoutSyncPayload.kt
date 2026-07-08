package org.micoli.micraft.ui

import kotlinx.serialization.Serializable

@Serializable data class LayoutSyncPayload(val layouts: List<GameLayout>, val activeLayout: String)
