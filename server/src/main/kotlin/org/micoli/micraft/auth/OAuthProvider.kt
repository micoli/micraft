package org.micoli.micraft.auth

import io.ktor.client.*
import io.ktor.client.engine.cio.*
import io.ktor.client.request.*
import io.ktor.client.request.forms.*
import io.ktor.client.statement.*
import io.ktor.http.*
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.micoli.micraft.game.OAuthConfig

class OAuthProvider(private val config: OAuthConfig, @Volatile var groupsConfig: GroupsConfig) :
    AuthProvider {
    private data class StateEntry(val returnUrl: String)

    private val stateMap = ConcurrentHashMap<String, StateEntry>()
    private val http = HttpClient(CIO)
    private val json = Json { ignoreUnknownKeys = true }

    override fun oauthStartUrl(returnUrl: String): String {
        val state = UUID.randomUUID().toString()
        stateMap[state] = StateEntry(returnUrl)
        val params =
            Parameters.build {
                append("client_id", config.clientId)
                append("redirect_uri", config.redirectUri)
                append("response_type", "code")
                append("scope", config.scopes.joinToString(" "))
                append("state", state)
                append("access_type", "offline")
            }
        return "https://accounts.google.com/o/oauth2/v2/auth?${params.formUrlEncode()}"
    }

    override fun oauthReturnUrl(state: String): String? = stateMap[state]?.returnUrl

    override suspend fun oauthCallback(code: String, state: String): AuthResult? {
        stateMap.remove(state) ?: return null

        val tokenResponse =
            runCatching {
                    http
                        .submitForm(
                            url = "https://oauth2.googleapis.com/token",
                            formParameters =
                                Parameters.build {
                                    append("code", code)
                                    append("client_id", config.clientId)
                                    append("client_secret", config.clientSecret)
                                    append("redirect_uri", config.redirectUri)
                                    append("grant_type", "authorization_code")
                                },
                        )
                        .bodyAsText()
                }
                .getOrNull() ?: return null

        val accessToken =
            json
                .parseToJsonElement(tokenResponse)
                .jsonObject["access_token"]
                ?.jsonPrimitive
                ?.content ?: return null

        val userInfo =
            runCatching {
                    http
                        .get("https://www.googleapis.com/oauth2/v3/userinfo") {
                            bearerAuth(accessToken)
                        }
                        .bodyAsText()
                }
                .getOrNull() ?: return null

        val userJson = json.parseToJsonElement(userInfo).jsonObject
        val sub = userJson["sub"]?.jsonPrimitive?.content ?: return null
        val name = userJson["name"]?.jsonPrimitive?.content ?: sub
        val permissions = groupsConfig.resolveDefaultPermissions()
        return AuthResult(playerId = sub, displayName = name, permissions = permissions)
    }
}
