package org.micoli.micraft.http.admin

import io.ktor.client.plugins.websocket.WebSockets as ClientWebSockets
import io.ktor.client.plugins.websocket.webSocket
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.request.put
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.server.application.install
import io.ktor.server.routing.routing
import io.ktor.server.testing.testApplication
import io.ktor.server.websocket.WebSockets
import io.ktor.websocket.Frame
import io.ktor.websocket.readText
import io.ktor.websocket.send
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.micoli.micraft.game.GameLoop
import org.micoli.micraft.game.world.BlockRegistry
import org.micoli.micraft.game.world.BlockType
import org.micoli.micraft.http.AdminController
import org.micoli.micraft.support.testWorld

class AdminEditWsTest {

    private fun gameLoop(vararg solid: Triple<Int, Int, Int>) = GameLoop(testWorld(*solid))

    private fun controller(gameLoop: GameLoop) =
        AdminController(null, null, null, gameLoop, "data", null)

    private suspend fun jsonId(body: String): String =
        Json.parseToJsonElement(body).jsonObject["id"]!!.jsonPrimitive.content

    @Test
    fun `scene block edit persists and broadcasts to other editors but not the sender`() =
        testApplication {
            val loop = gameLoop()
            application {
                install(WebSockets)
                routing {
                    controller(loop).register(this)
                    controller(loop).registerEditWs(this)
                }
            }
            val restClient = createClient {}
            val sceneId =
                jsonId(
                    restClient
                        .post("/api/admin/scenes") {
                            contentType(ContentType.Application.Json)
                            setBody("""{"name":"Test","width":4,"height":4,"depth":4}""")
                        }
                        .bodyAsText())

            val wsClient = createClient { install(ClientWebSockets) }
            withTimeout(5000) {
                wsClient.webSocket("/api/admin/ws/scenes/$sceneId") {
                    val editorA = this
                    wsClient.webSocket("/api/admin/ws/scenes/$sceneId") {
                        val editorB = this
                        editorA.send("""{"x":1,"y":2,"z":3,"type":"STONE","state":0}""")
                        val broadcast = editorB.incoming.receive()
                        assertTrue(broadcast is Frame.Text)
                        val received = broadcast.readText()
                        assertTrue(received.contains("\"x\":1"))
                        assertTrue(received.contains("STONE"))
                    }
                }
            }

            assertEquals(
                BlockRegistry.wireIndex(BlockType.STONE).toByte(),
                loop.scenes().get(sceneId)?.blockAt(1, 2, 3))
        }

    @Test
    fun `scene block edit with unknown block type returns error to sender only`() =
        testApplication {
            val loop = gameLoop()
            application {
                install(WebSockets)
                routing {
                    controller(loop).register(this)
                    controller(loop).registerEditWs(this)
                }
            }
            val restClient = createClient {}
            val sceneId =
                jsonId(
                    restClient
                        .post("/api/admin/scenes") {
                            contentType(ContentType.Application.Json)
                            setBody("""{"name":"Test","width":4,"height":4,"depth":4}""")
                        }
                        .bodyAsText())

            val wsClient = createClient { install(ClientWebSockets) }
            withTimeout(5000) {
                wsClient.webSocket("/api/admin/ws/scenes/$sceneId") {
                    send("""{"x":1,"y":1,"z":1,"type":"NOT_A_BLOCK","state":0}""")
                    val reply = incoming.receive()
                    assertTrue(reply is Frame.Text)
                    assertTrue(reply.readText().contains("\"type\":\"error\""))
                }
            }
        }

    @Test
    fun `instance block edit persists to the world and broadcasts to other editors`() =
        testApplication {
            val loop = gameLoop(Triple(0, 0, 0))
            application {
                install(WebSockets)
                routing {
                    controller(loop).register(this)
                    controller(loop).registerEditWs(this)
                }
            }
            val restClient = createClient {}
            val instanceId =
                jsonId(
                    restClient
                        .post("/api/admin/instances") {
                            contentType(ContentType.Application.Json)
                            setBody(
                                """{"name":"Test","yMin":0,"yMax":10,"chunks":[{"cx":0,"cz":0}]}""")
                        }
                        .bodyAsText())

            val wsClient = createClient { install(ClientWebSockets) }
            withTimeout(5000) {
                wsClient.webSocket("/api/admin/ws/instances/$instanceId") {
                    val editorA = this
                    wsClient.webSocket("/api/admin/ws/instances/$instanceId") {
                        val editorB = this
                        editorA.send("""{"x":2,"y":5,"z":2,"type":"STONE","state":0}""")
                        val broadcast = editorB.incoming.receive()
                        assertTrue(broadcast is Frame.Text)
                        assertTrue(broadcast.readText().contains("\"x\":2"))
                    }
                }
            }

            assertEquals(BlockType.STONE, loop.getWorldState().getBlock(2, 5, 2))
        }

