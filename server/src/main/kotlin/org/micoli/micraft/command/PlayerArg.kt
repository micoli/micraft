package org.micoli.micraft.command

import org.micoli.micraft.game.session.PlayerSession
import org.micoli.micraft.game.world.sanitizePlayerName

/** `@<playerId>` token produced by player-name autocomplete. */
fun playerToken(session: PlayerSession): String = "@" + session.state.id

/** Online-player completions for a player-name argument, labelled by display name. */
fun CommandContext.playerCompletions(
    partial: String,
    excludeSelf: PlayerSession? = null,
): List<Completion> =
    sessions()
        .filter { it != excludeSelf && it.state.name.contains(partial, ignoreCase = true) }
        .map { Completion(it.state.name, playerToken(it)) }

/**
 * Resolves a player-name argument to a live session. An `@<id>` token (from autocomplete) matches
 * by stable id; anything else falls back to a sanitized, case-insensitive display-name match so a
 * manually typed name still works.
 */
/**
 * Canonical display name for a player-name argument: the live `state.name` when [token] resolves
 * (via `@<id>` or a sanitized name match), otherwise [token] unchanged. Lets downstream code that
 * only knows how to look players up by name still handle an autocomplete `@<id>` token.
 */
fun CommandContext.canonicalPlayerName(token: String): String =
    resolvePlayerSession(token)?.state?.name ?: token.trim()

fun CommandContext.resolvePlayerSession(token: String): PlayerSession? {
    val t = token.trim()
    if (t.startsWith("@")) {
        val id = t.drop(1)
        return sessions().find { it.state.id == id }
    }
    val sanitized = sanitizePlayerName(t)
    return sessions().find {
        sanitizePlayerName(it.state.name).equals(sanitized, ignoreCase = true)
    }
}
