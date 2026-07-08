package org.micoli.micraft.game.recipe

import kotlin.io.path.createTempDirectory
import kotlin.io.path.readText
import kotlin.io.path.writeText
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import org.micoli.micraft.game.world.ItemType

class RecipeRegistryLoaderTest {

    private fun loaderWith(yaml: String, resourcesYaml: String = "{}\n"): RecipeRegistryLoader {
        val dir = createTempDirectory()
        val tmp = dir.resolve("recipes.yaml")
        tmp.writeText(yaml)
        val resources = dir.resolve("recipes-defaults.yaml")
        resources.writeText(resourcesYaml)
        return RecipeRegistryLoader(tmp, resources)
    }

    @Test
    fun validYaml_loadsRecipes() {
        val loader =
            loaderWith(
                """
            MY_RECIPE:
              giveType: item
              giveId: "COBBLESTONE"
              giveAmount: 2
              items:
                - "GRAVEL*3"
                - "SAND*1"
            """
                    .trimIndent())
        val result = loader.load()
        assertEquals(1, result.size)
        val recipe = result["MY_RECIPE"]!!
        assertEquals("item", recipe.giveType)
        assertEquals("COBBLESTONE", recipe.giveId)
        assertEquals(2, recipe.giveAmount)
        assertEquals(2, recipe.ingredients.size)
        assertEquals(ItemType("GRAVEL"), recipe.ingredients[0].type)
        assertEquals(3, recipe.ingredients[0].count)
        assertEquals(ItemType("SAND"), recipe.ingredients[1].type)
        assertEquals(1, recipe.ingredients[1].count)
    }

    @Test
    fun ingredient_withoutCount_defaultsToOne() {
        val loader =
            loaderWith(
                """
            SIMPLE:
              giveType: block
              giveId: "DIRT"
              items:
                - "COBBLESTONE"
            """
                    .trimIndent())
        val result = loader.load()
        val recipe = result["SIMPLE"]!!
        assertEquals(1, recipe.ingredients[0].count)
    }

    @Test
    fun missingFile_generatesDefault() {
        val dir = createTempDirectory()
        val path = dir.resolve("recipes.yaml")
        val resources = dir.resolve("recipes-defaults.yaml")
        resources.writeText(
            "COBBLESTONE_BRICK:\n  giveType: block\n  giveId: COBBLESTONE\n  giveAmount: 4\n  items: []\n")
        val loader = RecipeRegistryLoader(path, resources)
        assertTrue(path.toFile().exists(), "Default file should be generated")
        val result = loader.load()
        assertTrue(result.isNotEmpty())
    }

    @Test
    fun invalidYaml_fallsBackToDefaults() {
        val loader =
            loaderWith(
                "this is not: [valid yaml: }",
                "COBBLESTONE_BRICK:\n  giveType: block\n  giveId: COBBLESTONE\n  giveAmount: 4\n  items: []\n")
        val result = loader.load()
        assertTrue(result.isNotEmpty(), "Corrupt YAML should fall back to built-in defaults")
    }

    @Test
    fun partialFile_missingDefaultEntriesAppendedAsComments() {
        val dir = createTempDirectory()
        val tmp = dir.resolve("recipes.yaml")
        tmp.writeText(
            "COBBLESTONE_BRICK:\n  giveType: block\n  giveId: COBBLESTONE\n  giveAmount: 4\n  items: [\"COBBLESTONE*2\", \"GRAVEL*1\"]\n")
        val resources = dir.resolve("recipes-defaults.yaml")
        resources.writeText(
            "COBBLESTONE_BRICK:\n  giveType: block\n  giveId: COBBLESTONE\n  giveAmount: 4\n  items: [\"COBBLESTONE*2\", \"GRAVEL*1\"]\nDIRT_PILE:\n  giveType: item\n  giveId: DIRT\n  giveAmount: 2\n  items: [\"GRAVEL*2\", \"SAND*1\"]\n")
        RecipeRegistryLoader(tmp, resources)
        val written = tmp.readText()
        assertTrue(
            written.contains("COBBLESTONE_BRICK:\n  giveType: block"), "existing entry untouched")
        assertTrue(written.contains("# DIRT_PILE:"), "missing default entry added as comment")
    }

    @Test
    fun missingEntry_isMergedFromResourcesDefault() {
        val dir = createTempDirectory()
        val tmp = dir.resolve("recipes.yaml")
        tmp.writeText(
            "COBBLESTONE_BRICK:\n  giveType: block\n  giveId: COBBLESTONE\n  giveAmount: 4\n  items: []\n")
        val resources = dir.resolve("recipes-defaults.yaml")
        resources.writeText(
            "COBBLESTONE_BRICK:\n  giveType: block\n  giveId: COBBLESTONE\n  giveAmount: 4\n  items: []\nDIRT_PILE:\n  giveType: item\n  giveId: DIRT\n  giveAmount: 2\n  items: []\n")
        val result = RecipeRegistryLoader(tmp, resources).load()
        assertEquals(2, result.size, "missing default entry is active in loaded result")
        assertTrue(result.containsKey("DIRT_PILE"))
    }
}
