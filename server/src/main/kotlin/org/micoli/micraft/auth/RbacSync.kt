package org.micoli.micraft.auth

import org.micoli.micraft.game.world.GameWorldRegistry

/**
 * Re-resolves and applies live permissions to every connected session after a group's permission
 * list changes (admin groups routes), so the effect is immediate instead of waiting for a
 * reconnect. In-game permissions are resolved from each session's own character
 * ([org.micoli.micraft.player.PlayerState.groups]) — independent of the account-level groups on
 * [UserEntry] that gate the admin panel.
 */
fun refreshLiveSessionPermissions(
    gameWorldRegistry: GameWorldRegistry,
    groupsConfig: GroupsConfig
) {
    gameWorldRegistry
        .all()
        .flatMap { it.sessions.all() }
        .forEach { session ->
            session.permissions = groupsConfig.resolvePermissions(session.state.groups)
        }
}
