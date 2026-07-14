package org.micoli.micraft.auth

data class AuthResult(
    val playerId: String,
    val displayName: String,
    val token: String = "",
    val permissions: Set<String> = emptySet(),
    val email: String = playerId,
)

interface AuthProvider {
    suspend fun login(email: String, password: String): AuthResult? = null

    fun oauthStartUrl(returnUrl: String): String? = null

    suspend fun oauthCallback(code: String, state: String): AuthResult? = null

    fun oauthReturnUrl(state: String): String? = null
}
