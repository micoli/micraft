package org.micoli.micraft.auth

import java.nio.file.Path
import kotlin.system.exitProcess

fun main(args: Array<String>) {
    if (args.size < 2) {
        System.err.println("Usage: adduser <email> <password> [displayName] [group1,group2,...]")
        System.err.println("  email       - login email address")
        System.err.println("  password    - plaintext password (hashed with bcrypt)")
        System.err.println("  displayName - optional in-game display name (defaults to email)")
        System.err.println("  groups      - optional comma-separated group list")
        exitProcess(1)
    }
    val email = args[0]
    val password = args[1]
    val displayName = if (args.size >= 3) args[2] else email
    val groups =
        if (args.size >= 4) args[3].split(",").map { it.trim() }.filter { it.isNotEmpty() }
        else emptyList()

    val usersFile = Path.of("data/config/auth/users.yaml")
    val groupsConfig = loadGroupsConfig(Path.of("data/config/auth/groups.yaml"))
    val provider = LocalAuthProvider(usersFile, groupsConfig)
    runCatching { provider.addUser(email, password, displayName, groups) }
        .onSuccess { println("User added: $email (displayName=$displayName, groups=$groups)") }
        .onFailure { e ->
            System.err.println("Error: ${e.message}")
            exitProcess(2)
        }
}
