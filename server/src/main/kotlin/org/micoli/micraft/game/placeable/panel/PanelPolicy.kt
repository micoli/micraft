package org.micoli.micraft.game.placeable.panel

import java.net.URI

/** Live panel settings pushed by `applyServerConfig` (reloadable through `/reload`). */
object PanelPolicy {
    @Volatile var externalAllowlist: List<String> = emptyList()

    /** True for an `https` URL whose host is exactly (or a subdomain of) an allowlisted host. */
    fun isAllowedExternalUrl(url: String, allowlist: List<String> = externalAllowlist): Boolean {
        if (url.length > org.micoli.micraft.placeable.panel.PanelConstants.MAX_URL_LENGTH) {
            return false
        }
        val uri = runCatching { URI(url) }.getOrNull() ?: return false
        if (uri.scheme != "https" || uri.userInfo != null) return false
        val host = uri.host?.lowercase() ?: return false
        return allowlist.any { host == hostOf(it) || host.endsWith(".${hostOf(it)}") }
    }

    /** Accepts either a bare hostname (`google.com`) or a full URL (`https://google.com`). */
    private fun hostOf(entry: String): String {
        val trimmed = entry.trim().lowercase()
        if ("://" !in trimmed) return trimmed
        return runCatching { URI(trimmed).host?.lowercase() }.getOrNull() ?: trimmed
    }
}
