package org.micoli.micraft.world

import kotlin.io.path.createTempDirectory
import kotlin.io.path.createTempFile
import kotlin.io.path.writeText
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class RecipeRegistryLoaderTest {

    private fun loaderWith(yaml: String): RecipeRegistryLoader {
        val tmp = createTempFile(suffix = ".yaml")
        tmp.toFile().deleteOnExit()
        tmp.writeText(yaml)
        return RecipeRegistryLoader(tmp)
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
        val loader = RecipeRegistryLoader(path)
        assertTrue(path.toFile().exists(), "Default file should be generated")
        val result = loader.load()
        assertTrue(result.isNotEmpty())
    }

    @Test
    fun invalidYaml_fallsBackToDefaults() {
        val loader = loaderWith("this is not: [valid yaml: }")
        val result = loader.load()
        assertTrue(result.isNotEmpty(), "Corrupt YAML should fall back to built-in defaults")
    }
}
