package org.micoli.micraft.auth

import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.server.testing.*
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

class AuthRoutesTest {

    private val scope = CoroutineScope(Dispatchers.Default)

    @Test
    fun `auth config returns provider name`() = testApplication {
        application { installAuthRoutes("none", null, null) }
        val r = client.get("/api/auth/config")
        assertEquals(HttpStatusCode.OK, r.status)
        val body = r.bodyAsText()
        assertTrue(body.contains("\"none\""))
    }

    @Test
    fun `login success returns token`() = testApplication {
        val tmp = Files.createTempFile("micraft-users", ".yaml")
        tmp.toFile().writeText("users: []\n")
        val provider = LocalAuthProvider(tmp, GroupsConfig())
        provider.addUser("alice@test.com", "pass1234", "Alice")
        val store = TokenStore(scope)

        application { installAuthRoutes("local", provider, store) }

        val r =
            client.post("/auth/login") {
                contentType(ContentType.Application.Json)
                setBody("""{"email":"alice@test.com","password":"pass1234"}""")
            }
        assertEquals(HttpStatusCode.OK, r.status)
        val json = Json.parseToJsonElement(r.bodyAsText()).jsonObject
        val token = json["token"]?.jsonPrimitive?.content
        assertTrue(!token.isNullOrEmpty())
        assertEquals("Alice", json["displayName"]?.jsonPrimitive?.content)

        tmp.toFile().delete()
    }

    @Test
    fun `login wrong password returns 401`() = testApplication {
        val tmp = Files.createTempFile("micraft-users", ".yaml")
        tmp.toFile().writeText("users: []\n")
        val provider = LocalAuthProvider(tmp, GroupsConfig())
        provider.addUser("bob@test.com", "correct", "Bob")
        val store = TokenStore(scope)

        application { installAuthRoutes("local", provider, store) }

        val r =
            client.post("/auth/login") {
                contentType(ContentType.Application.Json)
                setBody("""{"email":"bob@test.com","password":"wrong"}""")
            }
        assertEquals(HttpStatusCode.Unauthorized, r.status)

        tmp.toFile().delete()
    }

    @Test
    fun `auth me with valid token returns user`() = testApplication {
        val store = TokenStore(scope)
        val token = store.issue(AuthResult("carol@test.com", "Carol"))

        application { installAuthRoutes("local", null, store) }

        val r = client.get("/auth/me") { headers.append("Authorization", "Bearer $token") }
        assertEquals(HttpStatusCode.OK, r.status)
        val json = Json.parseToJsonElement(r.bodyAsText()).jsonObject
        assertEquals("carol@test.com", json["playerId"]?.jsonPrimitive?.content)
    }

    @Test
    fun `auth me with invalid token returns 401`() = testApplication {
        val store = TokenStore(scope)
        application { installAuthRoutes("local", null, store) }

        val r = client.get("/auth/me") { headers.append("Authorization", "Bearer invalid-token") }
        assertEquals(HttpStatusCode.Unauthorized, r.status)
    }
}
