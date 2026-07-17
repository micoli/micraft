package org.micoli.micraft.auth

import com.auth0.jwt.JWT
import com.auth0.jwt.algorithms.Algorithm
import com.auth0.jwt.exceptions.JWTVerificationException
import java.util.Date
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class TokenStore(scope: CoroutineScope, private val ttlSeconds: Long = 600) {
    private val algorithm = Algorithm.HMAC256(UUID.randomUUID().toString().replace("-", ""))
    private val verifier = JWT.require(algorithm).build()
    private val issued = ConcurrentHashMap.newKeySet<String>()

    init {
        scope.launch {
            while (true) {
                delay(60_000)
                issued.removeIf { !isJwtValid(it) }
            }
        }
    }

    private fun isJwtValid(token: String): Boolean =
        runCatching { verifier.verify(token) }.isSuccess

    fun issue(result: AuthResult): String {
        val now = System.currentTimeMillis()
        val token =
            JWT.create()
                .withJWTId(UUID.randomUUID().toString())
                .withSubject(result.playerId)
                .withClaim("name", result.displayName)
                .withClaim("email", result.email)
                .withClaim("perms", result.permissions.joinToString(","))
                .withIssuedAt(Date(now))
                .withExpiresAt(Date(now + ttlSeconds * 1000L))
                .sign(algorithm)
        issued.add(token)
        return token
    }

    fun validate(token: String): AuthResult? {
        if (!issued.contains(token)) return null
        return try {
            val decoded = verifier.verify(token)
            val permsStr = decoded.getClaim("perms").asString() ?: ""
            val permissions = if (permsStr.isEmpty()) emptySet() else permsStr.split(",").toSet()
            AuthResult(
                playerId = decoded.subject,
                displayName = decoded.getClaim("name").asString() ?: "",
                token = token,
                permissions = permissions,
                email = decoded.getClaim("email").asString() ?: decoded.subject,
            )
        } catch (_: JWTVerificationException) {
            issued.remove(token)
            null
        }
    }
}
