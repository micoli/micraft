package org.micoli.micraft.auth

import at.favre.lib.crypto.bcrypt.BCrypt
import com.charleskorn.kaml.Yaml
import java.nio.file.Path
import java.util.UUID
import kotlin.io.path.createDirectories
import kotlin.io.path.exists
import kotlin.io.path.readText
import kotlin.io.path.writeText
import org.micoli.micraft.config.validateYamlConfig

/** bcrypt work factor. Never lower this in production — only tests may pass a cheaper cost. */
const val DEFAULT_BCRYPT_COST = 12

private val EMAIL_REGEX = Regex("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$")

class LocalAuthProvider(
    private val usersFile: Path,
    @Volatile var groupsConfig: GroupsConfig,
    private val bcryptCost: Int = DEFAULT_BCRYPT_COST,
    private val requirePassword: Boolean = true,
) : AuthProvider {
    private fun load(): UsersConfig =
        if (usersFile.exists())
            runCatching {
                    validateYamlConfig(usersFile, "auth-users.schema.json")
                    Yaml.default.decodeFromString(UsersConfig.serializer(), usersFile.readText())
                }
                .getOrDefault(UsersConfig())
        else UsersConfig()

    override suspend fun login(email: String, password: String): AuthResult? {
        var user = load().users.firstOrNull { it.email.equals(email, ignoreCase = true) }
        if (user == null) {
            // Passwordless local auth behaves like `none`: an unknown email is provisioned on
            // first login instead of rejected — but still gets a real RBAC group, unlike `none`.
            if (requirePassword || !email.matches(EMAIL_REGEX)) return null
            runCatching { addUser(email, password, email, groupsConfig.defaultGroups) }
            user =
                load().users.firstOrNull { it.email.equals(email, ignoreCase = true) }
                    ?: return null
        } else if (requirePassword) {
            val result = BCrypt.verifyer().verify(password.toCharArray(), user.passwordHash)
            if (!result.verified) return null
        }
        val permissions = groupsConfig.resolvePermissions(user.groups)
        return AuthResult(
            playerId = user.email,
            displayName = user.displayName,
            permissions = permissions,
            email = user.email,
        )
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
        val secret = if (requirePassword) password else UUID.randomUUID().toString()
        val hash = BCrypt.withDefaults().hashToString(bcryptCost, secret.toCharArray())
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

    fun listUsers(): List<UserEntry> = load().users

    fun deleteUser(email: String) {
        val config = load()
        if (config.users.none { it.email.equals(email, ignoreCase = true) })
            error("User not found: $email")
        val updated =
            config.copy(users = config.users.filter { !it.email.equals(email, ignoreCase = true) })
        usersFile.writeText(Yaml.default.encodeToString(UsersConfig.serializer(), updated))
    }

    fun updateUser(email: String, displayName: String?, groups: List<String>?) {
        val config = load()
        val updated =
            config.copy(
                users =
                    config.users.map {
                        if (it.email.equals(email, ignoreCase = true))
                            it.copy(
                                displayName = displayName ?: it.displayName,
                                groups = groups ?: it.groups,
                            )
                        else it
                    })
        usersFile.writeText(Yaml.default.encodeToString(UsersConfig.serializer(), updated))
    }
}
