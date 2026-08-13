package org.micoli.micraft.http

import io.github.smiley4.ktoropenapi.post
import io.ktor.http.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import java.io.File
import java.time.Instant
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.Base64
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

private val TIMESTAMP_FMT: DateTimeFormatter =
    DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH-mm-ss").withZone(ZoneOffset.UTC)

@Serializable private data class UploadScreenshotRequest(val imageData: String)

@Serializable private data class UploadScreenshotResponse(val filename: String)

class ScreenshotController(private val dataPath: String) {
    fun register(route: Route) =
        route.apply {
            post(
                "/api/player/{id}/screenshots",
                {
                    description =
                        "Upload a player screenshot (base64 PNG, optionally as a data: URI)"
                    request {
                        pathParameter<String>("id") { description = "Player id" }
                        body<UploadScreenshotRequest>()
                    }
                    response {
                        code(HttpStatusCode.Created) { body<UploadScreenshotResponse>() }
                        code(HttpStatusCode.BadRequest) {
                            description = "Invalid body or base64 data"
                        }
                    }
                }) {
                    val id =
                        call.parameters["id"] ?: return@post call.respond(HttpStatusCode.BadRequest)
                    val body =
                        runCatching { call.receiveText() }
                            .getOrElse {
                                return@post call.respond(HttpStatusCode.BadRequest)
                            }
                    val json = Json { ignoreUnknownKeys = true }
                    val imageData =
                        runCatching {
                                json
                                    .parseToJsonElement(body)
                                    .jsonObject["imageData"]
                                    ?.jsonPrimitive
                                    ?.content
                            }
                            .getOrNull() ?: return@post call.respond(HttpStatusCode.BadRequest)

                    val base64 =
                        if (imageData.startsWith("data:")) imageData.substringAfter(",")
                        else imageData

                    val bytes =
                        runCatching { Base64.getDecoder().decode(base64) }
                            .getOrElse {
                                return@post call.respond(HttpStatusCode.BadRequest)
                            }

                    val dir = File("$dataPath/screenshots/$id")
                    dir.mkdirs()
                    val filename = "${TIMESTAMP_FMT.format(Instant.now())}.png"
                    File(dir, filename).writeBytes(bytes)

                    call.respondText(
                        """{"filename":"$filename"}""",
                        ContentType.Application.Json,
                        HttpStatusCode.Created,
                    )
                }
        }
}
