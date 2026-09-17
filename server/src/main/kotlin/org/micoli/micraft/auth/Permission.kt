package org.micoli.micraft.auth

/** A permission's stable wire form is its [id] ("namespace:action"), used in groups.yaml / JSON. */
@JvmInline
value class Permission(val id: String) {
    override fun toString() = id

    companion object {
        val WILDCARD = Permission("*")

        fun of(namespace: String, action: String) = Permission("$namespace:$action")
    }
}

/**
 * Registry for permissions checked outside the command system (`PluginCommand.permission` values
 * are discovered dynamically from the live command registry instead — see
 * [org.micoli.micraft.game.GameLoop.knownCommandPermissions]). Each standalone-permission namespace
 * object registers its values here at declaration time instead of being copied into a hand-kept
 * list, so there is nothing to keep in sync with `hasPermission(...)` call sites.
 */
object PermissionRegistry {
    private val _all = mutableSetOf<Permission>()
    val all: Set<Permission>
        get() = _all

    fun register(permission: Permission): Permission {
        _all += permission
        return permission
    }
}

object CorePermissions {
    val ADMIN = Permission("admin")
    val PLAYER = Permission("player")
}

fun AuthResult.hasPermission(perm: Permission): Boolean =
    Permission.WILDCARD in permissions || perm in permissions
