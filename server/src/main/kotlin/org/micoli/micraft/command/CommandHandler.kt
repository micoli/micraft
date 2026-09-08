package org.micoli.micraft.command

import java.util.UUID
import kotlinx.serialization.Serializable
import org.micoli.micraft.game.session.PlayerSession

/**
 * One autocomplete suggestion. [label] is shown in the console; [value] is inserted into the
 * command and sent on the wire (defaults to [label]). Player suggestions use a stable `@<id>` value
 * so the server resolves them without name casing / underscore ambiguity.
 */
@Serializable data class Completion(val label: String, val value: String = label)

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
        if (argIndex == 0) options.filter { it.contains(partial, ignoreCase = true) }
        else emptyList()

    /**
     * Rich completions with a distinct wire [Completion.value] (e.g. `@<playerId>`). Return `null`
     * to fall back to [completeArg] (each string becomes a `label == value` completion).
     */
    suspend fun completeArgRich(
        argIndex: Int,
        partial: String,
        session: PlayerSession?,
        context: CommandContext,
    ): List<Completion>? = null
}
