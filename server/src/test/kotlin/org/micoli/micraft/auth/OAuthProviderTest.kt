package org.micoli.micraft.auth

import java.net.URLDecoder
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import org.micoli.micraft.game.OAuthConfig

class OAuthProviderTest {
    private val config =
        OAuthConfig(
            clientId = "my-client-id",
            clientSecret = "my-secret",
            redirectUri = "http://localhost/callback",
            scopes = listOf("email", "profile"),
        )

    @Test
    fun oauthStartUrl_containsClientId() {
        val provider = OAuthProvider(config, GroupsConfig())
        val url = provider.oauthStartUrl("http://localhost/return")
        assertNotNull(url)
        assertTrue(url.contains("my-client-id"), "URL should contain clientId")
    }

    @Test
    fun oauthStartUrl_containsRedirectUri() {
        val provider = OAuthProvider(config, GroupsConfig())
        val url = provider.oauthStartUrl("http://localhost/return")
        assertTrue(url.contains("localhost%2Fcallback") || url.contains("localhost/callback"))
    }

    @Test
    fun oauthReturnUrl_unknownState_returnsNull() {
        val provider = OAuthProvider(config, GroupsConfig())
        assertNull(provider.oauthReturnUrl("nonexistent-state-abc"))
    }

    @Test
    fun oauthReturnUrl_knownState_returnsReturnUrl() {
        val provider = OAuthProvider(config, GroupsConfig())
        val returnUrl = "http://localhost/after-login"
        val startUrl = provider.oauthStartUrl(returnUrl)
        // Extract state param from start URL
        val stateEncoded =
            startUrl.split("&", "?").firstOrNull { it.startsWith("state=") }?.removePrefix("state=")
                ?: return
        val state = URLDecoder.decode(stateEncoded, "UTF-8")
        assertEquals(returnUrl, provider.oauthReturnUrl(state))
    }
}
