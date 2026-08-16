package org.micoli.micraft.di

import java.nio.file.Path
import org.koin.core.annotation.Module
import org.koin.core.annotation.Single
import org.micoli.micraft.dataPath
import org.micoli.micraft.game.item.ItemRegistryLoader
import org.micoli.micraft.game.item.expandPlainColorItems
import org.micoli.micraft.game.plaincolor.PlainColorRegistryLoader
import org.micoli.micraft.game.vehicle.VehicleRegistryLoader
import org.micoli.micraft.game.world.BlockRegistry
import org.micoli.micraft.game.world.ItemRegistry
import org.micoli.micraft.game.world.PlainColorRegistry
import org.micoli.micraft.game.world.block.BlockRegistryLoader
import org.micoli.micraft.vehicle.VehicleRegistry

/**
 * Single load sequence shared by bootstrap and `/reload`: palette first (blocks reference it
 * through their generated items), then blocks, then items expanded with one variant per colorable
 * block × color.
 */
fun loadRegistries(
    blockRegistryLoader: BlockRegistryLoader,
    itemRegistryLoader: ItemRegistryLoader,
    plainColorRegistryLoader: PlainColorRegistryLoader,
    vehicleRegistryLoader: VehicleRegistryLoader,
) {
    PlainColorRegistry.load(plainColorRegistryLoader.load())
    val blocks = blockRegistryLoader.load()
    BlockRegistry.load(blocks)
    ItemRegistry.load(
        expandPlainColorItems(itemRegistryLoader.load(), blocks, PlainColorRegistry.all()))
    VehicleRegistry.load(vehicleRegistryLoader.load())
}

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

    @Single
    fun plainColorRegistryLoader(): PlainColorRegistryLoader =
        PlainColorRegistryLoader(
            Path.of("$dataPath/config/plain_colors.yaml"),
            Path.of("resources/config/plain_colors.yaml"),
        )

    @Single
    fun vehicleRegistryLoader(): VehicleRegistryLoader =
        VehicleRegistryLoader(
            Path.of("$dataPath/config/vehicles.yaml"),
            Path.of("resources/config/vehicles.yaml"),
        )

    @Single(createdAtStart = true)
    fun registryBootstrap(
        blockRegistryLoader: BlockRegistryLoader,
        itemRegistryLoader: ItemRegistryLoader,
        plainColorRegistryLoader: PlainColorRegistryLoader,
        vehicleRegistryLoader: VehicleRegistryLoader,
    ): RegistryBootstrapResult {
        loadRegistries(
            blockRegistryLoader,
            itemRegistryLoader,
            plainColorRegistryLoader,
            vehicleRegistryLoader)
        return RegistryBootstrapResult()
    }
}
