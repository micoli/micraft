package org.micoli.micraft.auth

import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import java.net.URLEncoder
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

fun Application.installAuthRoutes(
    providerName: String,
    provider: AuthProvider?,
    tokenStore: TokenStore?,
) {
    routing {
        get("/api/auth/config") {
            call.respondText(
                """{"provider":"$providerName"}""",
                ContentType.Application.Json,
            )
        }

        if (tokenStore != null) {
            get("/auth/me") {
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
                    """{"playerId":"${result.playerId}","displayName":"${result.displayName.replace("\"", "\\\"")}"}""",
                    ContentType.Application.Json,
                )
            }
        }

        if (provider == null || tokenStore == null) return@routing

        post("/auth/login") {
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
                """{"token":"$token","displayName":"${result.displayName.replace("\"", "\\\"")}","playerId":"${result.playerId}"}""",
                ContentType.Application.Json,
            )
        }

        get("/auth/oauth/start") {
            val returnUrl =
                call.request.queryParameters["returnUrl"] ?: call.request.headers["Referer"] ?: "/"
            val url = provider.oauthStartUrl(returnUrl)
            if (url == null) {
                call.respond(HttpStatusCode.NotFound)
                return@get
            }
            call.respondRedirect(url)
        }

        get("/auth/callback") {
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
            call.respondRedirect("$returnUrl#auth_token=$token&auth_name=$encodedName")
        }
    }
}
