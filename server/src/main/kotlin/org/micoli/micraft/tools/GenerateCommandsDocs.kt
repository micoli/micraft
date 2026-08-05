package org.micoli.micraft.tools

import io.github.classgraph.ClassGraph
import java.io.File
import kotlin.system.exitProcess
import org.micoli.micraft.command.CommandHandler
import org.micoli.micraft.command.PluginCommand

fun main(args: Array<String>) {
    val checkOnly = "--check" in args
    val readme = File("README.md")

    val handlers =
        ClassGraph().enableClassInfo().acceptPackages("org.micoli.micraft").scan().use { result ->
            result
                .getClassesImplementing(CommandHandler::class.java)
                .filter { !it.isAbstract && !it.isInterface }
                .mapNotNull { info ->
                    runCatching {
                            @Suppress("UNCHECKED_CAST")
                            (info.loadClass() as Class<CommandHandler>)
                                .getDeclaredConstructor()
                                .newInstance()
                        }
                        .getOrNull()
                }
        }

    val core = handlers.filter { it !is PluginCommand }
    val plugins = handlers.filterIsInstance<PluginCommand>()

    val section = buildSection(core, plugins)

    val content = readme.readText()
    val begin = "<!-- BEGIN_COMMANDS -->"
    val end = "<!-- END_COMMANDS -->"
    val beginIdx = content.indexOf(begin)
    val endIdx = content.indexOf(end)
    if (beginIdx < 0 || endIdx < 0) {
        System.err.println(
            "ERROR: sentinel markers <!-- BEGIN_COMMANDS --> / <!-- END_COMMANDS --> not found in README.md")
        exitProcess(1)
    }

    val replaced = content.substring(0, beginIdx) + section + content.substring(endIdx + end.length)

    if (checkOnly) {
        if (replaced == content) {
            println(
                "OK. README.md commands section is up to date (${core.size} core + ${plugins.size} plugin commands).")
        } else {
            System.err.println("README.md commands section is out of date. Run: make docs")
            exitProcess(1)
        }
    } else {
        readme.writeText(replaced)
        println("Done. ${core.size} core + ${plugins.size} plugin commands written to README.md")
    }
}

private fun buildSection(core: List<CommandHandler>, plugins: List<CommandHandler>): String =
    listOf(
            "<!-- BEGIN_COMMANDS -->",
            "",
            "### Core commands",
            "",
            buildTable(core),
            "",
            "### Plugin commands",
            "",
            buildTable(plugins),
            "",
            "<!-- END_COMMANDS -->",
        )
        .joinToString("\n")

private fun buildTable(commands: List<CommandHandler>): String {
    val header =
        listOf(
            "| Command | Usage | Description | Options / Autocomplete |",
            "|---------|-------|-------------|------------------------|",
        )
    val rows =
        commands
            .sortedBy { it.command }
            .map { c ->
                val opts =
                    when {
                        c.options.isNotEmpty() -> c.options.joinToString(", ")
                        c.autocompleteArgs.isNotEmpty() -> "dynamic"
                        else -> "—"
                    }
                "| `${c.command.escapeCell()}` | `${c.usage.escapeCell()}` | ${c.description.replace("\n", " ").escapeCell()} | $opts |"
            }
    return (header + rows).joinToString("\n")
}

private fun String.escapeCell() = replace("|", "\\|")
