package org.micoli.micraft.auth

import com.auth0.jwt.JWT
import com.auth0.jwt.algorithms.Algorithm
import com.auth0.jwt.exceptions.JWTVerificationException
import java.nio.file.Files
import java.nio.file.Path
import java.util.Date
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.micoli.micraft.config.ConfigPaths

/**
 * Reads the JWT signing secret from [path], generating and persisting one on first run. Keeping it
 * stable across restarts is what lets a token issued before `make dev-restart-server` still
 * validate after — a random per-boot secret would otherwise force every connected client back to
 * the login screen on every server restart.
 */
fun loadOrCreateJwtSecret(path: Path = ConfigPaths.dataConfig("jwt.secret")): String {
    if (Files.exists(path)) return Files.readString(path).trim()
    val secret =
        UUID.randomUUID().toString().replace("-", "") +
            UUID.randomUUID().toString().replace("-", "")
    Files.createDirectories(path.parent)
    Files.writeString(path, secret)
    return secret
}

class TokenStore(
    scope: CoroutineScope,
    private val ttlSeconds: Long = 600,
    secret: String = loadOrCreateJwtSecret(),
) {
    private val algorithm = Algorithm.HMAC256(secret)
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
                .withClaim("perms", result.permissions.joinToString(",") { it.id })
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
            val permissions =
                if (permsStr.isEmpty()) emptySet()
                else permsStr.split(",").map { Permission(it) }.toSet()
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
