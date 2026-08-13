package org.micoli.micraft.auth

import kotlinx.serialization.Serializable
import org.micoli.micraft.schema.JsonSchemaRoot

@Serializable
@JsonSchemaRoot(file = "auth-users.schema.json")
data class UsersConfig(val users: List<UserEntry> = emptyList())
