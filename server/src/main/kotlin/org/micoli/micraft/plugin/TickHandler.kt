package org.micoli.micraft.plugin

import java.util.UUID

interface TickHandler {
    val id: UUID
    val name: String

    suspend fun tick(context: TickContext)
}
