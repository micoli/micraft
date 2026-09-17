package org.micoli.micraft.http

import io.github.smiley4.ktoropenapi.get
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.server.response.respondText
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.route
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import org.micoli.micraft.game.macro.MACRO_CONTEXT_SCHEMA
import org.micoli.micraft.game.macro.MacroContextVar

class MacrosController {
    fun register(route: Route) =
        route.apply {
            get(
                "/api/macros/context",
                {
                    description = "Variables available to the macro JEXL evaluation context"
                    response { code(HttpStatusCode.OK) { body<List<MacroContextVar>>() } }
                }) {
                    call.respondText(
                        Json.encodeToString(
                            ListSerializer(MacroContextVar.serializer()), MACRO_CONTEXT_SCHEMA),
                        ContentType.Application.Json,
                    )
                }
        }
}
