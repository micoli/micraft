package org.micoli.micraft.auth

/**
 * In-game permissions follow the Character's groups, never the Account. Without a token store (dev
 * / no-auth mode) there is no backend to gate on, so the session gets full access; with one, a
 * Character without groups config or groups holds nothing.
 */
fun resolveSessionPermissions(
    tokenStore: TokenStore?,
    groupsConfig: GroupsConfig?,
    characterGroups: List<String>,
): Set<Permission> {
    if (tokenStore == null) return setOf(Permission.WILDCARD)
    return groupsConfig?.resolvePermissions(characterGroups) ?: emptySet()
}
