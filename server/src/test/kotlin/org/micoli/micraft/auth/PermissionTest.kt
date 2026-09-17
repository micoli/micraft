package org.micoli.micraft.auth

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class PermissionTest {
    @Test
    fun of_buildsNamespacedId() {
        assertEquals("actionblock:edit", Permission.of("actionblock", "edit").id)
    }

    @Test
    fun toString_returnsId() {
        assertEquals("admin", CorePermissions.ADMIN.toString())
    }

    @Test
    fun registry_containsPermissionsRegisteredAtDeclaration() {
        // Accessing any member forces the object's init block — self-registers via
        // PermissionRegistry.register.
        assertTrue(ActionPermissions.FLY in PermissionRegistry.all)
        assertTrue(ActionPermissions.BREAK in PermissionRegistry.all)
        assertTrue(ActionPermissions.PLACE in PermissionRegistry.all)
    }

    @Test
    fun authResult_hasPermission_wildcardGrantsAll() {
        val result =
            AuthResult(
                playerId = "p1", displayName = "Admin", permissions = setOf(Permission.WILDCARD))
        assertTrue(result.hasPermission(CorePermissions.ADMIN))
        assertTrue(result.hasPermission(Permission("anything")))
    }

    @Test
    fun authResult_hasPermission_specificPermOnly() {
        val result =
            AuthResult(
                playerId = "p1", displayName = "Bob", permissions = setOf(CorePermissions.PLAYER))
        assertTrue(result.hasPermission(CorePermissions.PLAYER))
        assertTrue(!result.hasPermission(CorePermissions.ADMIN))
    }
}
