package org.micoli.micraft.http

import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.server.testing.testApplication
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import org.micoli.micraft.module

class VersionRoutesTest {

    @Test
    fun testVersionReturnsServerId() = testApplication {
        application { module() }
        val r = client.get("/api/version")
        assertEquals(HttpStatusCode.OK, r.status)
        assertEquals(ContentType.Application.Json, r.contentType()?.withoutParameters())
        val json = Json.parseToJsonElement(r.bodyAsText()).jsonObject
        assertTrue(json.containsKey("server"), "version payload must expose server id")
    }
}
