package org.micoli.micraft.auth

import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class NoAuthAccountStoreTest {

    @Test
    fun `getOrCreate creates new account on first use`() {
        val file = Files.createTempFile("noauth-accounts", ".yaml")
        file.toFile().delete()
        val store = NoAuthAccountStore(file)

        assertFalse(store.exists("alice@test.com"))
        val account = store.getOrCreate("alice@test.com")
        assertEquals("alice@test.com", account.email)
        assertTrue(store.exists("alice@test.com"))

        file.toFile().delete()
    }

    @Test
    fun `getOrCreate is idempotent for same email`() {
        val file = Files.createTempFile("noauth-accounts", ".yaml")
        file.toFile().delete()
        val store = NoAuthAccountStore(file)

        store.getOrCreate("bob@test.com")
        store.getOrCreate("bob@test.com")

        val config =
            com.charleskorn.kaml.Yaml.default.decodeFromString(
                NoAuthAccountsConfig.serializer(), file.toFile().readText())
        assertEquals(
            1, config.accounts.count { it.email.equals("bob@test.com", ignoreCase = true) })

        file.toFile().delete()
    }

    @Test
    fun `exists is case-insensitive`() {
        val file = Files.createTempFile("noauth-accounts", ".yaml")
        file.toFile().delete()
        val store = NoAuthAccountStore(file)

        store.getOrCreate("Carol@Test.COM")
        assertTrue(store.exists("carol@test.com"))

        file.toFile().delete()
    }
}
