package org.micoli.micraft.command

import java.util.UUID

interface Plugin {
    val id: UUID
    val name: String
}
