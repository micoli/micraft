package org.micoli.micraft.http.skins

import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.server.testing.testApplication
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import org.micoli.micraft.module

class SkinsRoutesTest {

    @Test
    fun testSkinsReturnsJsonArray() = testApplication {
        application { module() }
        val r = client.get("/api/skins")
        assertEquals(HttpStatusCode.OK, r.status)
        assertEquals(ContentType.Application.Json, r.contentType()?.withoutParameters())
        assertTrue(r.bodyAsText().trim().startsWith("["), "skins payload must be a JSON array")
    }
}
