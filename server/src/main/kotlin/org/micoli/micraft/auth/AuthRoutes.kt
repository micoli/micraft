package org.micoli.micraft.auth

import io.github.smiley4.ktoropenapi.get
import io.github.smiley4.ktoropenapi.post
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import java.net.URLEncoder
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

@Serializable
private data class AuthConfigResponse(val provider: String, val messageEncoder: String)

@Serializable
private data class AuthMeResponse(val playerId: String, val displayName: String, val email: String)

@Serializable private data class NoAuthLoginRequest(val email: String)

@Serializable private data class NoAuthLoginResponse(val email: String)

@Serializable private data class LoginRequest(val email: String, val password: String)

@Serializable
private data class LoginResponse(
    val token: String,
    val displayName: String,
    val playerId: String,
    val email: String,
)

fun Application.installAuthRoutes(
    providerName: String,
    provider: AuthProvider?,
    tokenStore: TokenStore?,
    messageEncoder: String,
    noAuthAccountStore: NoAuthAccountStore? = null,
) {
    routing {
        get(
            "/api/auth/config",
            {
                description = "Active auth provider, used by the client to pick the right login UI"
                response { code(HttpStatusCode.OK) { body<AuthConfigResponse>() } }
            }) {
                call.respondText(
                    """{"provider":"$providerName","messageEncoder":"$messageEncoder"}""",
                    ContentType.Application.Json,
                )
            }

        if (tokenStore != null) {
            get(
                "/auth/me",
                {
                    description = "Resolve the current session from its bearer token"
                    response {
                        code(HttpStatusCode.OK) { body<AuthMeResponse>() }
                        code(HttpStatusCode.Unauthorized) {
                            description = "Missing or invalid token"
                        }
                    }
                }) {
                    val authHeader =
                        call.request.headers["Authorization"]
                            ?: run {
                                call.respond(HttpStatusCode.Unauthorized)
                                return@get
                            }
                    val token = authHeader.removePrefix("Bearer ").trim()
                    val result = tokenStore.validate(token)
                    if (result == null) {
                        call.respond(HttpStatusCode.Unauthorized)
                        return@get
                    }
                    call.respondText(
                        """{"playerId":"${result.playerId}","displayName":"${result.displayName.replace("\"", "\\\"")}","email":"${result.email}"}""",
                        ContentType.Application.Json,
                    )
                }
        }

        if (noAuthAccountStore != null) {
            post(
                "/auth/noauth-login",
                {
                    description =
                        "Create/reuse an account by email when auth is disabled (auth.provider=none)"
                    request { body<NoAuthLoginRequest>() }
                    response {
                        code(HttpStatusCode.OK) { body<NoAuthLoginResponse>() }
                        code(HttpStatusCode.BadRequest) { description = "Missing or invalid email" }
                    }
                }) {
                    val body =
                        runCatching { call.receiveText() }
                            .getOrElse {
                                call.respond(HttpStatusCode.BadRequest)
                                return@post
                            }
                    val json = Json { ignoreUnknownKeys = true }
                    val obj =
                        runCatching { json.parseToJsonElement(body).jsonObject }
                            .getOrElse {
                                call.respond(HttpStatusCode.BadRequest)
                                return@post
                            }
                    val email =
                        obj["email"]?.jsonPrimitive?.content?.trim()
                            ?: run {
                                call.respond(HttpStatusCode.BadRequest)
                                return@post
                            }
                    if (!email.matches(Regex("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$"))) {
                        call.respond(HttpStatusCode.BadRequest)
                        return@post
                    }
                    noAuthAccountStore.getOrCreate(email)
                    call.respondText(
                        """{"email":"${email.replace("\"", "\\\"")}"}""",
                        ContentType.Application.Json,
                    )
                }
        }

        if (provider == null || tokenStore == null) return@routing

        post(
            "/auth/login",
            {
                description = "Log in with email/password (local auth provider)"
                request { body<LoginRequest>() }
                response {
                    code(HttpStatusCode.OK) { body<LoginResponse>() }
                    code(HttpStatusCode.BadRequest) { description = "Missing email or password" }
                    code(HttpStatusCode.Unauthorized) { description = "Invalid credentials" }
                }
            }) {
                val body =
                    runCatching { call.receiveText() }
                        .getOrElse {
                            call.respond(HttpStatusCode.BadRequest)
                            return@post
                        }
                val json = Json { ignoreUnknownKeys = true }
                val obj =
                    runCatching { json.parseToJsonElement(body).jsonObject }
                        .getOrElse {
                            call.respond(HttpStatusCode.BadRequest)
                            return@post
                        }
                val email =
                    obj["email"]?.jsonPrimitive?.content
                        ?: run {
                            call.respond(HttpStatusCode.BadRequest)
                            return@post
                        }
                val password =
                    obj["password"]?.jsonPrimitive?.content
                        ?: run {
                            call.respond(HttpStatusCode.BadRequest)
                            return@post
                        }
                val result = provider.login(email, password)
                if (result == null) {
                    call.respond(HttpStatusCode.Unauthorized)
                    return@post
                }
                val token = tokenStore.issue(result)
                call.respondText(
                    """{"token":"$token","displayName":"${result.displayName.replace("\"", "\\\"")}","playerId":"${result.playerId}","email":"${result.email}"}""",
                    ContentType.Application.Json,
                )
            }

        get(
            "/auth/oauth/start",
            {
                description = "Redirect to the OAuth provider's consent screen"
                request {
                    queryParameter<String>("returnUrl") {
                        description = "URL to return to after login"
                        required = false
                    }
                }
                response {
                    code(HttpStatusCode.Found) { description = "Redirect to the OAuth provider" }
                    code(HttpStatusCode.NotFound) { description = "OAuth not configured" }
                }
            }) {
                val returnUrl =
                    call.request.queryParameters["returnUrl"]
                        ?: call.request.headers["Referer"]
                        ?: "/"
                val url = provider.oauthStartUrl(returnUrl)
                if (url == null) {
                    call.respond(HttpStatusCode.NotFound)
                    return@get
                }
                call.respondRedirect(url)
            }

        get(
            "/auth/callback",
            {
                description =
                    "OAuth authorization code callback — redirects with the session token in the URL fragment"
                request {
                    queryParameter<String>("code") { description = "Authorization code" }
                    queryParameter<String>("state") { description = "CSRF state token" }
                }
                response {
                    code(HttpStatusCode.Found) {
                        description = "Redirect to returnUrl#auth_token=..."
                    }
                    code(HttpStatusCode.BadRequest) { description = "Missing code or state" }
                    code(HttpStatusCode.Unauthorized) { description = "OAuth exchange failed" }
                }
            }) {
                val code =
                    call.parameters["code"]
                        ?: run {
                            call.respond(HttpStatusCode.BadRequest)
                            return@get
                        }
                val state =
                    call.parameters["state"]
                        ?: run {
                            call.respond(HttpStatusCode.BadRequest)
                            return@get
                        }
                val returnUrl = provider.oauthReturnUrl(state) ?: "/"
                val result = provider.oauthCallback(code, state)
                if (result == null) {
                    call.respond(HttpStatusCode.Unauthorized)
                    return@get
                }
                val token = tokenStore.issue(result)
                val encodedName =
                    URLEncoder.encode(result.displayName, Charsets.UTF_8).replace("+", "%20")
                val encodedEmail =
                    URLEncoder.encode(result.email, Charsets.UTF_8).replace("+", "%20")
                call.respondRedirect(
                    "$returnUrl#auth_token=$token&auth_name=$encodedName&auth_email=$encodedEmail")
            }
    }
}
