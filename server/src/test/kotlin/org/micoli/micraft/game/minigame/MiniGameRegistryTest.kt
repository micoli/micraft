package org.micoli.micraft.game.minigame

import kotlin.io.path.createTempDirectory
import kotlin.io.path.writeText
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class MiniGameRegistryTest {

    @Test
    fun load_readsGameTypesFromYaml() {
        val configDir = createTempDirectory("config")
        val resourcesConfig = configDir.resolve("minigames.yaml")
        resourcesConfig.writeText(
            """
            tictactoe:
              displayName: "Tic-Tac-Toe"
              entryUrl: /minigames/tictactoe/bundle.js
              minPlayers: 2
              maxPlayers: 2
            """
                .trimIndent())
        val path = configDir.resolve("data-minigames.yaml")

        val loader = MiniGameRegistryLoader(path = path, resourcesPath = resourcesConfig)
        val registry = MiniGameRegistry(loader)

        val definition = registry.find("tictactoe")
        assertEquals("tictactoe", definition?.gameType)
        assertEquals("Tic-Tac-Toe", definition?.displayName)
        assertEquals("/minigames/tictactoe/bundle.js", definition?.entryUrl)
        assertEquals(2, definition?.minPlayers)
        assertEquals(2, definition?.maxPlayers)
        assertEquals(1, registry.all().size)
    }

    @Test
    fun find_unknownGameType_returnsNull() {
        val configDir = createTempDirectory("config")
        val resourcesConfig = configDir.resolve("minigames.yaml")
        resourcesConfig.writeText(
            """
            tictactoe:
              displayName: "Tic-Tac-Toe"
              entryUrl: /minigames/tictactoe/bundle.js
            """
                .trimIndent())
        val path = configDir.resolve("data-minigames.yaml")

        val registry =
            MiniGameRegistry(MiniGameRegistryLoader(path = path, resourcesPath = resourcesConfig))

        assertNull(registry.find("does_not_exist"))
    }

    @Test
    fun reload_picksUpChangesFromDisk() {
        val configDir = createTempDirectory("config")
        val resourcesConfig = configDir.resolve("minigames.yaml")
        resourcesConfig.writeText(
            """
            tictactoe:
              displayName: "Tic-Tac-Toe"
              entryUrl: /minigames/tictactoe/bundle.js
            """
                .trimIndent())
        val path = configDir.resolve("data-minigames.yaml")
        val loader = MiniGameRegistryLoader(path = path, resourcesPath = resourcesConfig)
        val registry = MiniGameRegistry(loader)
        assertNull(registry.find("connect4"))

        resourcesConfig.writeText(
            """
            tictactoe:
              displayName: "Tic-Tac-Toe"
              entryUrl: /minigames/tictactoe/bundle.js
            connect4:
              displayName: "Connect Four"
              entryUrl: /minigames/connect4/bundle.js
            """
                .trimIndent())
        registry.reload()

        assertEquals("Connect Four", registry.find("connect4")?.displayName)
    }
}