    @Test
    fun `scene batch edit applies all blocks with a single broadcast`() = testApplication {
        val loop = gameLoop()
        application {
            install(WebSockets)
            routing {
                controller(loop).register(this)
                controller(loop).registerEditWs(this)
            }
        }
        val restClient = createClient {}
        val sceneId =
            jsonId(
                restClient
                    .post("/api/admin/scenes") {
                        contentType(ContentType.Application.Json)
                        setBody("""{"name":"Test","width":4,"height":4,"depth":4}""")
                    }
                    .bodyAsText())

        val wsClient = createClient { install(ClientWebSockets) }
        withTimeout(5000) {
            wsClient.webSocket("/api/admin/ws/scenes/$sceneId") {
                val editorA = this
                wsClient.webSocket("/api/admin/ws/scenes/$sceneId") {
                    val editorB = this
                    editorA.send(
                        """{"edits":[{"x":1,"y":1,"z":1,"type":"STONE","state":0},{"x":2,"y":1,"z":1,"type":"DIRT","state":0}]}""")
                    val broadcast = editorB.incoming.receive()
                    assertTrue(broadcast is Frame.Text)
                    val received = broadcast.readText()
                    assertTrue(received.contains("\"edits\""))
                    assertTrue(received.contains("STONE"))
                    assertTrue(received.contains("DIRT"))
                }
            }
        }

        assertEquals(
            BlockRegistry.wireIndex(BlockType.STONE).toByte(),
            loop.scenes().get(sceneId)?.blockAt(1, 1, 1))
        assertEquals(
            BlockRegistry.wireIndex(BlockType.DIRT).toByte(),
            loop.scenes().get(sceneId)?.blockAt(2, 1, 1))
    }

    @Test
    fun `scene batch edit skips invalid entries but applies the rest`() = testApplication {
        val loop = gameLoop()
        application {
            install(WebSockets)
            routing {
                controller(loop).register(this)
                controller(loop).registerEditWs(this)
            }
        }
        val restClient = createClient {}
        val sceneId =
            jsonId(
                restClient
                    .post("/api/admin/scenes") {
                        contentType(ContentType.Application.Json)
                        setBody("""{"name":"Test","width":4,"height":4,"depth":4}""")
                    }
                    .bodyAsText())

        val wsClient = createClient { install(ClientWebSockets) }
        withTimeout(5000) {
            wsClient.webSocket("/api/admin/ws/scenes/$sceneId") {
                val editorA = this
                // A second listener is only used to wait for the batch to actually have been
                // processed server-side before asserting — a batch with at least one applied edit
                // sends no ack to the sender itself, only a broadcast to OTHER listeners.
                wsClient.webSocket("/api/admin/ws/scenes/$sceneId") {
                    editorA.send(
                        """{"edits":[{"x":1,"y":1,"z":1,"type":"STONE","state":0},{"x":2,"y":1,"z":1,"type":"NOT_A_BLOCK","state":0}]}""")
                    incoming.receive()
                }
            }
        }

        assertEquals(
            BlockRegistry.wireIndex(BlockType.STONE).toByte(),
            loop.scenes().get(sceneId)?.blockAt(1, 1, 1))
    }

