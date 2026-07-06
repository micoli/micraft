package org.micoli.micraft

import java.util.UUID
import org.micoli.micraft.session.PlayerSession

interface CommandHandler {
    val id: UUID
    val name: String
    val command: String
        get() = "/$name"

    val description: String
        get() = ""

    val permission: String?
        get() = null

    val usage: String
        get() = command

    val options: List<String>
        get() = emptyList()

    /** Arg indices for which this command provides server-side autocomplete. */
    val autocompleteArgs: List<Int>
        get() = if (options.isNotEmpty()) listOf(0) else emptyList()

    suspend fun execute(session: PlayerSession, args: String, context: CommandContext)

    /** Returns completions for the given arg index, or empty list if none. */
    suspend fun completeArg(
        argIndex: Int,
        partial: String,
        session: PlayerSession?,
        context: CommandContext,
    ): List<String> =
        if (argIndex == 0) options.filter { it.startsWith(partial, ignoreCase = true) }
        else emptyList()
}
