package org.micoli.micraft.di

import org.micoli.micraft.game.session.PlayerSession
import org.micoli.micraft.game.world.WorldPersistence

/**
 * Extracted from [org.micoli.micraft.game.GameLoop] so collaborators built as Koin singletons can
 * save player state without depending on GameLoop's own private method.
 */
class PlayerPersister(private val persistence: WorldPersistence?) {
    fun save(session: PlayerSession) {
        persistence?.savePlayerState(
            session.state.name,
            session.state.copy(
                inventory = session.inventory.toMap(),
                shortcutBar = session.shortcutBar.toList(),
                knownRecipes = session.knownRecipes.toSet(),
                characterData = session.characterData,
            ),
        )
    }
}
