package org.micoli.micraft.http

import io.ktor.client.plugins.websocket.webSocket
import io.ktor.client.request.post
import io.ktor.http.HttpStatusCode
import io.ktor.server.testing.testApplication
import io.ktor.websocket.Frame
import io.ktor.websocket.readText
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlinx.coroutines.launch
import org.micoli.micraft.module

/** Verifies the asset-reload push path: POST /api/assets/reload broadcasts a reload over /ws. */
class AssetNotifyControllerTest {

    @Test
    fun testReloadEndpointReturns204() = testApplication {
        application { module() }
        val r = client.post("/api/assets/reload")
        assertEquals(HttpStatusCode.NoContent, r.status)
    }

    @Test
    fun testReloadBroadcastsToConnectedClient() = testApplication {
        application { module() }
        val wsClient = createClient { install(io.ktor.client.plugins.websocket.WebSockets) }
        wsClient.webSocket("/ws") {
            // Trigger the forced-reload broadcast once the socket is connected.
            val postJob = launch { client.post("/api/assets/reload") }
            var received: String? = null
            for (frame in incoming) {
                if (frame is Frame.Text) {
                    val text = frame.readText()
                    if (text.contains("\"reload\"")) {
                        received = text
                        break
                    }
                }
            }
            postJob.join()
            assertTrue(received != null && received.contains("\"type\":\"reload\""))
        }
    }
}
