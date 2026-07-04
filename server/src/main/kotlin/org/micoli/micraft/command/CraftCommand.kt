package org.micoli.micraft.command

import java.util.UUID
import org.micoli.micraft.CommandContext
import org.micoli.micraft.CommandHandler
import org.micoli.micraft.protocol.ServerMessage
import org.micoli.micraft.session.PlayerSession
import org.micoli.micraft.world.RecipeRegistry

class CraftCommand : CommandHandler {
    override val id: UUID = UUID.fromString("a9b8c7d6-e5f4-3210-abcd-fedcba987654")
    override val command = "/craft"
    override val description = "Opens the crafting window."
    override val usage = "/craft"

    override suspend fun execute(session: PlayerSession, args: String, context: CommandContext) {
        session.send(ServerMessage.OpenCraft)
        session.send(
            ServerMessage.RecipeSync(
                recipes = RecipeRegistry.all(),
                knownRecipes = session.knownRecipes.toSet(),
            ))
    }
}
