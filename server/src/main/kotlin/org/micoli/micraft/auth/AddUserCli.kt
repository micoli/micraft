package org.micoli.micraft.auth

import java.nio.file.Path
import kotlin.system.exitProcess

fun main(args: Array<String>) {
    if (args.size < 2) {
        System.err.println("Usage: adduser <email> <password> [displayName]")
        System.err.println("  email       - login email address")
        System.err.println("  password    - plaintext password (hashed with bcrypt)")
        System.err.println("  displayName - optional in-game display name (defaults to email)")
        exitProcess(1)
    }
    val email = args[0]
    val password = args[1]
    val displayName = if (args.size >= 3) args[2] else email

    val usersFile = Path.of("data/auth/users.yaml")
    val provider = LocalAuthProvider(usersFile)
    runCatching { provider.addUser(email, password, displayName) }
        .onSuccess { println("User added: $email (displayName=$displayName)") }
        .onFailure { e ->
            System.err.println("Error: ${e.message}")
            exitProcess(2)
        }
}
