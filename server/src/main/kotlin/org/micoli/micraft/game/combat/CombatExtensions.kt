package org.micoli.micraft.game.combat

import org.micoli.micraft.game.armor.ArmorDefinition
import org.micoli.micraft.game.rpg.DerivedStatsCalculator
import org.micoli.micraft.game.session.PlayerSession
import org.micoli.micraft.player.rpg.CharacterData
import org.micoli.micraft.player.rpg.DerivedStats

fun PlayerSession.computeDerived(
    armorRegistry: Map<String, ArmorDefinition>,
    charData: CharacterData,
    effectNames: Set<String> = emptySet(),
): DerivedStats {
    val armors = state.armors.mapNotNull { armorRegistry[it]?.statBonus }
    return DerivedStatsCalculator.compute(charData, armors, effectNames)
}
