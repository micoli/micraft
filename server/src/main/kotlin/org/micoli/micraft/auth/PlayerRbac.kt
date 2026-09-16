package org.micoli.micraft.auth

import org.micoli.micraft.game.session.PlayerSession
import org.micoli.micraft.game.world.WorldPersistence

/** Result of a group mutation targeting a character (online session or offline save file). */
sealed interface PlayerRbacResult {
    data class Applied(val groups: List<String>) : PlayerRbacResult

    data object NotFound : PlayerRbacResult
}

/**
 * Adds/removes groups on a character's [org.micoli.micraft.player.PlayerState.groups] — in-game
 * RBAC, distinct from the account-level groups on [UserEntry] that gate the admin panel. The live
 * session (if connected) and its persisted save are updated together, since there is no periodic
 * autosave to rely on.
 */
fun mutatePlayerGroups(
    playerName: String,
    sessions: Collection<PlayerSession>,
    persistence: WorldPersistence?,
    groupsConfig: GroupsConfig,
    savePlayer: (PlayerSession) -> Unit,
    transform: (List<String>) -> List<String>,
): PlayerRbacResult {
    val session = sessions.find { it.state.name.equals(playerName, ignoreCase = true) }
    if (session != null) {
        val updated = transform(session.state.groups).distinct()
        session.state = session.state.copy(groups = updated)
        session.permissions = groupsConfig.resolvePermissions(updated)
        savePlayer(session)
        return PlayerRbacResult.Applied(updated)
    }
    val saved = persistence?.loadPlayerState(playerName) ?: return PlayerRbacResult.NotFound
    val updated = transform(saved.groups).distinct()
    persistence.savePlayerState(playerName, saved.copy(groups = updated))
    return PlayerRbacResult.Applied(updated)
}
