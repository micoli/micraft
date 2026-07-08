package org.micoli.micraft.auth

import kotlinx.serialization.Serializable

@Serializable data class UsersConfig(val users: List<UserEntry> = emptyList())
