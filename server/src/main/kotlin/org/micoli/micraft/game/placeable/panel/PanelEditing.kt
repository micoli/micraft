package org.micoli.micraft.game.placeable.panel

import kotlin.math.floor
import org.micoli.micraft.I18nConfig
import org.micoli.micraft.game.placeable.PlaceableManager
import org.micoli.micraft.game.session.PlayerSession
import org.micoli.micraft.game.world.BlockPos
import org.micoli.micraft.game.world.claim.Authorizer
import org.micoli.micraft.game.world.claim.ClaimRegistry
import org.micoli.micraft.game.world.claim.EditTarget
import org.micoli.micraft.protocol.ServerMessage

/**
 * Permission-checked panel edit flows shared by the protocol handlers and the `/panel` command.
 * Every failure is reported to [session] as an i18n notification (`panel:server:*`).
 */
class PanelEditing(
    private val placeables: PlaceableManager,
    claims: ClaimRegistry?,
    private val i18n: I18nConfig,
) {
    private val authorizer = Authorizer(claims)

    private suspend fun notify(session: PlayerSession, key: String, vararg args: Any) {
        session.send(ServerMessage.Notification(i18n.t(session.state.language, key, *args)))
    }

    /**
     * False (after notifying [session]) when [placeableId] isn't a panel or [session] can't edit
     * it.
     */
    private suspend fun authorize(session: PlayerSession, placeableId: String): Boolean {
        val instance = placeables.get(placeableId)
        if (instance == null || !placeables.panels.isPanel(placeableId)) {
            notify(session, "panel:server:not_a_panel")
            return false
        }
        val owner = placeables.panels.ownerOf(placeableId) ?: return false
        val pos =
            BlockPos(
                floor(instance.pos.x).toInt(),
                floor(instance.pos.y).toInt(),
                floor(instance.pos.z).toInt())
        if (!authorizer.isAllowed(
            session, PanelPermissions.EDIT, EditTarget.OwnedInWorld(owner, pos))) {
            notify(session, "panel:server:no_permission")
            return false
        }
        return true
    }

    suspend fun requestEditor(session: PlayerSession, placeableId: String) {
        if (!authorize(session, placeableId)) return
        val data = placeables.panels.editData(placeableId) ?: return
        session.send(ServerMessage.PanelEditOpen(data))
    }

    suspend fun save(
        session: PlayerSession,
        placeableId: String,
        externalUrl: String,
        pages: Map<String, String>,
    ) {
        if (!authorize(session, placeableId)) return
        when (placeables.panels.save(placeableId, externalUrl, pages)) {
            PanelSaveResult.OK -> notify(session, "panel:server:saved")
            PanelSaveResult.UNKNOWN_PANEL -> notify(session, "panel:server:not_a_panel")
            PanelSaveResult.INVALID_URL -> notify(session, "panel:server:invalid_url")
            PanelSaveResult.TOO_MANY_PAGES -> notify(session, "panel:server:too_many_pages")
            PanelSaveResult.INVALID_PAGE_NAME -> notify(session, "panel:server:invalid_page_name")
            PanelSaveResult.PAGE_TOO_LARGE -> notify(session, "panel:server:page_too_large")
        }
    }
}
