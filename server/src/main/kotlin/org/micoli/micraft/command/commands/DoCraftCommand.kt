package org.micoli.micraft.command.commands

import java.util.UUID
import org.micoli.micraft.command.CommandContext
import org.micoli.micraft.command.CommandHandler
import org.micoli.micraft.game.recipe.RecipeRegistry
import org.micoli.micraft.game.session.PlayerSession
import org.micoli.micraft.game.world.ItemType
import org.micoli.micraft.protocol.ServerMessage

class DoCraftCommand : CommandHandler {
    override val id: UUID = UUID.fromString("c3d4e5f6-a7b8-9012-cdef-123456789012")
    override val name = "docraft"
    override val description = "Crafts a recipe."
    override val usage = "$command <recipeId> [count]"
    override val options
        get() = RecipeRegistry.keys().map { it.lowercase() }

    override suspend fun execute(session: PlayerSession, args: String, context: CommandContext) {
        val lang = session.state.language
        val parts = args.trim().split(Regex("\\s+"))
        val recipeId = parts.getOrNull(0).orEmpty().uppercase()
        val count = parts.getOrNull(1)?.toIntOrNull()?.coerceAtLeast(1) ?: 1

        if (recipeId.isBlank()) {
            session.send(
                ServerMessage.Notification(context.i18n.t(lang, "craft:server:docraft_usage")))
            return
        }

        val recipe =
            RecipeRegistry.get(recipeId)
                ?: run {
                    session.send(
                        ServerMessage.Notification(
                            context.i18n.t(
                                lang, "craft:server:unknown_recipe", recipeId.lowercase())))
                    return
                }

        if (recipeId !in session.knownRecipes) {
            session.send(
                ServerMessage.Notification(
                    context.i18n.t(lang, "craft:server:not_known", recipeId.lowercase())))
            return
        }

        for (ingredient in recipe.ingredients) {
            val have = session.inventory[ingredient.type] ?: 0
            val need = ingredient.count * count
            if (have < need) {
                session.send(
                    ServerMessage.Notification(
                        context.i18n.t(
                            lang,
                            "craft:server:not_enough",
                            ingredient.type.id.lowercase(),
                            have,
                            need)))
                return
            }
        }

        for (ingredient in recipe.ingredients) {
            val need = ingredient.count * count
            val remaining = (session.inventory[ingredient.type] ?: 0) - need
            if (remaining <= 0) session.inventory.remove(ingredient.type)
            else session.inventory[ingredient.type] = remaining
        }

        val resultType = ItemType(recipe.giveId)
        val resultCount = recipe.giveAmount * count
        session.inventory.merge(resultType, resultCount, Int::plus)
        context.savePlayer(session)
        session.send(ServerMessage.InventoryUpdate(session.inventory.toMap()))
        session.send(
            ServerMessage.Notification(
                context.i18n.t(lang, "craft:server:done", resultCount, recipe.giveId.lowercase())))
    }
}
