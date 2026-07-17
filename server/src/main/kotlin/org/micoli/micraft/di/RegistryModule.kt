package org.micoli.micraft.di

import java.nio.file.Path
import org.koin.core.annotation.Module
import org.koin.core.annotation.Single
import org.micoli.micraft.dataPath
import org.micoli.micraft.game.item.ItemRegistryLoader
import org.micoli.micraft.game.world.BlockRegistry
import org.micoli.micraft.game.world.ItemRegistry
import org.micoli.micraft.game.world.block.BlockRegistryLoader

@Module
class RegistryModule {
    @Single
    fun blockRegistryLoader(): BlockRegistryLoader =
        BlockRegistryLoader(
            resourcesBlocksPath = Path.of("resources/blocks"),
            dataBlocksPath = Path.of("$dataPath/resources/blocks"),
        )

    @Single
    fun itemRegistryLoader(): ItemRegistryLoader =
        ItemRegistryLoader(
            Path.of("$dataPath/config/items.yaml"),
            Path.of("resources/config/items.yaml"),
        )

    @Single(createdAtStart = true)
    fun registryBootstrap(
        blockRegistryLoader: BlockRegistryLoader,
        itemRegistryLoader: ItemRegistryLoader,
    ): RegistryBootstrapResult {
        BlockRegistry.load(blockRegistryLoader.load())
        ItemRegistry.load(itemRegistryLoader.load())
        return RegistryBootstrapResult()
    }
}
