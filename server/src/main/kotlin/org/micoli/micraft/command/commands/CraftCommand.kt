package org.micoli.micraft.command.commands

import java.util.UUID
import org.micoli.micraft.command.CommandContext
import org.micoli.micraft.command.CommandHandler
import org.micoli.micraft.game.recipe.RecipeRegistry
import org.micoli.micraft.game.session.PlayerSession
import org.micoli.micraft.protocol.ServerMessage

class CraftCommand : CommandHandler {
    override val id: UUID = UUID.fromString("a9b8c7d6-e5f4-3210-abcd-fedcba987654")
    override val name = "craft"
    override val description = "Opens the crafting window."

    override suspend fun execute(session: PlayerSession, args: String, context: CommandContext) {
        session.send(ServerMessage.OpenCraft)
        session.send(
            ServerMessage.RecipeSync(
                recipes = RecipeRegistry.all(),
                knownRecipes = session.knownRecipes.toSet(),
            ))
    }
}
