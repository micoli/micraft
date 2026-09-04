package org.micoli.micraft.game.quest

import java.nio.file.Files
import kotlin.io.path.writeText
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotSame
import kotlin.test.assertSame

class QuestRegistryLoaderTest {
    private fun questYaml(title: String) =
        """
        title: $title
        description: desc
        type: KILL
        """
            .trimIndent()

    @Test
    fun load_isMemoized_ignoresFileChangesUntilReload() {
        val dir = Files.createTempDirectory("quest-registry-test")
        dir.resolve("quest_a.yaml").writeText(questYaml("A"))
        val loader = QuestRegistryLoader(dir)

        val first = loader.load()
        assertEquals("A", first.getValue("quest_a.yaml").title)
        assertSame(first, loader.load(), "second load() must return the cached instance")

        dir.resolve("quest_a.yaml").writeText(questYaml("B"))
        assertEquals(
            "A", loader.load().getValue("quest_a.yaml").title, "load() must still be memoized")

        val reloaded = loader.reload()
        assertNotSame(first, reloaded, "reload() must bypass the cache")
        assertEquals("B", reloaded.getValue("quest_a.yaml").title)
        assertSame(reloaded, loader.load(), "load() after reload() must return the fresh value")
    }
}
