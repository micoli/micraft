package org.micoli.micraft.http

import io.github.smiley4.ktoropenapi.get
import io.github.smiley4.ktoropenapi.post
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.server.response.respond
import io.ktor.server.response.respondText
import io.ktor.server.routing.Route
import io.ktor.server.routing.RoutingContext
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import org.micoli.micraft.auth.TokenStore
import org.micoli.micraft.game.GameLoop
import org.micoli.micraft.protocol.AuctionListing

private val json = Json { encodeDefaults = true }

class AuctionsController(
    private val gameLoop: GameLoop,
    private val tokenStore: TokenStore? = null,
) {
    private suspend fun RoutingContext.requireAdmin(): Boolean {
        tokenStore ?: return true
        val token = call.request.headers[HttpHeaders.Authorization]?.removePrefix("Bearer ")?.trim()
        val auth = if (token != null) tokenStore.validate(token) else null
        if (auth == null) {
            call.respond(HttpStatusCode.Unauthorized)
            return false
        }
        if ("*" !in auth.permissions && "admin" !in auth.permissions) {
            call.respond(HttpStatusCode.Forbidden)
            return false
        }
        return true
    }

    fun register(route: Route) =
        route.apply {
            get(
                "/api/admin/auctions",
                {
                    description = "List all auction listings, any status"
                    response {
                        code(HttpStatusCode.OK) { body<List<AuctionListing>>() }
                        code(HttpStatusCode.Unauthorized) {
                            description = "Missing or invalid token"
                        }
                        code(HttpStatusCode.Forbidden) { description = "Missing admin permission" }
                    }
                }) {
                    if (!requireAdmin()) return@get
                    val listings = gameLoop.getAuctionManager()?.getAll()?.toList() ?: emptyList()
                    call.respondText(
                        json.encodeToString(ListSerializer(AuctionListing.serializer()), listings),
                        ContentType.Application.Json,
                    )
                }
            post(
                "/api/admin/auctions/{id}/force-cancel",
                {
                    description =
                        "Force-cancels a listing: returns the item to the seller and refunds the highest bidder, no tax"
                    request { pathParameter<String>("id") { description = "Listing id" } }
                    response {
                        code(HttpStatusCode.OK) { description = "Cancelled" }
                        code(HttpStatusCode.NotFound) {
                            description = "Listing not found or already terminal"
                        }
                        code(HttpStatusCode.Unauthorized) {
                            description = "Missing or invalid token"
                        }
                        code(HttpStatusCode.Forbidden) { description = "Missing admin permission" }
                    }
                }) {
                    if (!requireAdmin()) return@post
                    val id = call.parameters["id"]
                    if (id == null) {
                        call.respond(HttpStatusCode.NotFound)
                        return@post
                    }
                    val ok = gameLoop.getAuctionManager()?.adminForceCancel(id) ?: false
                    call.respond(if (ok) HttpStatusCode.OK else HttpStatusCode.NotFound)
                }
        }
}
