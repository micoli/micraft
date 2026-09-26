package org.micoli.micraft.http

import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.server.routing.routing
import io.ktor.server.testing.testApplication
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class PanelPagesControllerTest {

    private fun pageProvider(placeableId: String, page: String): String? =
        if (placeableId == "p1" && page == "index") "<p>hello</p>" else null

    @Test
    fun knownPage_returnsHtmlWithStrictCsp() = testApplication {
        application { routing { PanelPagesController(::pageProvider).register(this) } }
        val r = client.get("/panels/p1/index.html")
        assertEquals(HttpStatusCode.OK, r.status)
        assertEquals(ContentType.Text.Html, r.contentType()?.withoutParameters())
        assertEquals(PANEL_PAGE_CSP, r.headers["Content-Security-Policy"])
        assertEquals("nosniff", r.headers["X-Content-Type-Options"])
        assertTrue(r.bodyAsText().contains("<p>hello</p>"))
    }

    @Test
    fun unknownPlaceableOrPage_returns404() = testApplication {
        application { routing { PanelPagesController(::pageProvider).register(this) } }
        assertEquals(HttpStatusCode.NotFound, client.get("/panels/p1/missing.html").status)
        assertEquals(HttpStatusCode.NotFound, client.get("/panels/unknown/index.html").status)
    }

    @Test
    fun pageProvider_lookupStripsHtmlSuffix() {
        assertNull(pageProvider("p1", "index.html"))
        assertEquals("<p>hello</p>", pageProvider("p1", "index"))
    }
}
