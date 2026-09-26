package org.micoli.micraft.game.placeable.panel

import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.jsoup.safety.Safelist

/**
 * Write-time sanitizer for panel pages: keeps structural/text markup and inline `style`, keeps only
 * relative `page.html` links (navigation between the panel's own pages) and `data:` images, strips
 * everything else (scripts, event handlers, external resources, `javascript:` URLs).
 */
object PanelHtmlSanitizer {
    private val safelist: Safelist =
        Safelist.relaxed()
            .addAttributes(":all", "style", "class")
            .preserveRelativeLinks(true)
            .addProtocols("img", "src", "data")
            .removeProtocols("a", "href", "ftp", "http", "https", "mailto")

    fun sanitize(html: String): String {
        val cleaned = Jsoup.clean(html, "", safelist, Document.OutputSettings().prettyPrint(false))
        val doc = Jsoup.parseBodyFragment(cleaned)
        doc.select("a[href]").forEach { a ->
            if (!isRelativePage(a.attr("href"))) a.removeAttr("href")
        }
        doc.select("img[src]").forEach { img ->
            if (!img.attr("src").startsWith("data:image/")) img.remove()
        }
        return doc.body().html()
    }

    private fun isRelativePage(href: String): Boolean = PAGE_LINK.matches(href)

    private val PAGE_LINK = Regex("^[A-Za-z0-9_-]{1,48}\\.html$")
}
