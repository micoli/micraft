package org.micoli.micraft.http.character

import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.server.testing.testApplication
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.micoli.micraft.module

class CharacterApiTest {

    @Test
    fun testCreateCharacter() = testApplication {
        application { module() }
        val r =
            client.post("/api/character/create") {
                contentType(ContentType.Application.Json)
                setBody("""{"playerName":"TestHero","skin":"player"}""")
            }
        assertEquals(HttpStatusCode.OK, r.status)
        val json = Json.parseToJsonElement(r.bodyAsText()).jsonObject
        assertEquals("TestHero", json["playerName"]?.jsonPrimitive?.content)
    }

    @Test
    fun testCreateCharacterInvalidName() = testApplication {
        application { module() }
        val r =
            client.post("/api/character/create") {
                contentType(ContentType.Application.Json)
                setBody("""{"playerName":"ab","skin":"player"}""")
            }
        assertEquals(HttpStatusCode.BadRequest, r.status)
    }

    @Test
    fun testCreateRpgCharacter() = testApplication {
        application { module() }
        val r =
            client.post("/api/character/rpgcreate") {
                contentType(ContentType.Application.Json)
                setBody(
                    """{"playerName":"RpgHero","skin":"player","characterClass":"WARRIOR",
                |"str":14,"dex":8,"intel":8,"wis":8,"con":12,"cha":8}"""
                        .trimMargin())
            }
        assertEquals(HttpStatusCode.OK, r.status)
        val json = Json.parseToJsonElement(r.bodyAsText()).jsonObject
        assertEquals("RpgHero", json["playerName"]?.jsonPrimitive?.content)
        assertEquals("WARRIOR", json["characterClass"]?.jsonPrimitive?.content)
    }

    @Test
    fun testCreateRpgCharacterBudgetExceeded() = testApplication {
        application { module() }
        val r =
            client.post("/api/character/rpgcreate") {
                contentType(ContentType.Application.Json)
                setBody(
                    """{"playerName":"OverBudget","skin":"player","characterClass":"WARRIOR",
                |"str":15,"dex":15,"intel":15,"wis":15,"con":15,"cha":15}"""
                        .trimMargin())
            }
        assertEquals(HttpStatusCode.BadRequest, r.status)
    }

    @Test
    fun testCreateRpgCharacterDuplicateReturnsConflict() = testApplication {
        application { module() }
        val body =
            """{"playerName":"DupeHero","skin":"player","characterClass":"MAGE",
            |"str":8,"dex":8,"intel":14,"wis":12,"con":8,"cha":8}"""
                .trimMargin()
        val first =
            client.post("/api/character/rpgcreate") {
                contentType(ContentType.Application.Json)
                setBody(body)
            }
        assertEquals(HttpStatusCode.OK, first.status)
        val second =
            client.post("/api/character/rpgcreate") {
                contentType(ContentType.Application.Json)
                setBody(body)
            }
        assertEquals(HttpStatusCode.Conflict, second.status)
    }

    @Test
    fun testCreateRpgCharacterUnknownClass() = testApplication {
        application { module() }
        val r =
            client.post("/api/character/rpgcreate") {
                contentType(ContentType.Application.Json)
                setBody(
                    """{"playerName":"BadClass","skin":"player","characterClass":"DRUID",
                |"str":8,"dex":8,"intel":8,"wis":8,"con":8,"cha":8}"""
                        .trimMargin())
            }
        assertEquals(HttpStatusCode.BadRequest, r.status)
    }
}
