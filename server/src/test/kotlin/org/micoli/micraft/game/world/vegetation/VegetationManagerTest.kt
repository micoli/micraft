package org.micoli.micraft.game.world.vegetation

import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.writeText
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import org.micoli.micraft.game.world.BlockPos
import org.micoli.micraft.support.testWorld

class VegetationManagerTest {
    private fun createManager(
        savePath: Path = Files.createTempFile("veg-save", ".json")
    ): VegetationManager {
        val dir = Files.createTempDirectory("veg-config")
        val config = VegetationConfig(dir.resolve("vegetation.yaml"))
        return VegetationManager(testWorld(), config, savePath)
    }

    @Test
    fun initialState_noActiveBlocks() {
        assertEquals(0, createManager().activeBlockCount())
    }

    @Test
    fun deactivate_nonExistentPos_doesNotThrow() {
        val mgr = createManager()
        mgr.deactivate(BlockPos(0, 0, 0))
        assertEquals(0, mgr.activeBlockCount())
    }

    @Test
    fun load_populatesBlocksFromSaveFile() {
        val saveFile = Files.createTempFile("veg-save-load", ".json")
        saveFile.writeText(
            """{"blocks":[{"pos":{"x":5,"y":10,"z":5},"chainName":"oak_growth","stageIndex":0,"ticksAccumulated":0,"ticksRequired":500}]}""")
        val dir = Files.createTempDirectory("veg-config-load")
        val config = VegetationConfig(dir.resolve("vegetation.yaml"))
        val mgr = VegetationManager(testWorld(), config, saveFile)
        mgr.load()
        assertEquals(1, mgr.activeBlockCount())
    }

    @Test
    fun deactivate_removesActiveBlock() {
        val saveFile = Files.createTempFile("veg-deact", ".json")
        saveFile.writeText(
            """{"blocks":[{"pos":{"x":1,"y":5,"z":1},"chainName":"oak_growth","stageIndex":0,"ticksAccumulated":0,"ticksRequired":500}]}""")
        val dir = Files.createTempDirectory("veg-config-deact")
        val config = VegetationConfig(dir.resolve("vegetation.yaml"))
        val mgr = VegetationManager(testWorld(), config, saveFile)
        mgr.load()
        assertEquals(1, mgr.activeBlockCount())
        mgr.deactivate(BlockPos(1, 5, 1))
        assertEquals(0, mgr.activeBlockCount())
    }

    @Test
    fun save_writesFile() {
        val saveFile = Files.createTempFile("veg-save-write", ".json")
        val mgr = createManager(saveFile)
        mgr.save()
        assertTrue(saveFile.toFile().exists())
        assertTrue(saveFile.toFile().length() > 0)
    }

    @Test
    fun reload_updatesConfig() {
        val mgr = createManager()
        mgr.reload(
            VegetationConfig(
                Files.createTempDirectory("veg-config-reload").resolve("vegetation.yaml")))
    }
}
