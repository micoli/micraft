package org.micoli.micraft.world

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.micoli.micraft.player.Orientation
import org.micoli.micraft.player.PlayerState
import org.micoli.micraft.player.Vec3
import org.micoli.micraft.ui.GameLayout
import org.micoli.micraft.ui.LayoutWidget
import org.micoli.micraft.ui.WidgetType
import org.micoli.micraft.ui.defaultLayout
import org.micoli.micraft.ui.validateLayouts

class LayoutPersistenceTest {

    private fun minimalState() =
        PlayerState(
            id = "x",
            name = "Alice",
            pos = Vec3(0f, 0f, 0f),
            orientation = Orientation(0f, 0f),
        )

    @Test
    fun roundTrip_customLayouts_preservesData() {
        val custom =
            GameLayout("compact", listOf(LayoutWidget(WidgetType.HUD, x = 5, y = 5, w = 10, h = 4)))
        val state =
            minimalState().copy(layouts = listOf(defaultLayout(), custom), activeLayout = "compact")
        val json = Json.encodeToString(state)
        val decoded = Json.decodeFromString<PlayerState>(json)
        assertEquals(2, decoded.layouts.size)
        assertEquals("compact", decoded.activeLayout)
        assertEquals("compact", decoded.layouts[1].name)
        assertEquals(1, decoded.layouts[1].widgets.size)
        assertEquals(WidgetType.HUD, decoded.layouts[1].widgets[0].type)
    }

    @Test
    fun deserialise_missingLayouts_usesDefault() {
        // JSON without layouts/activeLayout fields (old save format)
        val json =
            """{"id":"x","name":"Alice","pos":{"x":0,"y":0,"z":0},"orientation":{"yaw":0,"pitch":0}}"""
        val decoded = Json { ignoreUnknownKeys = true }.decodeFromString<PlayerState>(json)
        assertNotNull(decoded.layouts)
        assertEquals(1, decoded.layouts.size)
        assertEquals("default", decoded.layouts[0].name)
        assertEquals("default", decoded.activeLayout)
    }

    @Test
    fun validateLayouts_duplicateNames_returnsError() {
        val layouts =
            listOf(
                GameLayout("a", emptyList()),
                GameLayout("a", emptyList()),
            )
        assertNotNull(validateLayouts(layouts, "a"))
    }

    @Test
    fun validateLayouts_emptyList_returnsError() {
        assertNotNull(validateLayouts(emptyList(), "default"))
    }

    @Test
    fun validateLayouts_activeNotInList_returnsError() {
        val layouts = listOf(GameLayout("default", emptyList()))
        assertNotNull(validateLayouts(layouts, "nonexistent"))
    }

    @Test
    fun validateLayouts_valid_returnsNull() {
        val layouts =
            listOf(
                GameLayout("default", emptyList()),
                GameLayout("compact", emptyList()),
            )
        assertNull(validateLayouts(layouts, "compact"))
    }
}
