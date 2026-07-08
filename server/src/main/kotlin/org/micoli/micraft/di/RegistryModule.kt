package org.micoli.micraft.di

import java.nio.file.Path
import org.koin.core.qualifier.named
import org.koin.dsl.module
import org.micoli.micraft.dataPath
import org.micoli.micraft.game.item.ItemRegistryLoader
import org.micoli.micraft.game.world.BlockRegistry
import org.micoli.micraft.game.world.ItemRegistry
import org.micoli.micraft.game.world.block.BlockRegistryLoader

val REGISTRY_LOAD_BOOTSTRAP = named("registryLoadBootstrap")

val registryModule = module {
    single {
        BlockRegistryLoader(
            resourcesBlocksPath = Path.of("resources/blocks"),
            dataBlocksPath = Path.of("$dataPath/resources/blocks"),
        )
    }

    single {
        ItemRegistryLoader(
            Path.of("$dataPath/config/items.yaml"),
            Path.of("resources/config/items.yaml"),
        )
    }

    single(REGISTRY_LOAD_BOOTSTRAP, createdAtStart = true) {
        BlockRegistry.load(get<BlockRegistryLoader>().load())
        ItemRegistry.load(get<ItemRegistryLoader>().load())
    }
}
