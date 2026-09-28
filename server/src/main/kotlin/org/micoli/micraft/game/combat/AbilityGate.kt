package org.micoli.micraft.game.combat

import org.micoli.micraft.game.classes.ClassDefinitionEntry
import org.micoli.micraft.game.session.PlayerSession
import org.micoli.micraft.player.rpg.CharacterData
import org.micoli.micraft.player.rpg.ClassResource

enum class AbilityKind {
    ATTACK,
    SPELL
}

enum class AbilityResource {
    MANA,
    RAGE,
    TOKENS
}

data class AbilityCost(val mana: Int = 0, val rage: Int = 0, val tokens: Int = 0)

data class AbilityRank(val cost: AbilityCost, val cooldownMs: Long)

sealed interface AbilityVerdict {
    /** Every check passed; nothing has been paid or started yet — see [AbilityGate.commit]. */
    data class Cleared(val ability: AbilityUse) : AbilityVerdict

    sealed interface Refused : AbilityVerdict

    data object NoCharacter : Refused

    data object UnknownAbility : Refused

    data object NotUnlocked : Refused

    data object UnknownRank : Refused

    data object OnGlobalCooldown : Refused

    data class OnCooldown(val remainingMs: Long) : Refused

    data class InsufficientResource(val resource: AbilityResource) : Refused
}

data class AbilityUse(val cooldownKey: String, val rank: AbilityRank)

fun abilityCooldownKey(abilityId: String, rank: Int) = "$abilityId:$rank"

/**
 * The one place that decides whether a Character may use an Ability at a Rank right now.
 *
 * Range is the caller's business (a locked target, a world point…): call [check], then the range
 * test, then [commit], so a refusal for range never charges or starts a cooldown.
 */
class AbilityGate(
    @Volatile private var classRegistry: Map<String, ClassDefinitionEntry>,
    @Volatile private var globalCooldownMs: Long,
    private val clock: () -> Long = System::currentTimeMillis,
) {
    fun reload(classRegistry: Map<String, ClassDefinitionEntry>, globalCooldownMs: Long) {
        this.classRegistry = classRegistry
        this.globalCooldownMs = globalCooldownMs
    }

    /** [ranks] is null when the Ability itself is unknown to its registry. */
    fun check(
        session: PlayerSession,
        kind: AbilityKind,
        abilityId: String,
        rank: Int,
        ranks: Map<Int, AbilityRank>?,
    ): AbilityVerdict {
        val character = session.characterData ?: return AbilityVerdict.NoCharacter
        if (ranks == null) return AbilityVerdict.UnknownAbility
        if (!isUnlocked(character, kind, abilityId, rank)) return AbilityVerdict.NotUnlocked
        val rankDef = ranks[rank] ?: return AbilityVerdict.UnknownRank

        val now = clock()
        if (now < session.combatState.globalCooldownUntilMs) return AbilityVerdict.OnGlobalCooldown
        val key = abilityCooldownKey(abilityId, rank)
        val cooldownUntil = character.cooldownsUntilMs[key] ?: 0L
        if (now < cooldownUntil) return AbilityVerdict.OnCooldown(cooldownUntil - now)
        missingResource(character, rankDef.cost)?.let {
            return AbilityVerdict.InsufficientResource(it)
        }
        return AbilityVerdict.Cleared(AbilityUse(key, rankDef))
    }

    /** Pays the cost and starts both the Global cooldown and the Ability's Cooldown. */
    fun commit(session: PlayerSession, use: AbilityUse) {
        val now = clock()
        val character = session.characterData ?: return
        val cost = use.rank.cost
        val paid =
            when (character.characterClass.classResource) {
                ClassResource.MANA ->
                    character.copy(currentMana = character.currentMana - cost.mana)
                ClassResource.RAGE ->
                    character.copy(currentRage = character.currentRage - cost.rage)
            }
        val cooldowns =
            paid.cooldownsUntilMs.filterValues { it > now } +
                if (use.rank.cooldownMs > 0) mapOf(use.cooldownKey to now + use.rank.cooldownMs)
                else emptyMap()
        session.characterData =
            paid.copy(
                currentTokens = paid.currentTokens - cost.tokens, cooldownsUntilMs = cooldowns)
        session.combatState =
            session.combatState.copy(globalCooldownUntilMs = now + globalCooldownMs)
    }

    private fun isUnlocked(
        character: CharacterData,
        kind: AbilityKind,
        abilityId: String,
        rank: Int,
    ): Boolean {
        val classDef = classRegistry[character.characterClass.name] ?: return true
        val unlocked =
            classDef.levels
                .filter { (level, _) -> level <= character.level }
                .values
                .flatMap { level ->
                    when (kind) {
                        AbilityKind.ATTACK -> level.attacks.map { it.attack to it.rank }
                        AbilityKind.SPELL -> level.spells.map { it.spell to it.rank }
                    }
                }
        if (unlocked.isEmpty()) return true
        return (abilityId to rank) in unlocked
    }

    private fun missingResource(character: CharacterData, cost: AbilityCost): AbilityResource? {
        val resource = character.characterClass.classResource
        if (resource == ClassResource.MANA && character.currentMana < cost.mana)
            return AbilityResource.MANA
        if (resource == ClassResource.RAGE && character.currentRage < cost.rage)
            return AbilityResource.RAGE
        if (character.currentTokens < cost.tokens) return AbilityResource.TOKENS
        return null
    }
}
