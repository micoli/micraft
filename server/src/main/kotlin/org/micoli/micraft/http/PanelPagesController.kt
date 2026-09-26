package org.micoli.micraft.http

import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.server.response.header
import io.ktor.server.response.respond
import io.ktor.server.response.respondText
import io.ktor.server.routing.Route
import io.ktor.server.routing.get

const val PANEL_PAGE_CSP =
    "sandbox; default-src 'none'; style-src 'unsafe-inline'; img-src data:; " +
        "base-uri 'none'; form-action 'none'"

private const val BASE_STYLE =
    "body{margin:0;padding:16px;font:16px/1.4 system-ui,sans-serif;color:#eee;background:#1b1b1f}" +
        "a{color:#7cc4ff}h1,h2,h3{margin:.4em 0}img{max-width:100%}"

fun renderPanelDocument(bodyHtml: String): String =
    "<!doctype html><html><head><meta charset=\"utf-8\">" +
        "<meta name=\"viewport\" content=\"width=device-width,initial-scale=1\">" +
        "<style>$BASE_STYLE</style></head><body>$bodyHtml</body></html>"

/**
 * Serves a panel's locally-stored (already sanitized) pages. [pageProvider] returns the page body
 * for `(placeableId, pageName)` or null. Responses carry a strict CSP with `sandbox`, so even a
 * sanitizer bypass could not run script or load anything remote.
 */
class PanelPagesController(private val pageProvider: (String, String) -> String?) {
    fun register(route: Route) =
        route.apply {
            get("/panels/{placeableId}/{page}") {
                val id = call.parameters["placeableId"]
                val page = call.parameters["page"]?.removeSuffix(".html")
                val body = if (id == null || page == null) null else pageProvider(id, page)
                if (body == null) {
                    call.respond(HttpStatusCode.NotFound)
                    return@get
                }
                call.response.header("Content-Security-Policy", PANEL_PAGE_CSP)
                call.response.header("X-Content-Type-Options", "nosniff")
                call.response.header("Cache-Control", "no-store")
                call.respondText(renderPanelDocument(body), ContentType.Text.Html)
            }
        }
}
