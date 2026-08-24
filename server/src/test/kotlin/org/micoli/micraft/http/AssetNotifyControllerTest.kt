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
            // The server registers the session and sends this "ready" ack from the same
            // coroutine, in that order — waiting for it here guarantees the session is
            // registered before we trigger the reload below, avoiding a race where the POST's
            // broadcast fires before the server has stored this session (flaky under load).
            var frame = incoming.receive()
            while (frame !is Frame.Text || !frame.readText().contains("\"ready\"")) {
                frame = incoming.receive()
            }

            val postJob = launch { client.post("/api/assets/reload") }
            var received: String? = null
            for (f in incoming) {
                if (f is Frame.Text) {
                    val text = f.readText()
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
