package org.micoli.micraft.di

import org.koin.core.annotation.Module
import org.koin.core.annotation.Single
import org.micoli.micraft.game.item.ItemRegistryLoader
import org.micoli.micraft.game.item.expandPlainColorItems
import org.micoli.micraft.game.placeable.furniture.FurnitureRegistryLoader
import org.micoli.micraft.game.placeable.siege.SiegeProjectileRegistryLoader
import org.micoli.micraft.game.placeable.siege.SiegeWeaponRegistryLoader
import org.micoli.micraft.game.plaincolor.PlainColorRegistryLoader
import org.micoli.micraft.game.vehicle.VehicleRegistryLoader
import org.micoli.micraft.game.world.BlockRegistry
import org.micoli.micraft.game.world.ItemRegistry
import org.micoli.micraft.game.world.PlainColorRegistry
import org.micoli.micraft.game.world.block.BlockRegistryLoader
import org.micoli.micraft.placeable.PlaceableDefinition
import org.micoli.micraft.placeable.PlaceableRegistry
import org.micoli.micraft.placeable.furniture.FurnitureRegistry
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
    furnitureRegistryLoader: FurnitureRegistryLoader,
) {
    PlainColorRegistry.load(plainColorRegistryLoader.load())
    val blocks = blockRegistryLoader.load()
    BlockRegistry.load(blocks, blockRegistryLoader.wireOrder())
    ItemRegistry.load(
        expandPlainColorItems(itemRegistryLoader.load(), blocks, PlainColorRegistry.all()))
    VehicleRegistry.load(vehicleRegistryLoader.load())
    SiegeWeaponRegistry.load(siegeWeaponRegistryLoader.load())
    SiegeProjectileRegistry.load(siegeProjectileRegistryLoader.load())
    FurnitureRegistry.load(furnitureRegistryLoader.load())
    // Merge each kind-specific placeable registry into the generic one — the bbmodelPath is the
    // model location relative to `resources/`, prefixed per kind; more kinds append here.
    PlaceableRegistry.load(
        SiegeWeaponRegistry.keys().associateWith { type ->
            PlaceableDefinition("siege/weapons/${SiegeWeaponRegistry.get(type)!!.bbmodelFile}")
        } +
            FurnitureRegistry.keys().associateWith { type ->
                val def = FurnitureRegistry.get(type)!!
                PlaceableDefinition("furnitures/${def.bbmodelFile}", def.rotatable)
            })
}

@Module
class RegistryModule {
    @Single fun blockRegistryLoader(): BlockRegistryLoader = BlockRegistryLoader()

    @Single fun itemRegistryLoader(): ItemRegistryLoader = ItemRegistryLoader()

    @Single fun plainColorRegistryLoader(): PlainColorRegistryLoader = PlainColorRegistryLoader()

    @Single fun vehicleRegistryLoader(): VehicleRegistryLoader = VehicleRegistryLoader()

    @Single fun siegeWeaponRegistryLoader(): SiegeWeaponRegistryLoader = SiegeWeaponRegistryLoader()

    @Single
    fun siegeProjectileRegistryLoader(): SiegeProjectileRegistryLoader =
        SiegeProjectileRegistryLoader()

    @Single fun furnitureRegistryLoader(): FurnitureRegistryLoader = FurnitureRegistryLoader()

    @Single(createdAtStart = true)
    fun registryBootstrap(
        blockRegistryLoader: BlockRegistryLoader,
        itemRegistryLoader: ItemRegistryLoader,
        plainColorRegistryLoader: PlainColorRegistryLoader,
        vehicleRegistryLoader: VehicleRegistryLoader,
        siegeWeaponRegistryLoader: SiegeWeaponRegistryLoader,
        siegeProjectileRegistryLoader: SiegeProjectileRegistryLoader,
        furnitureRegistryLoader: FurnitureRegistryLoader,
    ): RegistryBootstrapResult {
        loadRegistries(
            blockRegistryLoader,
            itemRegistryLoader,
            plainColorRegistryLoader,
            vehicleRegistryLoader,
            siegeWeaponRegistryLoader,
            siegeProjectileRegistryLoader,
            furnitureRegistryLoader)
        return RegistryBootstrapResult()
    }
}
