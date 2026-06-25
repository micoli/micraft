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
)

@Serializable data class UsersConfig(val users: List<UserEntry> = emptyList())

class LocalAuthProvider(private val usersFile: Path) : AuthProvider {
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
        return AuthResult(playerId = user.email, displayName = user.displayName)
    }

    fun addUser(email: String, password: String, displayName: String = email) {
        val config = load()
        if (config.users.any { it.email.equals(email, ignoreCase = true) })
            error("User already exists: $email")
        val hash = BCrypt.withDefaults().hashToString(12, password.toCharArray())
        val updated = config.copy(users = config.users + UserEntry(email, hash, displayName))
        usersFile.parent?.createDirectories()
        usersFile.writeText(Yaml.default.encodeToString(UsersConfig.serializer(), updated))
    }
}
