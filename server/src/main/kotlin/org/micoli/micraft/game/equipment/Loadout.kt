package org.micoli.micraft.game.equipment

import org.micoli.micraft.game.armor.ArmorClassRules
import org.micoli.micraft.game.armor.ArmorType
import org.micoli.micraft.game.rpg.CharacterStats
import org.micoli.micraft.game.session.PlayerSession
import org.micoli.micraft.player.Hand
import org.micoli.micraft.player.PlayerState
import org.micoli.micraft.player.rpg.CharacterClass
import org.micoli.micraft.protocol.ServerMessage

sealed interface GrantResult {
    data class Granted(val state: PlayerState, val name: String) : GrantResult

    data class AlreadyOwned(val name: String) : GrantResult

    data object Unknown : GrantResult
}

sealed interface EquipResult {
    data object Equipped : EquipResult

    data object Unknown : EquipResult

    data object NotOwned : EquipResult

    data object AlreadyWorn : EquipResult

    data class LevelTooLow(val required: Int, val actual: Int) : EquipResult

    data class WrongArmorType(val armorType: ArmorType) : EquipResult

    data class Overlap(val conflict: String) : EquipResult
}

sealed interface UnequipResult {
    data object Unequipped : UnequipResult

    data object NotWorn : UnequipResult
}

sealed interface WieldResult {
    data class Wielded(val hand: Hand) : WieldResult

    data object Unknown : WieldResult

    data object NotOwned : WieldResult

    data object WrongClass : WieldResult

    data object HandsFull : WieldResult

    data object WrongHand : WieldResult
}

sealed interface UnwieldResult {
    data class Unwielded(val item: String) : UnwieldResult

    data object Empty : UnwieldResult
}

/**
 * Owns what a Character may own and wear: ownership grants and equip rules. A change to what is
 * worn or wielded is broadcast, saved and resynced here.
 */
class Loadout(
    private val catalog: EquipmentCatalog,
    private val characterStats: CharacterStats = CharacterStats(catalog),
    private val broadcast: suspend (ServerMessage) -> Unit = {},
    private val savePlayer: suspend (PlayerSession) -> Unit = {},
) {
    fun grant(state: PlayerState, itemName: String): GrantResult {
        val name = catalog.resolve(itemName) ?: return GrantResult.Unknown
        if (state.owns(name)) return GrantResult.AlreadyOwned(name)
        val granted =
            when (name) {
                in catalog.armors -> state.copy(ownedArmors = state.ownedArmors + name)
                in catalog.weapons -> state.copy(ownedWeapons = state.ownedWeapons + name)
                else -> state.copy(ownedTools = state.ownedTools + name)
            }
        return GrantResult.Granted(granted, name)
    }

    /** Armor-only and exact-name: loot tables and quest rewards name armors by their id. */
    fun grantArmor(state: PlayerState, armorName: String): GrantResult {
        if (armorName !in catalog.armors) return GrantResult.Unknown
        return grant(state, armorName)
    }

    fun grantArmors(state: PlayerState, armorNames: List<String>): PlayerState =
        armorNames.fold(state) { acc, name ->
            (grantArmor(acc, name) as? GrantResult.Granted)?.state ?: acc
        }

    suspend fun equip(session: PlayerSession, name: String): EquipResult {
        val armor = catalog.armors[name] ?: return EquipResult.Unknown
        val state = session.state
        if (name !in state.ownedArmors) return EquipResult.NotOwned
        if (name in state.armors) return EquipResult.AlreadyWorn
        session.characterData?.let { character ->
            if (character.level < armor.requiredLevel) {
                return EquipResult.LevelTooLow(armor.requiredLevel, character.level)
            }
            if (!ArmorClassRules.canWear(character.characterClass, armor.armorType)) {
                return EquipResult.WrongArmorType(armor.armorType)
            }
        }
        val conflict =
            state.armors.firstOrNull { worn ->
                catalog.armors[worn]?.wearable?.overlaps(armor.wearable) == true
            }
        if (conflict != null) return EquipResult.Overlap(conflict)

        commit(session, state.copy(armors = state.armors + name))
        return EquipResult.Equipped
    }

    suspend fun unequip(session: PlayerSession, name: String): UnequipResult {
        val state = session.state
        if (name !in state.armors) return UnequipResult.NotWorn
        commit(session, state.copy(armors = state.armors - name))
        return UnequipResult.Unequipped
    }

    /** [hand] null picks the off hand first, the dominant hand for main-hand-only items. */
    suspend fun wield(session: PlayerSession, name: String, hand: Hand? = null): WieldResult {
        val item = wieldable(name) ?: return WieldResult.Unknown
        val state = session.state
        if (name !in item.ownedIn(state)) return WieldResult.NotOwned
        val characterClass = session.characterData?.characterClass
        if (item.allowedClasses != null &&
            (characterClass == null || characterClass !in item.allowedClasses)) {
            return WieldResult.WrongClass
        }
        val target = hand ?: freeHand(state, item.mainHandOnly) ?: return WieldResult.HandsFull
        if (item.mainHandOnly && target != state.dominantHand) return WieldResult.WrongHand

        commit(session, state.holding(target, name))
        return WieldResult.Wielded(target)
    }

    suspend fun unwield(session: PlayerSession, hand: Hand): UnwieldResult {
        val current = session.state.itemIn(hand) ?: return UnwieldResult.Empty
        commit(session, session.state.holding(hand, null))
        return UnwieldResult.Unwielded(current)
    }

    private suspend fun commit(session: PlayerSession, state: PlayerState) {
        session.state = state
        broadcast(ServerMessage.PlayerUpdate(state))
        if (!characterStats.resync(session)) savePlayer(session)
    }

    private class Wieldable(
        val ownedIn: (PlayerState) -> List<String>,
        val mainHandOnly: Boolean,
        val allowedClasses: Set<CharacterClass>?,
    )

    private fun wieldable(name: String): Wieldable? {
        catalog.weapons[name]?.let { weapon ->
            val category = catalog.weaponCategories[weapon.category]
            return Wieldable(
                PlayerState::ownedWeapons, category?.mainHandOnly == true, category?.allowedClasses)
        }
        val tool = catalog.tools[name] ?: return null
        val mainHandOnly = catalog.toolCategories[tool.category]?.mainHandOnly == true
        return Wieldable(PlayerState::ownedTools, mainHandOnly, allowedClasses = null)
    }

    private fun freeHand(state: PlayerState, mainHandOnly: Boolean): Hand? {
        val dominant = state.dominantHand
        if (mainHandOnly) return dominant
        val offHand = if (dominant == Hand.RIGHT) Hand.LEFT else Hand.RIGHT
        return listOf(offHand, dominant).firstOrNull { state.itemIn(it) == null }
    }

    private fun PlayerState.owns(name: String): Boolean =
        name in ownedArmors || name in ownedWeapons || name in ownedTools

    private fun PlayerState.itemIn(hand: Hand): String? =
        when (hand) {
            Hand.RIGHT -> rightHandItem
            Hand.LEFT -> leftHandItem
        }

    private fun PlayerState.holding(hand: Hand, item: String?): PlayerState =
        when (hand) {
            Hand.RIGHT -> copy(rightHandItem = item)
            Hand.LEFT -> copy(leftHandItem = item)
        }
}
