package org.micoli.micraft.http

import io.ktor.http.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import org.micoli.micraft.ui.WIDGET_REGISTRY
import org.micoli.micraft.ui.WidgetRegistryEntry

class LayoutController {
    fun register(route: Route) =
        route.apply {
            get("/api/layout/registry") {
                call.respondText(
                    Json.encodeToString(
                        ListSerializer(WidgetRegistryEntry.serializer()), WIDGET_REGISTRY),
                    ContentType.Application.Json)
            }
        }
}
