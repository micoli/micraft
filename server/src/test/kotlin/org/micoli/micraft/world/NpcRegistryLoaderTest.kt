package org.micoli.micraft.world

import org.micoli.micraft.npc.behaviors.InteractionableNpcBehavior
import org.micoli.micraft.npc.behaviors.RandomMovableNpcBehavior
import kotlin.io.path.createTempFile
import kotlin.io.path.writeText
import kotlin.io.path.deleteIfExists
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class NpcRegistryLoaderTest {

    private fun loaderWithYaml(yaml: String): NpcRegistryLoader {
        val tmp = createTempFile(suffix = ".yaml")
        tmp.toFile().deleteOnExit()
        tmp.writeText(yaml)
        return NpcRegistryLoader(tmp)
    }

    @Test
    fun load_parsesYamlCorrectly() {
        val loader = loaderWithYaml("""
            SELLER:
              bbmodelFile: npc_seller.bbmodel
              behavior: interactionable
              width: 0.6
              height: 1.8
              wanderSpeed: 0.0
              wanderRadius: 0.0
              spawn:
                autoSpawn: false
                maxTotal: 5
                maxPerChunk: 1
                spawnBiomes: []
            GOAT:
              bbmodelFile: npc_goat.bbmodel
              behavior: random_movable
              width: 0.5
              height: 0.9
              wanderSpeed: 2.0
              wanderRadius: 12.0
              spawn:
                autoSpawn: true
                maxTotal: 30
                maxPerChunk: 3
                spawnBiomes: [plains]
        """.trimIndent())
        val defs = loader.load()
        assertEquals(2, defs.size)
        val seller = defs["SELLER"]
        assertNotNull(seller)
        assertEquals("npc_seller.bbmodel", seller.bbmodelFile)
        assertEquals(0.6f, seller.width)
        assertEquals(1.8f, seller.height)
        assertTrue(seller.behavior is InteractionableNpcBehavior)
        assertFalse(seller.spawn.autoSpawn)
        assertEquals(5, seller.spawn.maxTotal)

        val goat = defs["GOAT"]
        assertNotNull(goat)
        assertTrue(goat.behavior is RandomMovableNpcBehavior)
        assertTrue(goat.spawn.autoSpawn)
        assertEquals(listOf("plains"), goat.spawn.spawnBiomes)
    }

    @Test
    fun load_missingSpawnSection_usesDefaults() {
        val loader = loaderWithYaml("""
            DUCK:
              bbmodelFile: npc_duck.bbmodel
              behavior: static
              width: 0.3
              height: 0.5
              wanderSpeed: 0.0
              wanderRadius: 0.0
        """.trimIndent())
        val defs = loader.load()
        val duck = defs["DUCK"]
        assertNotNull(duck)
        assertFalse(duck.spawn.autoSpawn)
        assertEquals(0, duck.spawn.maxTotal)
        assertEquals(1, duck.spawn.maxPerChunk)
        assertTrue(duck.spawn.spawnBiomes.isEmpty())
    }

    @Test
    fun load_unknownBehaviorKey_skipsEntry() {
        val loader = loaderWithYaml("""
            SELLER:
              bbmodelFile: npc_seller.bbmodel
              behavior: interactionable
              width: 0.6
              height: 1.8
              wanderSpeed: 0.0
              wanderRadius: 0.0
            UNKNOWN_NPC:
              bbmodelFile: npc_unknown.bbmodel
              behavior: totally_fake_behavior
              width: 0.6
              height: 1.8
              wanderSpeed: 0.0
              wanderRadius: 0.0
        """.trimIndent())
        val defs = loader.load()
        // SELLER should load, UNKNOWN_NPC should be skipped
        assertTrue(defs.containsKey("SELLER"))
        assertFalse(defs.containsKey("UNKNOWN_NPC"))
    }

    @Test
    fun load_missingFile_writesDefault() {
        val tmp = createTempFile(suffix = ".yaml")
        tmp.deleteIfExists()
        val loader = NpcRegistryLoader(tmp)
        val defs = loader.load()
        assertTrue(defs.isNotEmpty(), "Default NPC types should be written and loaded")
        tmp.toFile().deleteOnExit()
    }
}

private fun assertFalse(value: Boolean) = kotlin.test.assertFalse(value)
