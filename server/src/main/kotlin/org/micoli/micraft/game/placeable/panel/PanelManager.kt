package org.micoli.micraft.game.placeable.panel

import com.charleskorn.kaml.Yaml
import java.nio.file.Path
import java.util.concurrent.ConcurrentHashMap
import kotlin.io.path.createDirectories
import kotlin.io.path.exists
import kotlin.io.path.readText
import kotlin.io.path.writeText
import kotlinx.serialization.builtins.ListSerializer
import org.micoli.micraft.game.session.PlayerSession
import org.micoli.micraft.placeable.panel.PanelConstants
import org.micoli.micraft.placeable.panel.PanelEditData
import org.micoli.micraft.placeable.panel.PanelInfo
import org.micoli.micraft.protocol.ServerMessage
import org.slf4j.LoggerFactory

private val log = LoggerFactory.getLogger(PanelManager::class.java)

enum class PanelSaveResult {
    OK,
    UNKNOWN_PANEL,
    INVALID_URL,
    TOO_MANY_PAGES,
    INVALID_PAGE_NAME,
    PAGE_TOO_LARGE,
}

/**
 * Per-instance content of panel placeables (local pages or an allowlisted external URL), layered on
 * [org.micoli.micraft.game.placeable.PlaceableManager] by placeable id — same composition as
 * [org.micoli.micraft.game.placeable.siege.SiegeWeaponManager]. Only sanitized HTML is ever stored.
 */
class PanelManager(
    private val broadcast: suspend (ServerMessage) -> Unit,
    private val allowlist: () -> List<String> = { PanelPolicy.externalAllowlist },
) {
    private val panels = ConcurrentHashMap<String, PanelContent>()

    fun get(placeableId: String): PanelContent? = panels[placeableId]

    fun isPanel(placeableId: String): Boolean = panels.containsKey(placeableId)

    fun infoOf(content: PanelContent): PanelInfo =
        if (content.externalUrl.isNotBlank())
            PanelInfo(content.placeableId, content.externalUrl, true)
        else PanelInfo(content.placeableId, PanelConstants.pageUrl(content.placeableId), false)

    /** Registers an empty panel for [placeableId]; no-op broadcast-wise until first save. */
    suspend fun createFor(placeableId: String, owner: String) {
        val content = PanelContent(placeableId, owner)
        panels[placeableId] = content
        broadcast(ServerMessage.PanelChanged(infoOf(content)))
    }

    suspend fun removeFor(placeableId: String) {
        panels.remove(placeableId) ?: return
        broadcast(ServerMessage.PanelRemoved(placeableId))
    }

    fun ownerOf(placeableId: String): String? = panels[placeableId]?.owner

    fun editData(placeableId: String): PanelEditData? {
        val content = panels[placeableId] ?: return null
        return PanelEditData(placeableId, content.externalUrl, content.pages)
    }

    /** Local page HTML or null; a panel without any page serves a placeholder for [INDEX]. */
    fun pageHtml(placeableId: String, page: String): String? {
        val content = panels[placeableId] ?: return null
        content.pages[page]?.let {
            return it
        }
        return if (page == PanelConstants.INDEX_PAGE) EMPTY_INDEX else null
    }

    suspend fun save(
        placeableId: String,
        externalUrl: String,
        pages: Map<String, String>,
    ): PanelSaveResult {
        val current = panels[placeableId] ?: return PanelSaveResult.UNKNOWN_PANEL
        val url = externalUrl.trim()
        if (url.isNotEmpty() && !PanelPolicy.isAllowedExternalUrl(url, allowlist())) {
            return PanelSaveResult.INVALID_URL
        }
        if (pages.size > PanelConstants.MAX_PAGES) return PanelSaveResult.TOO_MANY_PAGES
        if (pages.keys.any { !PAGE_NAME.matches(it) }) return PanelSaveResult.INVALID_PAGE_NAME
        if (pages.values.any { it.length > PanelConstants.MAX_PAGE_HTML_LENGTH }) {
            return PanelSaveResult.PAGE_TOO_LARGE
        }
        val updated =
            current.copy(
                externalUrl = url,
                pages = pages.mapValues { (_, html) -> PanelHtmlSanitizer.sanitize(html) })
        panels[placeableId] = updated
        broadcast(ServerMessage.PanelChanged(infoOf(updated)))
        return PanelSaveResult.OK
    }

    suspend fun sendAllTo(session: PlayerSession) {
        session.send(ServerMessage.PanelSync(panels.values.map(::infoOf)))
    }

    /** Restores contents from [savePath], dropping any whose placeable no longer exists. */
    fun load(savePath: Path, isPlaceablePresent: (String) -> Boolean) {
        if (!savePath.exists()) return
        runCatching {
                Yaml.default
                    .decodeFromString(
                        ListSerializer(PanelContent.serializer()), savePath.readText())
                    .filter { isPlaceablePresent(it.placeableId) }
                    .forEach { panels[it.placeableId] = it }
                log.info("Loaded {} panels from {}", panels.size, savePath)
            }
            .onFailure { log.warn("Failed to load panels from {}: {}", savePath, it.message) }
    }

    fun save(savePath: Path) {
        runCatching {
                savePath.parent?.createDirectories()
                savePath.writeText(
                    Yaml.default.encodeToString(
                        ListSerializer(PanelContent.serializer()), panels.values.toList()))
            }
            .onFailure { log.warn("Failed to save panels: {}", it.message) }
    }

    private companion object {
        val PAGE_NAME = Regex("^[A-Za-z0-9_-]{1,${PanelConstants.MAX_PAGE_NAME_LENGTH}}$")
        const val EMPTY_INDEX = "<p>Empty panel.</p>"
    }
}
