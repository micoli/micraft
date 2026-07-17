package org.micoli.micraft.plugin

import org.micoli.micraft.command.CommandHandler
import org.micoli.micraft.command.Plugin

interface PluginEntrypoint : Plugin {
    fun commands(): List<CommandHandler> = emptyList()

    fun tickHandlers(): List<TickHandler> = emptyList()
}
