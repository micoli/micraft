package org.micoli.micraft.examples.hello

import java.util.UUID
import org.micoli.micraft.command.CommandHandler
import org.micoli.micraft.plugin.PluginEntrypoint
import org.micoli.micraft.plugin.TickHandler

class HelloPlugin : PluginEntrypoint {
    override val id: UUID = UUID.fromString("a1b2c3d4-0000-0000-0000-000000000000")
    override val name = "hello-world"

    override fun commands(): List<CommandHandler> = listOf(HelloCommand())

    override fun tickHandlers(): List<TickHandler> = listOf(HelloTickHandler())
}
