package org.micoli.micraft.http.chunks

import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.server.testing.*
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import org.micoli.micraft.module

class ChunkRoutesTest {

    @Test
    fun testChunkEndpointReturnsOctetStream() = testApplication {
        application { module() }
        val r = client.get("/api/chunks/0/0")
        assertEquals(HttpStatusCode.OK, r.status)
        assertEquals(ContentType.Application.OctetStream, r.contentType()?.withoutParameters())
    }

    @Test
    fun testChunkEndpointReturnsBinaryBody() = testApplication {
        application { module() }
        val r = client.get("/api/chunks/0/0")
        val body = r.readRawBytes()
        assertTrue(body.isNotEmpty(), "chunk response must not be empty")
    }

    @Test
    fun testChunkEndpointInvalidCxReturnsBadRequest() = testApplication {
        application { module() }
        val r = client.get("/api/chunks/abc/0")
        assertEquals(HttpStatusCode.BadRequest, r.status)
    }

    @Test
    fun testChunkEndpointInvalidCzReturnsBadRequest() = testApplication {
        application { module() }
        val r = client.get("/api/chunks/0/xyz")
        assertEquals(HttpStatusCode.BadRequest, r.status)
    }
}
