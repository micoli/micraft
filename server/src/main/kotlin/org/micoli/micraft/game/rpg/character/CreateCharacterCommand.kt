package org.micoli.micraft.game.rpg.character

import java.util.UUID
import org.micoli.micraft.command.CommandContext
import org.micoli.micraft.command.CommandHandler
import org.micoli.micraft.game.rpg.CharacterConstants
import org.micoli.micraft.game.session.PlayerSession
import org.micoli.micraft.player.rpg.CharacterClass
import org.micoli.micraft.protocol.ServerMessage

class CreateCharacterCommand : CommandHandler {
    override val id: UUID = UUID.fromString("c3d4e5f6-a7b8-9012-cdef-123456789abc")
    override val name = "createcharacter"
    override val description = "Create your RPG character"
    override val usage = "$command <name> <class> <str> <dex> <intel> <wis> <con> <cha>"

    override suspend fun execute(session: PlayerSession, args: String, context: CommandContext) {
        val lang = session.state.language
        if (session.characterData != null) {
            session.send(
                ServerMessage.Notification(
                    context.i18n.t(lang, "rpg:server:already_has_character")))
            return
        }
        val parts = args.trim().split(Regex("\\s+"))
        if (parts.size < 8) {
            session.send(ServerMessage.Notification("Usage: $usage"))
            return
        }
        val name = parts[0]
        if (name.length !in 3..24) {
            session.send(ServerMessage.Notification(context.i18n.t(lang, "rpg:server:name_length")))
            return
        }
        val characterClass =
            runCatching { CharacterClass.valueOf(parts[1].uppercase()) }.getOrNull()
                ?: run {
                    val valid = CharacterClass.entries.joinToString(", ") { it.name.lowercase() }
                    session.send(
                        ServerMessage.Notification(
                            context.i18n.t(lang, "rpg:server:unknown_class", valid)))
                    return
                }
        val rawInts = (2..7).map { parts[it].toIntOrNull() }
        if (rawInts.any { it == null }) {
            session.send(ServerMessage.Notification("Stats must be integers. $usage"))
            return
        }
        val s = rawInts.map { it!! }
        val result =
            RpgCharacterBuilder.build(name, characterClass, s[0], s[1], s[2], s[3], s[4], s[5])
        val (character, derived) =
            when (result) {
                is RpgCharacterResult.Success -> result.character to result.derived
                is RpgCharacterResult.Failure -> {
                    val msg =
                        when (result.kind) {
                            RpgCharacterResult.Kind.NAME_LENGTH ->
                                context.i18n.t(lang, "rpg:server:name_length")
                            RpgCharacterResult.Kind.STAT_RANGE ->
                                context.i18n.t(lang, "rpg:server:stat_range")
                            RpgCharacterResult.Kind.BUDGET_EXCEEDED ->
                                context.i18n.t(
                                    lang,
                                    "rpg:server:budget_exceeded",
                                    result.cost,
                                    CharacterConstants.POINT_BUY_BUDGET)
                        }
                    session.send(ServerMessage.Notification(msg))
                    return
                }
            }
        session.characterData = character
        session.state = session.state.copy(characterData = character, rpgOptOut = false)
        context.savePlayer(session)
        session.send(ServerMessage.CharacterSync(character, derived, character.baseStats))
        session.send(
            ServerMessage.Notification(
                context.i18n.t(lang, "rpg:server:character_created", character.name)))
    }
}
