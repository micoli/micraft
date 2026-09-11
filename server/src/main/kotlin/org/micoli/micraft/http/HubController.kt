package org.micoli.micraft.http

import io.ktor.http.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.server.routing.get as undocumentedGet
import io.ktor.server.websocket.*
import java.io.File
import org.micoli.micraft.I18nConfig
import org.micoli.micraft.auth.NoAuthAccountStore
import org.micoli.micraft.auth.TokenStore
import org.micoli.micraft.game.GameLoop
import org.micoli.micraft.game.hub.HubConnection

/**
 * Serves the `/hub` web-companion SPA shell and its WebSocket. `hub.js`/`hub.css` (esbuild/tailwind
 * output from `make build-hub`) are NOT read here — like admin.js/css, they ride the generic
 * `staticFiles("/", ...)` mount in Application.kt from `$MICRAFT_WEB_DIST`, always fresh.
 */
class HubController(
    private val gameLoop: GameLoop,
    private val tokenStore: TokenStore?,
    private val noAuthAccountStore: NoAuthAccountStore?,
    private val i18n: I18nConfig,
) {
    fun register(route: Route) {
        if (System.getenv("MICRAFT_HUB_ENABLED") == "0") return

        route.apply {
            // SPA deep links (/hub/mail, /hub/auction, ...) all serve the same shell — the
            // client-side router picks the screen. Plain (undocumented) routing.get: markup, not a
            // JSON API, and excluded from the OpenAPI spec anyway (Application.kt pathFilter).
            undocumentedGet("/hub") { call.respondFile(File("server/src/main/resources/hub.html")) }
            undocumentedGet("/hub/{...}") {
                call.respondFile(File("server/src/main/resources/hub.html"))
            }

            webSocket("/hub") {
                HubConnection(gameLoop.gameWorldRegistry, tokenStore, noAuthAccountStore, i18n)
                    .handle(
                        this,
                        call.request.queryParameters["lang"],
                        call.request.queryParameters["gameSession"])
            }
        }
    }
}
