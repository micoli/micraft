package org.micoli.micraft.http

import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.server.routing.routing
import io.ktor.server.testing.testApplication
import java.io.File
import java.nio.file.Files
import java.util.Base64
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ScreenshotControllerTest {
    private lateinit var tmpDir: File

    @BeforeTest
    fun setUp() {
        tmpDir = Files.createTempDirectory("screenshot-test").toFile()
    }

    @AfterTest
    fun tearDown() {
        tmpDir.deleteRecursively()
    }

    private fun fakePng(): String {
        // 1x1 transparent PNG, base64-encoded
        val pngBytes =
            Base64.getDecoder()
                .decode(
                    "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR42mNk+M9QDwADhgGAWjR9awAAAABJRU5ErkJggg==")
        return "data:image/png;base64," + Base64.getEncoder().encodeToString(pngBytes)
    }

    private val testPlayerId = "550e8400-e29b-41d4-a716-446655440000"

    @Test
    fun saveScreenshot_createsFile() = testApplication {
        application { routing { ScreenshotController(tmpDir.absolutePath).register(this) } }
        val r =
            client.post("/api/player/$testPlayerId/screenshots") {
                contentType(ContentType.Application.Json)
                setBody("""{"imageData":"${fakePng()}"}""")
            }
        assertEquals(HttpStatusCode.Created, r.status)
        val body = r.bodyAsText()
        assertTrue(body.contains("filename"))
        val dir = File(tmpDir, "screenshots/$testPlayerId")
        assertTrue(dir.exists())
        assertTrue(dir.listFiles()?.any { it.name.endsWith(".png") } == true)
    }

    @Test
    fun missingPlayerId_returnsBadRequest() = testApplication {
        application { routing { ScreenshotController(tmpDir.absolutePath).register(this) } }
        val r =
            client.post("/api/player//screenshots") {
                contentType(ContentType.Application.Json)
                setBody("""{"imageData":"${fakePng()}"}""")
            }
        assertEquals(HttpStatusCode.NotFound, r.status)
    }

    @Test
    fun missingImageData_returnsBadRequest() = testApplication {
        application { routing { ScreenshotController(tmpDir.absolutePath).register(this) } }
        val r =
            client.post("/api/player/$testPlayerId/screenshots") {
                contentType(ContentType.Application.Json)
                setBody("""{}""")
            }
        assertEquals(HttpStatusCode.BadRequest, r.status)
    }

    @Test
    fun invalidBase64_returnsBadRequest() = testApplication {
        application { routing { ScreenshotController(tmpDir.absolutePath).register(this) } }
        val r =
            client.post("/api/player/$testPlayerId/screenshots") {
                contentType(ContentType.Application.Json)
                setBody("""{"imageData":"not-valid-base64!!!"}""")
            }
        assertEquals(HttpStatusCode.BadRequest, r.status)
    }
}
