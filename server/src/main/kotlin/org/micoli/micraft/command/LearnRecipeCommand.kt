package org.micoli.micraft.command

import java.util.UUID
import org.micoli.micraft.CommandContext
import org.micoli.micraft.CommandHandler
import org.micoli.micraft.protocol.ServerMessage
import org.micoli.micraft.session.PlayerSession
import org.micoli.micraft.world.RecipeRegistry

class LearnRecipeCommand : CommandHandler {
    override val id: UUID = UUID.fromString("b2c3d4e5-f6a7-8901-bcde-f12345678901")
    override val command = "/learnrecipe"
    override val permission = "admin"
    override val description = "Teach a recipe to the player."
    override val usage = "/learnrecipe <recipeId>"
    override val options
        get() = RecipeRegistry.keys().map { it.lowercase() }

    override suspend fun execute(session: PlayerSession, args: String, context: CommandContext) {
        val lang = session.state.language
        val recipeId = args.trim().uppercase()
        if (recipeId.isBlank()) {
            val available = RecipeRegistry.keys().joinToString(", ") { it.lowercase() }
            session.send(
                ServerMessage.Notification(
                    context.i18n.t(lang, "craft:server:learnrecipe_usage", available)))
            return
        }
        if (RecipeRegistry.get(recipeId) == null) {
            session.send(
                ServerMessage.Notification(
                    context.i18n.t(lang, "craft:server:unknown_recipe", recipeId.lowercase())))
            return
        }
        session.knownRecipes.add(recipeId)
        context.savePlayer(session)
        session.send(
            ServerMessage.RecipeSync(
                recipes = RecipeRegistry.all(),
                knownRecipes = session.knownRecipes.toSet(),
            ))
        session.send(
            ServerMessage.Notification(
                context.i18n.t(lang, "craft:server:learned", recipeId.lowercase())))
    }
}
