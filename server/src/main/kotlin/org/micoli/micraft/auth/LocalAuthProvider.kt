package org.micoli.micraft.auth

import at.favre.lib.crypto.bcrypt.BCrypt
import com.charleskorn.kaml.Yaml
import java.nio.file.Path
import kotlin.io.path.createDirectories
import kotlin.io.path.exists
import kotlin.io.path.readText
import kotlin.io.path.writeText
import kotlinx.serialization.Serializable

@Serializable
data class UserEntry(
    val email: String,
    val passwordHash: String,
    val displayName: String = email,
    val groups: List<String> = emptyList(),
)

@Serializable data class UsersConfig(val users: List<UserEntry> = emptyList())

class LocalAuthProvider(private val usersFile: Path, @Volatile var groupsConfig: GroupsConfig) :
    AuthProvider {
    private fun load(): UsersConfig =
        if (usersFile.exists())
            runCatching {
                    Yaml.default.decodeFromString(UsersConfig.serializer(), usersFile.readText())
                }
                .getOrDefault(UsersConfig())
        else UsersConfig()

    override suspend fun login(email: String, password: String): AuthResult? {
        val user =
            load().users.firstOrNull { it.email.equals(email, ignoreCase = true) } ?: return null
        val result = BCrypt.verifyer().verify(password.toCharArray(), user.passwordHash)
        if (!result.verified) return null
        val permissions = groupsConfig.resolvePermissions(user.groups)
        return AuthResult(
            playerId = user.email, displayName = user.displayName, permissions = permissions)
    }

    fun addUser(
        email: String,
        password: String,
        displayName: String = email,
        groups: List<String> = emptyList(),
    ) {
        val config = load()
        if (config.users.any { it.email.equals(email, ignoreCase = true) })
            error("User already exists: $email")
        val hash = BCrypt.withDefaults().hashToString(12, password.toCharArray())
        val updated =
            config.copy(users = config.users + UserEntry(email, hash, displayName, groups))
        usersFile.parent?.createDirectories()
        usersFile.writeText(Yaml.default.encodeToString(UsersConfig.serializer(), updated))
    }

    fun setUserGroups(email: String, groups: List<String>) {
        val config = load()
        val user =
            config.users.firstOrNull { it.email.equals(email, ignoreCase = true) }
                ?: error("User not found: $email")
        val updated =
            config.copy(
                users =
                    config.users.map {
                        if (it.email.equals(email, ignoreCase = true)) it.copy(groups = groups)
                        else it
                    })
        usersFile.writeText(Yaml.default.encodeToString(UsersConfig.serializer(), updated))
    }

    fun getUserGroups(email: String): List<String>? =
        load().users.firstOrNull { it.email.equals(email, ignoreCase = true) }?.groups
}
