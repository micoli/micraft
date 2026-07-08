package org.micoli.micraft.game.rpg.character

import java.util.UUID
import org.micoli.micraft.command.CommandContext
import org.micoli.micraft.command.CommandHandler
import org.micoli.micraft.game.rpg.CharacterConstants
import org.micoli.micraft.game.rpg.DerivedStatsCalculator
import org.micoli.micraft.game.session.PlayerSession
import org.micoli.micraft.player.rpg.BaseStats
import org.micoli.micraft.player.rpg.CharacterClass
import org.micoli.micraft.player.rpg.CharacterData
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
        val statValues = rawInts.map { it!! }
        val str = statValues[0]
        val dex = statValues[1]
        val intel = statValues[2]
        val wis = statValues[3]
        val con = statValues[4]
        val cha = statValues[5]
        if (statValues.any {
            it !in CharacterConstants.STAT_MIN_BUY..CharacterConstants.STAT_MAX_BUY
        }) {
            session.send(ServerMessage.Notification(context.i18n.t(lang, "rpg:server:stat_range")))
            return
        }
        val totalCost = statValues.sumOf { CharacterConstants.POINT_BUY_COST[it] ?: 9 }
        if (totalCost > CharacterConstants.POINT_BUY_BUDGET) {
            session.send(
                ServerMessage.Notification(
                    context.i18n.t(
                        lang,
                        "rpg:server:budget_exceeded",
                        totalCost,
                        CharacterConstants.POINT_BUY_BUDGET)))
            return
        }
        val finalStats =
            BaseStats(
                str =
                    (str + characterClass.strBonus).coerceIn(1, CharacterConstants.STAT_MAX_TOTAL),
                dex =
                    (dex + characterClass.dexBonus).coerceIn(1, CharacterConstants.STAT_MAX_TOTAL),
                intel =
                    (intel + characterClass.intelBonus).coerceIn(
                        1, CharacterConstants.STAT_MAX_TOTAL),
                wis =
                    (wis + characterClass.wisBonus).coerceIn(1, CharacterConstants.STAT_MAX_TOTAL),
                con =
                    (con + characterClass.conBonus).coerceIn(1, CharacterConstants.STAT_MAX_TOTAL),
                cha =
                    (cha + characterClass.chaBonus).coerceIn(1, CharacterConstants.STAT_MAX_TOTAL),
            )
        val prelimChar =
            CharacterData(
                id = UUID.randomUUID().toString(),
                name = name,
                characterClass = characterClass,
                baseStats = finalStats,
                currentHp = 0,
                currentMana = 0,
            )
        val derived = DerivedStatsCalculator.compute(prelimChar)
        val character = prelimChar.copy(currentHp = derived.maxHp, currentMana = derived.maxMana)
        session.characterData = character
        session.state = session.state.copy(characterData = character, rpgOptOut = false)
        context.savePlayer(session)
        session.send(ServerMessage.CharacterSync(character, derived, character.baseStats))
        session.send(
            ServerMessage.Notification(
                context.i18n.t(lang, "rpg:server:character_created", character.name)))
    }
}
