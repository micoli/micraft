package org.micoli.micraft.auth

import java.time.Instant
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class TokenStore(scope: CoroutineScope, private val ttlSeconds: Long = 600) {
    private data class Entry(val result: AuthResult, val expiresAt: Instant)

    private val store = ConcurrentHashMap<String, Entry>()

    init {
        scope.launch {
            while (true) {
                delay(60_000)
                val now = Instant.now()
                store.entries.removeIf { it.value.expiresAt.isBefore(now) }
            }
        }
    }

    fun issue(result: AuthResult): String {
        val token = UUID.randomUUID().toString()
        store[token] = Entry(result.copy(token = token), Instant.now().plusSeconds(ttlSeconds))
        return token
    }

    fun validate(token: String): AuthResult? {
        val entry = store[token] ?: return null
        if (entry.expiresAt.isBefore(Instant.now())) {
            store.remove(token)
            return null
        }
        return entry.result
    }
}
