package org.micoli.micraft.auth

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers

class SessionPermissionsTest {
    private val groups = GroupsConfig(groups = listOf(GroupEntry("builder", listOf("claim:build"))))

    @Test
    fun noTokenStore_grantsFullAccess() {
        assertEquals(setOf(Permission.WILDCARD), resolveSessionPermissions(null, groups, listOf()))
        assertEquals(setOf(Permission.WILDCARD), resolveSessionPermissions(null, null, listOf()))
    }

    @Test
    fun tokenStore_resolvesTheCharacterGroups() {
        val store = TokenStore(CoroutineScope(Dispatchers.Default))
        assertEquals(
            setOf(Permission("claim:build")),
            resolveSessionPermissions(store, groups, listOf("builder")))
    }

    @Test
    fun tokenStoreWithoutGroupsConfig_grantsNothing() {
        val store = TokenStore(CoroutineScope(Dispatchers.Default))
        val permissions = resolveSessionPermissions(store, null, listOf("admin"))
        assertFalse(Permission.WILDCARD in permissions)
        assertEquals(emptySet(), permissions)
    }

    @Test
    fun grants_wildcardCoversEverythingSpecificOnlyItself() {
        assertEquals(true, setOf(Permission.WILDCARD).grants(Permission("x:y")))
        assertEquals(true, setOf(Permission("x:y")).grants(Permission("x:y")))
        assertEquals(false, setOf(Permission("x:y")).grants(Permission("x:z")))
    }
}
