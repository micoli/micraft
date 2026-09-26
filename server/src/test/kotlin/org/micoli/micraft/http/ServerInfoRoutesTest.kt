package org.micoli.micraft.http

import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpStatusCode
import io.ktor.server.routing.routing
import io.ktor.server.testing.testApplication
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlinx.serialization.json.Json
import org.micoli.micraft.protocol.PROTOCOL_FINGERPRINT

class ServerInfoRoutesTest {
    @Test
    fun `server info exposes the protocol fingerprint the client compares`() = testApplication {
        application { routing { ServerInfoController().register(this) } }

        val r = client.get("/api/server/info")

        assertEquals(HttpStatusCode.OK, r.status)
        assertEquals(
            PROTOCOL_FINGERPRINT,
            Json.decodeFromString<ServerInfo>(r.bodyAsText()).protocolFingerprint)
    }
}
