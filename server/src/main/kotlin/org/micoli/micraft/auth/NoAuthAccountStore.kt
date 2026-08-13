package org.micoli.micraft.auth

import com.charleskorn.kaml.Yaml
import java.nio.file.Path
import kotlin.io.path.createDirectories
import kotlin.io.path.exists
import kotlin.io.path.readText
import kotlin.io.path.writeText
import kotlinx.serialization.Serializable
import org.micoli.micraft.schema.JsonSchemaRoot

@Serializable data class NoAuthAccount(val email: String)

@Serializable
@JsonSchemaRoot(file = "noauth-accounts.schema.json")
data class NoAuthAccountsConfig(val accounts: List<NoAuthAccount> = emptyList())

class NoAuthAccountStore(private val file: Path) {
    private fun load(): NoAuthAccountsConfig =
        if (file.exists())
            runCatching {
                    Yaml.default.decodeFromString(
                        NoAuthAccountsConfig.serializer(), file.readText())
                }
                .getOrDefault(NoAuthAccountsConfig())
        else NoAuthAccountsConfig()

    fun exists(email: String): Boolean =
        load().accounts.any { it.email.equals(email, ignoreCase = true) }

    fun listAccounts(): List<NoAuthAccount> = load().accounts

    @Synchronized
    fun delete(email: String) {
        val config = load()
        val updated =
            config.copy(
                accounts = config.accounts.filter { !it.email.equals(email, ignoreCase = true) })
        file.parent?.createDirectories()
        file.writeText(Yaml.default.encodeToString(NoAuthAccountsConfig.serializer(), updated))
    }

    @Synchronized
    fun getOrCreate(email: String): NoAuthAccount {
        val config = load()
        val existing = config.accounts.firstOrNull { it.email.equals(email, ignoreCase = true) }
        if (existing != null) return existing
        val newAccount = NoAuthAccount(email)
        val updated = config.copy(accounts = config.accounts + newAccount)
        file.parent?.createDirectories()
        file.writeText(Yaml.default.encodeToString(NoAuthAccountsConfig.serializer(), updated))
        return newAccount
    }
}
