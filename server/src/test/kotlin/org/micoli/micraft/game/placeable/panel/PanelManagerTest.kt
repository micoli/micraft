package org.micoli.micraft.game.placeable.panel

import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.runBlocking
import org.micoli.micraft.game.placeable.PlaceableManager
import org.micoli.micraft.game.world.BlockPos
import org.micoli.micraft.game.world.EntityType
import org.micoli.micraft.placeable.PlaceableDefinition
import org.micoli.micraft.placeable.PlaceableRegistry
import org.micoli.micraft.placeable.panel.PanelConstants
import org.micoli.micraft.protocol.ServerMessage
import org.micoli.micraft.support.testSession
import org.micoli.micraft.support.testWorld

class PanelManagerTest {

    private val panelType = EntityType("TEST_PANEL")
    private lateinit var savedPlaceables: Map<EntityType, PlaceableDefinition>

    @BeforeTest
    fun setUp() {
        testWorld()
        savedPlaceables = PlaceableRegistry.keys().associateWith { PlaceableRegistry.get(it)!! }
        PlaceableRegistry.load(
            savedPlaceables + mapOf(panelType to PlaceableDefinition("panels/TEST_PANEL")))
    }

    @AfterTest
    fun tearDown() {
        PlaceableRegistry.load(savedPlaceables)
    }

    private suspend fun spawnedPanel(
        broadcasts: MutableList<ServerMessage> = mutableListOf(),
        owner: String = "Alice",
    ): Pair<PlaceableManager, String> {
        val world = testWorld(Triple(8, 6, 8))
        val manager = PlaceableManager({ broadcasts.add(it) })
        val instance = manager.spawn(panelType, BlockPos(8, 7, 8), world)!!
        manager.panels.createFor(instance.id, owner)
        return manager to instance.id
    }

    @Test
    fun createFor_registersEmptyPanelAndBroadcastsLocalHomeUrl() = runBlocking {
        val broadcasts = mutableListOf<ServerMessage>()
        val (manager, id) = spawnedPanel(broadcasts)

        assertTrue(manager.panels.isPanel(id))
        val changed = broadcasts.filterIsInstance<ServerMessage.PanelChanged>().last()
        assertEquals(id, changed.info.placeableId)
        assertFalse(changed.info.external)
        assertEquals("/panels/$id/index.html", changed.info.homeUrl)
    }

    @Test
    fun save_withAllowedExternalUrl_switchesToExternalAndBroadcasts() = runBlocking {
        val broadcasts = mutableListOf<ServerMessage>()
        val panels = PanelManager({ broadcasts.add(it) }, allowlist = { listOf("example.com") })
        panels.createFor("p1", "Alice")

        val result = panels.save("p1", "https://sub.example.com/page", emptyMap())

        assertEquals(PanelSaveResult.OK, result)
        val info = panels.infoOf(panels.get("p1")!!)
        assertTrue(info.external)
        assertEquals("https://sub.example.com/page", info.homeUrl)
        assertTrue(broadcasts.filterIsInstance<ServerMessage.PanelChanged>().last().info.external)
    }

    @Test
    fun save_withDisallowedHost_isRejected() = runBlocking {
        val panels = PanelManager({}, allowlist = { listOf("example.com") })
        panels.createFor("p1", "Alice")

        val result = panels.save("p1", "https://evil.test/page", emptyMap())

        assertEquals(PanelSaveResult.INVALID_URL, result)
        assertEquals("", panels.get("p1")!!.externalUrl)
    }

    @Test
    fun save_withHttpUrl_isRejected() = runBlocking {
        val panels = PanelManager({}, allowlist = { listOf("example.com") })
        panels.createFor("p1", "Alice")

        assertEquals(
            PanelSaveResult.INVALID_URL, panels.save("p1", "http://example.com", emptyMap()))
    }

    @Test
    fun save_sanitizesPageHtmlAndStripsScripts() = runBlocking {
        val panels = PanelManager({})
        panels.createFor("p1", "Alice")

        val result =
            panels.save(
                "p1",
                "",
                mapOf(
                    "index" to
                        "<p onclick=\"evil()\">hi</p><script>evil()</script>" +
                            "<a href=\"javascript:evil()\">x</a><a href=\"other.html\">ok</a>"))

        assertEquals(PanelSaveResult.OK, result)
        val html = panels.pageHtml("p1", "index")!!
        assertFalse(html.contains("script"))
        assertFalse(html.contains("onclick"))
        assertFalse(html.contains("javascript:"))
        assertTrue(html.contains("other.html"))
    }

    @Test
    fun save_rejectsInvalidPageNameAndTooManyPages() = runBlocking {
        val panels = PanelManager({})
        panels.createFor("p1", "Alice")

        assertEquals(
            PanelSaveResult.INVALID_PAGE_NAME, panels.save("p1", "", mapOf("../etc" to "<p>x</p>")))

        val tooMany = (1..PanelConstants.MAX_PAGES + 1).associate { "page$it" to "<p>x</p>" }
        assertEquals(PanelSaveResult.TOO_MANY_PAGES, panels.save("p1", "", tooMany))
    }

    @Test
    fun save_onUnknownPanel_isRejected() = runBlocking {
        val panels = PanelManager({})
        assertEquals(PanelSaveResult.UNKNOWN_PANEL, panels.save("missing", "", emptyMap()))
    }

    @Test
    fun ownerOf_knownPanel_returnsItsOwner_unknownIsNull() = runBlocking {
        val panels = PanelManager({})
        panels.createFor("p1", "Alice")

        assertEquals("Alice", panels.ownerOf("p1"))
        assertNull(panels.ownerOf("missing"))
    }

    @Test
    fun despawn_removesLinkedPanelContentAndBroadcasts() = runBlocking {
        val broadcasts = mutableListOf<ServerMessage>()
        val (manager, id) = spawnedPanel(broadcasts)
        val session = testSession()

        manager.despawn(id, session)

        assertFalse(manager.panels.isPanel(id))
        assertTrue(broadcasts.any { it is ServerMessage.PanelRemoved && it.placeableId == id })
    }

    @Test
    fun pageHtml_emptyIndexServesPlaceholder_missingPageIsNull() = runBlocking {
        val panels = PanelManager({})
        panels.createFor("p1", "Alice")

        assertNotNull(panels.pageHtml("p1", "index"))
        assertNull(panels.pageHtml("p1", "missing"))
    }
}
