package org.micoli.micraft.di

import kotlin.io.path.createTempDirectory
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlinx.coroutines.runBlocking
import org.micoli.micraft.npc.NpcConfigLoader
import org.micoli.micraft.npc.NpcManager
import org.micoli.micraft.protocol.ServerMessage
import org.micoli.micraft.support.testI18n
import org.micoli.micraft.support.testSession
import org.micoli.micraft.support.testWeatherManager
import org.micoli.micraft.support.testWorld
import org.micoli.micraft.tick.VegetationManager
import org.micoli.micraft.world.BlockRegistryLoader
import org.micoli.micraft.world.DropConfig
import org.micoli.micraft.world.NpcRegistryLoader
import org.micoli.micraft.world.VegetationConfig

class ReloadCoordinatorTest {

    private fun buildCoordinator(
        reloadBiomes:
            (() -> org.micoli.micraft.world.proceduralGenerator.chunkGenerator.ChunkGenerator)? =
            null,
        reloadRegistries: (() -> Unit)? = null,
        reloadGameConfig: (() -> Unit)? = null,
        sessionRegistry: SessionRegistry = SessionRegistry(),
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
            object : org.micoli.micraft.world.proceduralGenerator.chunkGenerator.ChunkGenerator {
                override fun generate(pos: org.micoli.micraft.world.ChunkPos) =
                    org.micoli.micraft.world.Chunk.empty(pos)

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
}
