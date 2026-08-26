package org.micoli.micraft.di

import java.nio.file.Path
import org.koin.core.annotation.Module
import org.koin.core.annotation.Single
import org.micoli.micraft.dataPath
import org.micoli.micraft.game.item.ItemRegistryLoader
import org.micoli.micraft.game.item.expandPlainColorItems
import org.micoli.micraft.game.placeable.siege.SiegeProjectileRegistryLoader
import org.micoli.micraft.game.placeable.siege.SiegeWeaponRegistryLoader
import org.micoli.micraft.game.plaincolor.PlainColorRegistryLoader
import org.micoli.micraft.game.vehicle.VehicleRegistryLoader
import org.micoli.micraft.game.world.BlockRegistry
import org.micoli.micraft.game.world.ItemRegistry
import org.micoli.micraft.game.world.PlainColorRegistry
import org.micoli.micraft.game.world.block.BlockIdRegistryLoader
import org.micoli.micraft.game.world.block.BlockRegistryLoader
import org.micoli.micraft.placeable.PlaceableDefinition
import org.micoli.micraft.placeable.PlaceableRegistry
import org.micoli.micraft.placeable.siege.SiegeProjectileRegistry
import org.micoli.micraft.placeable.siege.SiegeWeaponRegistry
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
    siegeWeaponRegistryLoader: SiegeWeaponRegistryLoader,
    siegeProjectileRegistryLoader: SiegeProjectileRegistryLoader,
) {
    PlainColorRegistry.load(plainColorRegistryLoader.load())
    val blocks = blockRegistryLoader.load()
    BlockRegistry.load(blocks, blockRegistryLoader.wireOrder())
    ItemRegistry.load(
        expandPlainColorItems(itemRegistryLoader.load(), blocks, PlainColorRegistry.all()))
    VehicleRegistry.load(vehicleRegistryLoader.load())
    SiegeWeaponRegistry.load(siegeWeaponRegistryLoader.load())
    SiegeProjectileRegistry.load(siegeProjectileRegistryLoader.load())
    // Merge each kind-specific placeable registry into the generic one — currently siege weapons
    // only, more kinds append here as they're added.
    PlaceableRegistry.load(
        SiegeWeaponRegistry.keys().associateWith { type ->
            PlaceableDefinition(bbmodelFile = SiegeWeaponRegistry.get(type)!!.bbmodelFile)
        })
}

@Module
class RegistryModule {
    @Single
    fun blockRegistryLoader(): BlockRegistryLoader =
        BlockRegistryLoader(
            resourcesBlocksPath = Path.of("resources/blocks"),
            dataBlocksPath = Path.of("$dataPath/resources/blocks"),
            blockIdRegistryLoader =
                BlockIdRegistryLoader(Path.of("$dataPath/config/block_ids.yaml")),
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

    @Single
    fun siegeWeaponRegistryLoader(): SiegeWeaponRegistryLoader =
        SiegeWeaponRegistryLoader(
            resourcesWeaponsPath = Path.of("resources/siege/weapons"),
            dataWeaponsPath = Path.of("$dataPath/resources/siege/weapons"),
        )

    @Single
    fun siegeProjectileRegistryLoader(): SiegeProjectileRegistryLoader =
        SiegeProjectileRegistryLoader(
            resourcesProjectilesPath = Path.of("resources/siege/projectiles"),
            dataProjectilesPath = Path.of("$dataPath/resources/siege/projectiles"),
        )

    @Single(createdAtStart = true)
    fun registryBootstrap(
        blockRegistryLoader: BlockRegistryLoader,
        itemRegistryLoader: ItemRegistryLoader,
        plainColorRegistryLoader: PlainColorRegistryLoader,
        vehicleRegistryLoader: VehicleRegistryLoader,
        siegeWeaponRegistryLoader: SiegeWeaponRegistryLoader,
        siegeProjectileRegistryLoader: SiegeProjectileRegistryLoader,
    ): RegistryBootstrapResult {
        loadRegistries(
            blockRegistryLoader,
            itemRegistryLoader,
            plainColorRegistryLoader,
            vehicleRegistryLoader,
            siegeWeaponRegistryLoader,
            siegeProjectileRegistryLoader)
        return RegistryBootstrapResult()
    }
}
