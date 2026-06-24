package org.micoli.micraft

import java.util.UUID

interface Plugin {
    val id: UUID
    val name: String
}
