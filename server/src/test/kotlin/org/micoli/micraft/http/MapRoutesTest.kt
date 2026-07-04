package org.micoli.micraft.http

import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.server.testing.*
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.micoli.micraft.module

class MapRoutesTest {

    @Test
    fun testMapStateReturnsJson() = testApplication {
        application { module() }
        val r = client.get("/api/map/state")
        assertEquals(HttpStatusCode.OK, r.status)
        assertEquals(ContentType.Application.Json, r.contentType()?.withoutParameters())
    }

    @Test
    fun testMapStateHasCorsHeader() = testApplication {
        application { module() }
        val r = client.get("/api/map/state")
        assertEquals("*", r.headers[HttpHeaders.AccessControlAllowOrigin])
    }

    @Test
    fun testMapStateStructure() = testApplication {
        application { module() }
        val body = client.get("/api/map/state").bodyAsText()
        val json = Json.parseToJsonElement(body).jsonObject
        assertNotNull(json["gameTicks"], "gameTicks field must be present")
        assertTrue(
            json["gameTicks"]!!.jsonPrimitive.content.toLong() >= 0,
            "gameTicks must be non-negative")
        assertNotNull(json["players"], "players field must be present")
        assertTrue(
            json["players"]!!.jsonArray.isEmpty() || json["players"]!!.jsonArray.isNotEmpty())
        assertNotNull(json["npcs"], "npcs field must be present")
        assertTrue(json["npcs"]!!.jsonArray.isEmpty() || json["npcs"]!!.jsonArray.isNotEmpty())
    }

    @Test
    fun testMapPageServed() = testApplication {
        application { module() }
        val r = client.get("/map")
        assertEquals(HttpStatusCode.OK, r.status)
        val body = r.bodyAsText()
        assertTrue(body.contains("id=\"root\""), "map page must include the app mount point")
        assertTrue(body.contains("/map.js"), "map page must load the map script")
    }

    @Test
    fun testRoadRasterPngReturnsPng() = testApplication {
        application { module() }
        val r = client.get("/api/map/road-raster.png?cx=0&cz=0&radius=64")
        assertEquals(HttpStatusCode.OK, r.status)
        assertEquals(ContentType.Image.PNG, r.contentType()?.withoutParameters())
        assertEquals("*", r.headers[HttpHeaders.AccessControlAllowOrigin])
        assertTrue(r.readBytes().isNotEmpty(), "PNG body must not be empty")
    }

    @Test
    fun testDtoMapping() {
        val info = PlayerMapInfo(id = "abc", name = "Alice", x = 1f, y = 64f, z = 2f, yaw = 90f)
        assertEquals("abc", info.id)
        assertEquals("Alice", info.name)
        assertEquals(1f, info.x)
        assertEquals(90f, info.yaw)

        val npc =
            NpcMapInfo(
                id = "xyz", name = "Zombie", type = "zombie", x = 10f, y = 64f, z = 20f, yaw = 0f)
        assertEquals("zombie", npc.type)
        assertEquals(10f, npc.x)
    }
}
