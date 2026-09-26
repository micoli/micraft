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
    fun dataPath_underDefaultDataDir_isRebasedOntoTheDataRoot() {
        val root = Path.of("perf/.data")
        assertEquals(
            Path.of("perf/.data/config/auth/users.yaml"),
            ConfigPaths.dataPathFrom("data/config/auth/users.yaml", root))
        assertEquals(
            Path.of("data/config/auth/users.yaml"),
            ConfigPaths.dataPathFrom("data/config/auth/users.yaml", Path.of("data")))
    }

    @Test
    fun dataPath_outsideDefaultDataDir_isKeptAsIs() {
        val root = Path.of("perf/.data")
        assertEquals(
            Path.of("/etc/micraft/users.yaml"),
            ConfigPaths.dataPathFrom("/etc/micraft/users.yaml", root))
        assertEquals(
            Path.of("custom/users.yaml"), ConfigPaths.dataPathFrom("custom/users.yaml", root))
        assertEquals(
            Path.of("database/users.yaml"), ConfigPaths.dataPathFrom("database/users.yaml", root))
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
