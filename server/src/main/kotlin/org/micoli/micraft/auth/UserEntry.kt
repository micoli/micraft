package org.micoli.micraft.auth

import kotlinx.serialization.Serializable

@Serializable
data class UserEntry(
    val email: String,
    val passwordHash: String,
    val displayName: String = email,
    val groups: List<String> = emptyList(),
)
