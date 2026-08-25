package org.micoli.micraft.game.placeable.siege

import java.nio.file.Path
import kotlin.io.path.createTempDirectory
import kotlin.io.path.readText
import kotlin.io.path.writeText
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import org.micoli.micraft.game.world.EntityType
import org.micoli.micraft.game.world.ItemType

class SiegeWeaponRegistryLoaderTest {

    private data class LoaderContext(
        val loader: SiegeWeaponRegistryLoader,
        val dataDir: Path,
    )

    private fun loaderWithWeapons(
        weapons: Map<String, String>,
        overrides: Map<String, String> = emptyMap(),
    ): LoaderContext {
        val resourcesDir = createTempDirectory("resources_siege_weapons")
        val dataDir = createTempDirectory("data_siege_weapons")
        weapons.forEach { (name, yaml) ->
            val weaponDir = resourcesDir.resolve(name)
            weaponDir.toFile().mkdir()
            weaponDir.resolve("$name.yaml").writeText(yaml)
        }
        overrides.forEach { (name, yaml) ->
            val overrideDir = dataDir.resolve(name)
            overrideDir.toFile().mkdir()
            overrideDir.resolve("$name.yaml").writeText(yaml)
        }
        return LoaderContext(SiegeWeaponRegistryLoader(resourcesDir, dataDir), dataDir)
    }

    @Test
    fun validYaml_loadsAllWeapons() {
        val (loader) =
            loaderWithWeapons(
                mapOf(
                    "CATAPULT" to
                        "bbmodelFile: CATAPULT\nwidth: 2.0\nheight: 1.5\nprojectileType: BOULDER\nammoItem: BOULDER\n",
                    "TREBUCHET" to
                        "bbmodelFile: TREBUCHET\nwidth: 2.5\nheight: 3.0\nprojectileType: BOULDER\nammoItem: FLAMING_BOULDER\n",
                ))
        val result = loader.load()
        assertEquals(2, result.size)
        val catapult = result[EntityType("CATAPULT")]!!
        assertEquals("CATAPULT", catapult.bbmodelFile)
        assertEquals(2.0f, catapult.width)
        assertEquals(1.5f, catapult.height)
        assertEquals("BOULDER", catapult.projectileType)
        assertEquals(ItemType("BOULDER"), catapult.ammoItem)
    }

    @Test
    fun defaults_appliedWhenFieldsOmitted() {
        val (loader) = loaderWithWeapons(mapOf("CATAPULT" to "projectileType: BOULDER\n"))
        val result = loader.load()
        val def = result[EntityType("CATAPULT")]!!
        assertEquals(0.8f, def.width)
        assertEquals(0.8f, def.height)
        assertEquals(null, def.ammoItem)
        assertEquals(3f, def.impactRadius)
    }

    @Test
    fun missingYamlInDirectory_isSkipped() {
        val resourcesDir = createTempDirectory("resources_siege_weapons")
        resourcesDir.resolve("EMPTY_DIR").toFile().mkdir()
        val dataDir = createTempDirectory("data_siege_weapons")
        val loader = SiegeWeaponRegistryLoader(resourcesDir, dataDir)
        assertTrue(loader.load().isEmpty())
    }

    @Test
    fun invalidYaml_skipsWeapon() {
        val (loader) =
            loaderWithWeapons(
                mapOf(
                    "CATAPULT" to "projectileType: BOULDER\n",
                    "TREBUCHET" to "this is not: [valid yaml: }",
                ))
        val result = loader.load()
        assertEquals(1, result.size)
        assertTrue(result.containsKey(EntityType("CATAPULT")))
        assertFalse(result.containsKey(EntityType("TREBUCHET")))
    }

    @Test
    fun dataOverride_mergesAndWritesBack() {
        val (loader, dataDir) =
            loaderWithWeapons(
                weapons =
                    mapOf(
                        "CATAPULT" to
                            "bbmodelFile: CATAPULT\nwidth: 2.0\nheight: 1.5\nprojectileType: BOULDER\n"),
                overrides = mapOf("CATAPULT" to "impactDamage: 99\n"),
            )
        val result = loader.load()
        val def = result[EntityType("CATAPULT")]!!
        assertEquals(99, def.impactDamage)
        assertEquals(2.0f, def.width, "Non-overridden fields stay from the base entry")
        val writtenBack = dataDir.resolve("CATAPULT/CATAPULT.yaml").readText()
        assertTrue(writtenBack.contains("99"), "Override value preserved in write-back")
        assertTrue(writtenBack.contains("width"), "Missing keys added in write-back as comments")
    }

    @Test
    fun dataOverride_absent_notCreated() {
        val (_, dataDir) = loaderWithWeapons(mapOf("CATAPULT" to "projectileType: BOULDER\n"))
        assertFalse(dataDir.resolve("CATAPULT/CATAPULT.yaml").toFile().exists())
    }

    @Test
    fun reload_isIdempotent() {
        val (loader, dataDir) =
            loaderWithWeapons(
                weapons = mapOf("CATAPULT" to "projectileType: BOULDER\n"),
                overrides = mapOf("CATAPULT" to "impactDamage: 99\n"),
            )
        loader.reload()
        val afterFirst = dataDir.resolve("CATAPULT/CATAPULT.yaml").readText()
        loader.reload()
        val afterSecond = dataDir.resolve("CATAPULT/CATAPULT.yaml").readText()
        assertEquals(afterFirst, afterSecond)
    }
}
