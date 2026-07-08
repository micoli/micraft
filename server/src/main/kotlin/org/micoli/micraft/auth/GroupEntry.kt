package org.micoli.micraft.auth

import kotlinx.serialization.Serializable

@Serializable
data class GroupEntry(
    val name: String,
    val permissions: List<String> = emptyList(),
)
