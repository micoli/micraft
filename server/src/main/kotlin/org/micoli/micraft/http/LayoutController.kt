package org.micoli.micraft.http

import io.github.smiley4.ktoropenapi.get
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
            get(
                "/api/layout/registry",
                {
                    description = "All widgets registered for the UI layout editor"
                    response { code(HttpStatusCode.OK) { body<List<WidgetRegistryEntry>>() } }
                }) {
                    call.respondText(
                        Json.encodeToString(
                            ListSerializer(WidgetRegistryEntry.serializer()), WIDGET_REGISTRY),
                        ContentType.Application.Json)
                }
        }
}
