package org.micoli.micraft.config

import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals

class ConfigPathsTest {

    @Test
    fun dataRootFrom_blankOrNull_fallsBackToData() {
        assertEquals(Path.of("data"), ConfigPaths.dataRootFrom(null))
        assertEquals(Path.of("data"), ConfigPaths.dataRootFrom(""))
        assertEquals(Path.of("data"), ConfigPaths.dataRootFrom("   "))
    }

    @Test
    fun dataRootFrom_setValue_isHonored() {
        assertEquals(Path.of("/srv/micraft-data"), ConfigPaths.dataRootFrom("/srv/micraft-data"))
    }

    @Test
    fun pairs_areBuiltUnderTheRightRoots() {
        val cfg = ConfigPaths.configPair("weather.yaml")
        assertEquals(ConfigPaths.resourcesRoot.resolve("config/weather.yaml"), cfg.resources)
        assertEquals(ConfigPaths.dataRoot.resolve("config/weather.yaml"), cfg.data)

        val dir = ConfigPaths.dirPair("blocks")
        assertEquals(ConfigPaths.resourcesRoot.resolve("blocks"), dir.resources)
        assertEquals(ConfigPaths.dataRoot.resolve("resources/blocks"), dir.data)
    }
}
