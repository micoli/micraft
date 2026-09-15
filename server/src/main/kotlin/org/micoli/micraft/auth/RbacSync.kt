package org.micoli.micraft.auth

import org.micoli.micraft.game.world.GameWorldRegistry

/**
 * Re-resolves and applies live permissions to every connected session after a groups/permissions
 * change, so the effect is immediate instead of waiting for a reconnect (session.permissions was
 * only ever set once, at connect time, from the login-time [AuthResult]).
 *
 * Only one [AuthProvider] is active at a time (`auth.provider` in server.yaml):
 * - [LocalAuthProvider]: each session's permissions come from that user's own persisted groups.
 * - [OAuthProvider]: there is no per-user group storage — every session gets `defaultGroups`, so
 *   editing a default group's permissions applies live to every connected OAuth session at once.
 * - Anything else (no-auth): sessions already hold `"*"`, nothing to refresh.
 */
fun refreshLiveSessionPermissions(
    gameWorldRegistry: GameWorldRegistry,
    authProvider: AuthProvider?,
    groupsConfig: GroupsConfig,
) {
    val sessions = gameWorldRegistry.all().flatMap { it.sessions.all() }
    when (authProvider) {
        is LocalAuthProvider ->
            sessions.forEach { session ->
                val groups = authProvider.getUserGroups(session.userName) ?: return@forEach
                session.permissions = groupsConfig.resolvePermissions(groups)
            }
        is OAuthProvider -> {
            val defaultPermissions = groupsConfig.resolveDefaultPermissions()
            sessions.forEach { session -> session.permissions = defaultPermissions }
        }
        else -> {}
    }
}
