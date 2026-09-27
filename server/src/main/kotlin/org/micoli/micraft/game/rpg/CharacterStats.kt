package org.micoli.micraft.game.rpg

import org.micoli.micraft.combat.StatusEffect
import org.micoli.micraft.game.combat.CombatConfigData
import org.micoli.micraft.game.equipment.EquipmentCatalog
import org.micoli.micraft.game.session.PlayerSession
import org.micoli.micraft.player.PlayerState
import org.micoli.micraft.player.rpg.BaseStats
import org.micoli.micraft.player.rpg.CharacterData
import org.micoli.micraft.player.rpg.ClassResource
import org.micoli.micraft.player.rpg.DerivedStats
import org.micoli.micraft.protocol.ServerMessage

data class StatSheet(val effective: BaseStats, val derived: DerivedStats)

/** The only place a Character's Effective stats and Derived stats are resolved. */
class CharacterStats(
    private val catalog: EquipmentCatalog = EquipmentCatalog(),
    @Volatile private var maxRage: Int = CombatConfigData().maxRage,
    private val savePlayer: suspend (PlayerSession) -> Unit = {},
) {
    fun reload(maxRage: Int) {
        this.maxRage = maxRage
    }

    fun affectsStats(effect: StatusEffect): Boolean = effect in DerivedStatsCalculator.STAT_EFFECTS

    fun of(
        character: CharacterData,
        state: PlayerState,
        effects: Collection<StatusEffect> = emptyList(),
    ): StatSheet {
        val bonuses = state.equipmentBonuses(catalog.armors, catalog.weapons, catalog.tools)
        val effective = DerivedStatsCalculator.effectiveBaseStats(character.baseStats, bonuses)
        val derived =
            DerivedStatsCalculator.compute(
                effective, character.level, bonuses.sumOf { it.acBonus }, effects)
        return StatSheet(effective, derived)
    }

    private fun of(session: PlayerSession, character: CharacterData): StatSheet =
        of(character, session.state, session.combatState.activeEffects.map { it.effect })

    fun derived(session: PlayerSession, character: CharacterData): DerivedStats =
        of(session, character).derived

    /**
     * Pushes the Character's full sheet after anything that may change a max (Loadout, Level,
     * stat-affecting effect), clamping current HP and mana to the new max. Returns whether the
     * clamp saved the Character.
     */
    suspend fun resync(session: PlayerSession): Boolean {
        val character = session.characterData ?: return false
        val sheet = of(session, character)
        val clamped =
            character.copy(
                currentHp = character.currentHp.coerceAtMost(sheet.derived.maxHp),
                currentMana = character.currentMana.coerceAtMost(sheet.derived.maxMana))
        if (clamped != character) {
            session.characterData = clamped
            savePlayer(session)
        }
        session.send(ServerMessage.CharacterSync(clamped, sheet.derived, sheet.effective))
        session.send(statusUpdate(session, clamped, sheet.derived))
        return clamped != character
    }

    suspend fun sendStatus(session: PlayerSession) {
        val character = session.characterData ?: return
        session.send(statusUpdate(session, character, derived(session, character)))
    }

    private fun statusUpdate(
        session: PlayerSession,
        character: CharacterData,
        derived: DerivedStats,
    ): ServerMessage.PlayerStatusUpdate {
        val isRage = character.characterClass.classResource == ClassResource.RAGE
        val now = System.currentTimeMillis()
        val combat = session.combatState
        return ServerMessage.PlayerStatusUpdate(
            currentHp = character.currentHp,
            maxHp = derived.maxHp,
            currentMana = if (isRage) 0 else character.currentMana,
            maxMana = if (isRage) 0 else derived.maxMana,
            currentRage = if (isRage) character.currentRage else 0,
            maxRage = if (isRage) maxRage else 0,
            currentTokens = if (isRage) character.currentTokens else 0,
            maxTokens = if (isRage) derived.maxTokens else 0,
            stance = session.state.stance,
            globalCooldownRemainingMs = (combat.attackCooldownUntilMs - now).coerceAtLeast(0),
            attackCooldownsRemainingMs =
                combat.attackCooldownsUntilMs
                    .mapValues { (_, until) -> (until - now).coerceAtLeast(0) }
                    .filter { (_, rem) -> rem > 0 },
            godMode = session.state.godMode,
        )
    }
}
