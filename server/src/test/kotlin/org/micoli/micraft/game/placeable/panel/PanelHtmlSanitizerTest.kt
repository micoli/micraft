package org.micoli.micraft.game.placeable.panel

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class PanelHtmlSanitizerTest {

    @Test
    fun stripsScriptTags() {
        val html = PanelHtmlSanitizer.sanitize("<p>hi</p><script>alert(1)</script>")
        assertFalse(html.contains("script", ignoreCase = true))
        assertTrue(html.contains("hi"))
    }

    @Test
    fun stripsEventHandlerAttributes() {
        val html =
            PanelHtmlSanitizer.sanitize("<img src=\"data:image/png;base64,AA\" onerror=\"evil()\">")
        assertFalse(html.contains("onerror"))
    }

    @Test
    fun stripsJavascriptUrls() {
        val html = PanelHtmlSanitizer.sanitize("<a href=\"javascript:alert(1)\">go</a>")
        assertFalse(html.contains("javascript:"))
    }

    @Test
    fun keepsRelativePageLinks() {
        val html = PanelHtmlSanitizer.sanitize("<a href=\"other.html\">go</a>")
        assertTrue(html.contains("href=\"other.html\""))
    }

    @Test
    fun stripsAbsoluteAndExternalLinks() {
        val html = PanelHtmlSanitizer.sanitize("<a href=\"https://evil.test\">go</a>")
        assertFalse(html.contains("href="))
    }

    @Test
    fun stripsRemoteImages_keepsDataUris() {
        val html =
            PanelHtmlSanitizer.sanitize(
                "<img src=\"https://evil.test/track.png\">" +
                    "<img src=\"data:image/png;base64,AA==\">")
        assertFalse(html.contains("evil.test"))
        assertTrue(html.contains("data:image/png"))
    }
}
