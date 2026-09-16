package org.micoli.micraft.http.chunks

import io.ktor.client.HttpClient
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.server.testing.*
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.micoli.micraft.module

class ChunkRoutesTest {

    // auth.provider is "local" with requirePassword: false in resources/config/server.yaml, so
    // an unknown email is enough to log in and get a bearer token — ChunkController requires one
    // whenever a TokenStore is wired in (always, in module()).
    private suspend fun HttpClient.chunkAuthToken(): String {
        val r =
            post("/auth/login") {
                contentType(ContentType.Application.Json)
                setBody("""{"email":"chunk-routes-test@example.com","password":""}""")
            }
        return Json.parseToJsonElement(r.bodyAsText()).jsonObject["token"]!!.jsonPrimitive.content
    }

    @Test
    fun testChunkEndpointReturnsOctetStream() = testApplication {
        application { module() }
        val token = client.chunkAuthToken()
        val r =
            client.get("/api/chunks/0/0") {
                headers.append(HttpHeaders.Authorization, "Bearer $token")
            }
        assertEquals(HttpStatusCode.OK, r.status)
        assertEquals(ContentType.Application.OctetStream, r.contentType()?.withoutParameters())
    }

    @Test
    fun testChunkEndpointReturnsBinaryBody() = testApplication {
        application { module() }
        val token = client.chunkAuthToken()
        val r =
            client.get("/api/chunks/0/0") {
                headers.append(HttpHeaders.Authorization, "Bearer $token")
            }
        val body = r.readRawBytes()
        assertTrue(body.isNotEmpty(), "chunk response must not be empty")
    }

    @Test
    fun testChunkEndpointInvalidCxReturnsBadRequest() = testApplication {
        application { module() }
        val token = client.chunkAuthToken()
        val r =
            client.get("/api/chunks/abc/0") {
                headers.append(HttpHeaders.Authorization, "Bearer $token")
            }
        assertEquals(HttpStatusCode.BadRequest, r.status)
    }

    @Test
    fun testChunkEndpointInvalidCzReturnsBadRequest() = testApplication {
        application { module() }
        val token = client.chunkAuthToken()
        val r =
            client.get("/api/chunks/0/xyz") {
                headers.append(HttpHeaders.Authorization, "Bearer $token")
            }
        assertEquals(HttpStatusCode.BadRequest, r.status)
    }
}
