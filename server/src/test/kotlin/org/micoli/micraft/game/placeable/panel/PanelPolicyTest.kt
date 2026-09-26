package org.micoli.micraft.game.placeable.panel

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class PanelPolicyTest {

    @Test
    fun bareHostnameEntry_allowsExactAndSubdomain() {
        val allowlist = listOf("google.com")
        assertTrue(PanelPolicy.isAllowedExternalUrl("https://google.com", allowlist))
        assertTrue(PanelPolicy.isAllowedExternalUrl("https://www.google.com", allowlist))
        assertFalse(PanelPolicy.isAllowedExternalUrl("https://evilgoogle.com", allowlist))
    }

    @Test
    fun fullUrlEntry_allowsSameHostAndSubdomain() {
        // Admins naturally paste a full URL into the config — accept it the same as a bare host.
        val allowlist = listOf("https://www.google.com", "https://google.com")
        assertTrue(PanelPolicy.isAllowedExternalUrl("https://www.google.com", allowlist))
        assertTrue(PanelPolicy.isAllowedExternalUrl("https://google.com", allowlist))
        assertTrue(PanelPolicy.isAllowedExternalUrl("https://maps.google.com", allowlist))
    }

    @Test
    fun httpScheme_isRejected() {
        assertFalse(PanelPolicy.isAllowedExternalUrl("http://google.com", listOf("google.com")))
    }

    @Test
    fun userInfoInUrl_isRejected() {
        assertFalse(
            PanelPolicy.isAllowedExternalUrl("https://user:pass@google.com", listOf("google.com")))
    }

    @Test
    fun emptyAllowlist_rejectsEverything() {
        assertFalse(PanelPolicy.isAllowedExternalUrl("https://google.com", emptyList()))
    }
}
