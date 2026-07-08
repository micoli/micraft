package org.micoli.micraft.http

import io.ktor.http.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import org.micoli.micraft.game.macro.MACRO_CONTEXT_SCHEMA
import org.micoli.micraft.game.macro.MacroContextVar

class MacrosController {
    fun register(route: Route) =
        route.apply {
            get("/api/macros/context") {
                call.respondText(
                    Json.encodeToString(
                        ListSerializer(MacroContextVar.serializer()), MACRO_CONTEXT_SCHEMA),
                    ContentType.Application.Json,
                )
            }
        }
}