    @Test
    fun `scene batch edit with no valid entries returns a single error`() = testApplication {
        val loop = gameLoop()
        application {
            install(WebSockets)
            routing {
                controller(loop).register(this)
                controller(loop).registerEditWs(this)
            }
        }
        val restClient = createClient {}
        val sceneId =
            jsonId(
                restClient
                    .post("/api/admin/scenes") {
                        contentType(ContentType.Application.Json)
                        setBody("""{"name":"Test","width":4,"height":4,"depth":4}""")
                    }
                    .bodyAsText())

        val wsClient = createClient { install(ClientWebSockets) }
        withTimeout(5000) {
            wsClient.webSocket("/api/admin/ws/scenes/$sceneId") {
                send("""{"edits":[{"x":0,"y":0,"z":0,"type":"NOT_A_BLOCK","state":0}]}""")
                val reply = incoming.receive()
                assertTrue(reply is Frame.Text)
                assertTrue(reply.readText().contains("\"type\":\"error\""))
            }
        }
    }

    @Test
    fun `instance batch edit applies all blocks with a single broadcast`() = testApplication {
        val loop = gameLoop(Triple(0, 0, 0))
        application {
            install(WebSockets)
            routing {
                controller(loop).register(this)
                controller(loop).registerEditWs(this)
            }
        }
        val restClient = createClient {}
        val instanceId =
            jsonId(
                restClient
                    .post("/api/admin/instances") {
                        contentType(ContentType.Application.Json)
                        setBody("""{"name":"Test","yMin":0,"yMax":10,"chunks":[{"cx":0,"cz":0}]}""")
                    }
                    .bodyAsText())

        val wsClient = createClient { install(ClientWebSockets) }
        withTimeout(5000) {
            wsClient.webSocket("/api/admin/ws/instances/$instanceId") {
                val editorA = this
                wsClient.webSocket("/api/admin/ws/instances/$instanceId") {
                    val editorB = this
                    editorA.send(
                        """{"edits":[
                            |{"x":1,"y":5,"z":1,"type":"STONE","state":0},
                            |{"x":2,"y":5,"z":2,"type":"DIRT","state":0}
                            |]}"""
                            .trimMargin())
                    val broadcast = editorB.incoming.receive()
                    assertTrue(broadcast is Frame.Text)
                    val received = broadcast.readText()
                    assertTrue(received.contains("\"edits\""))
                }
            }
        }

        assertEquals(BlockType.STONE, loop.getWorldState().getBlock(1, 5, 1))
        assertEquals(BlockType.DIRT, loop.getWorldState().getBlock(2, 5, 2))
    }

    @Test
    fun `instance batch edit skips invalid entries but applies the rest`() = testApplication {
        val loop = gameLoop(Triple(0, 0, 0))
        application {
            install(WebSockets)
            routing {
                controller(loop).register(this)
                controller(loop).registerEditWs(this)
            }
        }
        val restClient = createClient {}
        val instanceId =
            jsonId(
                restClient
                    .post("/api/admin/instances") {
                        contentType(ContentType.Application.Json)
                        setBody("""{"name":"Test","yMin":0,"yMax":10,"chunks":[{"cx":0,"cz":0}]}""")
                    }
                    .bodyAsText())

        val wsClient = createClient { install(ClientWebSockets) }
        withTimeout(5000) {
            wsClient.webSocket("/api/admin/ws/instances/$instanceId") {
                val editorA = this
                // See the scene equivalent test above for why a second listener is needed here —
                // just to wait for server-side processing before asserting.
                wsClient.webSocket("/api/admin/ws/instances/$instanceId") {
                    editorA.send(
                        """{"edits":[{"x":1,"y":5,"z":1,"type":"STONE","state":0},{"x":999,"y":5,"z":999,"type":"DIRT","state":0}]}""")
                    incoming.receive()
                }
            }
        }

        assertEquals(BlockType.STONE, loop.getWorldState().getBlock(1, 5, 1))
    }

    @Test
    fun `legacy REST block PUT routes no longer exist`() = testApplication {
        val loop = gameLoop()
        application { routing { controller(loop).register(this) } }
        val restClient = createClient {}
        val sceneId =
            jsonId(
                restClient
                    .post("/api/admin/scenes") {
                        contentType(ContentType.Application.Json)
                        setBody("""{"name":"Test","width":4,"height":4,"depth":4}""")
                    }
                    .bodyAsText())
        val r =
            restClient.put("/api/admin/scenes/$sceneId/blocks") {
                contentType(ContentType.Application.Json)
                setBody("""{"x":0,"y":0,"z":0,"type":"STONE","state":0}""")
            }
        assertEquals(HttpStatusCode.NotFound, r.status)
    }
}
