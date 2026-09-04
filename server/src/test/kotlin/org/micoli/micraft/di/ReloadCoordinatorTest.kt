package org.micoli.micraft.di

import kotlin.io.path.createTempDirectory
import kotlin.io.path.writeText
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlinx.coroutines.runBlocking
import org.micoli.micraft.game.drop.DropConfig
import org.micoli.micraft.game.npc.NpcConfigLoader
import org.micoli.micraft.game.npc.NpcManager
import org.micoli.micraft.game.npc.NpcRegistryLoader
import org.micoli.micraft.game.quest.QuestManager
import org.micoli.micraft.game.quest.QuestRegistryLoader
import org.micoli.micraft.game.world.Chunk
import org.micoli.micraft.game.world.ChunkPos
import org.micoli.micraft.game.world.block.BlockRegistryLoader
import org.micoli.micraft.game.world.proceduralGenerator.chunkGenerator.ChunkGenerator
import org.micoli.micraft.game.world.vegetation.VegetationConfig
import org.micoli.micraft.game.world.vegetation.VegetationManager
import org.micoli.micraft.protocol.ServerMessage
import org.micoli.micraft.support.testI18n
import org.micoli.micraft.support.testSession
import org.micoli.micraft.support.testWeatherManager
import org.micoli.micraft.support.testWorld

class ReloadCoordinatorTest {

    private fun buildCoordinator(
        reloadBiomes: (() -> ChunkGenerator)? = null,
        reloadRegistries: (() -> Unit)? = null,
        reloadGameConfig: (() -> Unit)? = null,
        reloadRbac: (() -> Unit)? = null,
        reloadArmorRegistry: (() -> Unit)? = null,
        reloadEquipmentCategories: (() -> Unit)? = null,
        reloadRecipeRegistry: (() -> Unit)? = null,
        sessionRegistry: SessionRegistry = SessionRegistry(),
        questManager: QuestManager? = null,
        questRegistryLoader: QuestRegistryLoader? = null,
    ): ReloadCoordinator {
        val emptyResources = createTempDirectory("reload-resources")
        val emptyData = createTempDirectory("reload-data")
        val world = testWorld()
        return ReloadCoordinator(
            dropConfig = DropConfig(BlockRegistryLoader(emptyResources, emptyData)),
            world = world,
            reloadBiomes = reloadBiomes,
            reloadRegistries = reloadRegistries,
            reloadGameConfig = reloadGameConfig,
            sessionRegistry = sessionRegistry,
            buildRegistrySync = { ServerMessage.RegistrySync(emptyList(), emptyMap()) },
            npcConfigLoader = NpcConfigLoader(emptyData.resolve("npc.yaml")),
            npcRegistryLoader = NpcRegistryLoader(emptyResources, emptyData),
            npcManager = NpcManager(broadcast = {}, getSessions = { emptyList() }),
            i18n = testI18n(),
            weatherManager = testWeatherManager(),
            vegetationManager =
                VegetationManager(
                    world,
                    VegetationConfig(emptyData.resolve("vegetation.yaml")),
                    emptyData.resolve("vegetation-save.json")),
            reloadRbac = reloadRbac,
            reloadArmorRegistry = reloadArmorRegistry,
            reloadEquipmentCategories = reloadEquipmentCategories,
            reloadRecipeRegistry = reloadRecipeRegistry,
            questManager = questManager,
            questRegistryLoader = questRegistryLoader,
        )
    }

    @Test
    fun reload_withNoOptionalHooks_stillReloadsCoreSystems() = runBlocking {
        val coordinator = buildCoordinator()
        val summary = coordinator.reload("en")
        assertTrue(summary.isNotEmpty())
    }

    @Test
    fun reload_invokesReloadBiomesWhenProvided() = runBlocking {
        var invoked = false
        val fakeGenerator =
            object : ChunkGenerator {
                override fun generate(pos: ChunkPos) = Chunk.empty(pos)

                override fun biomeAt(wx: Int, wz: Int) = "plains"
            }
        val coordinator =
            buildCoordinator(
                reloadBiomes = {
                    invoked = true
                    fakeGenerator
                })
        coordinator.reload("en")
        assertTrue(invoked)
    }

    @Test
    fun reload_withoutReloadBiomes_doesNotInvokeHook() = runBlocking {
        val coordinator = buildCoordinator(reloadBiomes = null)
        // Nothing to invoke; just confirm reload completes without error.
        val summary = coordinator.reload("en")
        assertTrue(summary.isNotEmpty())
    }

