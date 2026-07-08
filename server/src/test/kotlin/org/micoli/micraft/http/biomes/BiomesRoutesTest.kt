package org.micoli.micraft.http.biomes

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

class BiomesRoutesTest {

    @Test
    fun testBiomesReturnsColorMap() = testApplication {
        application { module() }
        val r = client.get("/api/biomes")
        assertEquals(HttpStatusCode.OK, r.status)
        assertEquals(ContentType.Application.Json, r.contentType()?.withoutParameters())
        assertTrue(Json.parseToJsonElement(r.bodyAsText()).jsonObject.isNotEmpty())
    }
}
