package org.micoli.micraft.game.combat

import org.micoli.micraft.I18nConfig
import org.micoli.micraft.game.session.PlayerSession
import org.micoli.micraft.protocol.ServerMessage

fun AbilityVerdict.Refused.notification(
    i18n: I18nConfig,
    session: PlayerSession,
    kind: AbilityKind,
    abilityId: String,
    rank: Int,
): ServerMessage.Notification {
    val lang = session.state.language
    val text =
        when (this) {
            AbilityVerdict.NoCharacter -> i18n.t(lang, "combat:server:no_character")
            AbilityVerdict.UnknownAbility ->
                i18n.t(
                    lang,
                    when (kind) {
                        AbilityKind.ATTACK -> "combat:server:unknown_attack"
                        AbilityKind.SPELL -> "combat:server:unknown_spell"
                    },
                    abilityId)
            AbilityVerdict.NotUnlocked ->
                i18n.t(lang, "combat:server:not_unlocked", abilityId, rank)
            AbilityVerdict.UnknownRank ->
                i18n.t(lang, "combat:server:unknown_rank", abilityId, rank)
            AbilityVerdict.OnGlobalCooldown -> i18n.t(lang, "combat:server:global_cooldown")
            is AbilityVerdict.OnCooldown ->
                i18n.t(lang, "combat:server:on_cooldown", abilityId, rank)
            is AbilityVerdict.InsufficientResource ->
                i18n.t(
                    lang,
                    when (resource) {
                        AbilityResource.MANA -> "combat:server:not_enough_mana"
                        AbilityResource.RAGE -> "combat:server:not_enough_rage"
                        AbilityResource.TOKENS -> "combat:server:not_enough_tokens"
                    })
        }
    return ServerMessage.Notification(text)
}