    @Test
    fun reload_invokesReloadRegistriesAndBroadcastsSync() = runBlocking {
        var invoked = false
        val sessionRegistry = SessionRegistry()
        val session = testSession(id = "a")
        sessionRegistry["a"] = session
        val coordinator =
            buildCoordinator(
                reloadRegistries = { invoked = true }, sessionRegistry = sessionRegistry)
        coordinator.reload("en")
        assertTrue(invoked)
        assertTrue(session.sent.any { it is ServerMessage.RegistrySync })
    }

    @Test
    fun reload_invokesReloadGameConfigAndBroadcastsSync() = runBlocking {
        var invoked = false
        val sessionRegistry = SessionRegistry()
        val session = testSession(id = "a")
        sessionRegistry["a"] = session
        val coordinator =
            buildCoordinator(
                reloadGameConfig = { invoked = true }, sessionRegistry = sessionRegistry)
        coordinator.reload("en")
        assertTrue(invoked)
        assertTrue(session.sent.any { it is ServerMessage.GameConfigSync })
    }

    @Test
    fun reload_withoutOptionalHooks_doesNotBroadcastSyncMessages() = runBlocking {
        val sessionRegistry = SessionRegistry()
        val session = testSession(id = "a")
        sessionRegistry["a"] = session
        val coordinator = buildCoordinator(sessionRegistry = sessionRegistry)
        coordinator.reload("en")
        assertFalse(session.sent.any { it is ServerMessage.RegistrySync })
        assertFalse(session.sent.any { it is ServerMessage.GameConfigSync })
    }

    @Test
    fun reload_invokesReloadRbacWhenProvided() = runBlocking {
        var invoked = false
        val coordinator = buildCoordinator(reloadRbac = { invoked = true })
        coordinator.reload("en")
        assertTrue(invoked)
    }

    @Test
    fun reload_withoutReloadRbac_doesNotInvokeHook() = runBlocking {
        val coordinator = buildCoordinator(reloadRbac = null)
        val summary = coordinator.reload("en")
        assertTrue(summary.isNotEmpty())
    }

    @Test
    fun reload_invokesReloadArmorRegistryWhenProvided() = runBlocking {
        var invoked = false
        val coordinator = buildCoordinator(reloadArmorRegistry = { invoked = true })
        coordinator.reload("en")
        assertTrue(invoked)
    }

    @Test
    fun reload_invokesReloadEquipmentCategoriesWhenProvided() = runBlocking {
        var invoked = false
        val coordinator = buildCoordinator(reloadEquipmentCategories = { invoked = true })
        coordinator.reload("en")
        assertTrue(invoked)
    }

    @Test
    fun reload_withoutReloadEquipmentCategories_doesNotInvokeHook() = runBlocking {
        val coordinator = buildCoordinator(reloadEquipmentCategories = null)
        val summary = coordinator.reload("en")
        assertTrue(summary.isNotEmpty())
    }

    @Test
    fun reload_invokesReloadRecipeRegistryWhenProvided() = runBlocking {
        var invoked = false
        val coordinator = buildCoordinator(reloadRecipeRegistry = { invoked = true })
        coordinator.reload("en")
        assertTrue(invoked)
    }

    /**
     * `QuestRegistryLoader.load()` is memoized (built once per `SharedGameServices`, shared across
     * every E2E `GameWorld`) — `/reload` must call `reload()` instead, or an edited quest YAML
     * would never be picked up by a running server.
     */
    @Test
    fun reload_questRegistryLoader_bypassesMemoizationAndPicksUpFileChanges() = runBlocking {
        val questsDir = createTempDirectory("reload-quests")
        questsDir
            .resolve("quest_a.yaml")
            .writeText("title: Original\ndescription: desc\ntype: KILL\n")
        val questRegistryLoader = QuestRegistryLoader(questsDir)
        val questManager =
            QuestManager(getSessions = { emptyList() }, savePlayer = {}).also {
                it.reloadDefinitions(questRegistryLoader.load())
            }
        assertEquals("Original", questManager.getDefinitions().getValue("quest_a.yaml").title)

        questsDir
            .resolve("quest_a.yaml")
            .writeText("title: Updated\ndescription: desc\ntype: KILL\n")
        val coordinator =
            buildCoordinator(questManager = questManager, questRegistryLoader = questRegistryLoader)
        coordinator.reload("en")

        assertEquals(
            "Updated",
            questManager.getDefinitions().getValue("quest_a.yaml").title,
            "/reload must bypass the loader cache and pick up the edited quest file")
    }
}
