package org.micoli.micraft.game

import kotlinx.serialization.json.Json
import org.micoli.micraft.ChunkManager
import org.micoli.micraft.babylon.jsInitNpcModels
import org.micoli.micraft.babylon.jsInitNpcWalkBones
import org.micoli.micraft.babylon.jsInitPlaceableModels
import org.micoli.micraft.babylon.jsInitSiegeProjectileModels
import org.micoli.micraft.babylon.jsInitVehicleModels
import org.micoli.micraft.babylon.jsReloadAttackMeta
import org.micoli.micraft.babylon.jsSetBlockRegistry
import org.micoli.micraft.babylon.jsSetImpostorSkirtDepth
import org.micoli.micraft.babylon.jsSetItemRegistry
import org.micoli.micraft.babylon.jsSetNpcDefinitions
import org.micoli.micraft.babylon.jsSetPlainColors
import org.micoli.micraft.babylon.jsSetVehicleDefinitions
import org.micoli.micraft.game.world.BlockDefinition
import org.micoli.micraft.game.world.BlockRegistry
import org.micoli.micraft.game.world.BlockType
import org.micoli.micraft.game.world.EntityType
import org.micoli.micraft.game.world.ItemDefinition
import org.micoli.micraft.game.world.ItemRegistry
import org.micoli.micraft.game.world.ItemType
import org.micoli.micraft.game.world.PlainColor
import org.micoli.micraft.game.world.PlainColorRegistry
import org.micoli.micraft.game.world.WorldConstants
import org.micoli.micraft.game.world.rail.RailConnectionPoint
import org.micoli.micraft.game.world.rail.RailDefinition
import org.micoli.micraft.placeable.PlaceableDefinition
import org.micoli.micraft.placeable.PlaceableRegistry
import org.micoli.micraft.protocol.ServerMessage
import org.micoli.micraft.protocol.SiegeWeaponCodexInfo

/**
 * Loads the static game-data registries (blocks/items/placeables/NPCs/vehicles/siege weapons)
 * pushed once per session by the server.
 */
class RegistrySyncHandler(private val chunkManager: ChunkManager) : ServerMessageHandler {
    // Populated from ServerMessage.RegistrySync.siegeWeaponDefinitions — launch-math stats keyed
    // by placeableType id (a siege weapon always composes with a placeable of the same type).
    var siegeWeaponDefs: Map<String, SiegeWeaponCodexInfo> = emptyMap()
        private set

    override fun handle(msg: ServerMessage) {
        if (msg !is ServerMessage.RegistrySync) return
        val blockDefs =
            msg.blocks
                .mapIndexed { _, info ->
                    BlockType(info.name) to
                        BlockDefinition(
                            hardness = info.hardness,
                            solid = info.solid,
                            transparent = info.transparent,
                            minimapColor = info.minimapColor,
                            topColor = info.topColor,
                            sideColor = info.sideColor,
                            modelElement = info.modelElement,
                            gltfModel = info.gltfModel,
                            liquid = info.liquid,
                            viscosity = info.viscosity,
                            minimapVisible = info.minimapVisible,
                            rotatable = info.rotatable,
                            hasStuds = info.hasStuds,
                            brickSize = info.brickSize,
                            plainColorable = info.plainColorable,
                            isCubic = info.isCubic,
                            rail =
                                info.rail?.let { rail ->
                                    RailDefinition(
                                        connections =
                                            rail.connections.map { group ->
                                                group.map { RailConnectionPoint.parse(it) }
                                            },
                                        height = rail.height,
                                    )
                                },
                        )
                }
                .toMap()
        PlainColorRegistry.load(msg.plainColors.mapNotNull { PlainColor.fromHex(it.name, it.hex) })
        BlockRegistry.load(blockDefs, msg.blocks.map { BlockType(it.name) })
        chunkManager.repushAllToMinimap()
        val itemDefs =
            msg.items.entries.associate { (key, info) ->
                ItemType(key) to
                    ItemDefinition(
                        buildable = info.buildable,
                        placesBlock =
                            info.placesBlock?.let { runCatching { BlockType(it) }.getOrNull() },
                        plainColor = info.plainColor,
                        consumable = info.consumable,
                        spawnsEntity = info.spawnsEntity?.let { EntityType(it) },
                    )
            }
        ItemRegistry.load(itemDefs)
        WorldConstants.IMPOSTOR_SKIRT_DEPTH = msg.impostorSkirtDepth
        jsSetImpostorSkirtDepth(msg.impostorSkirtDepth)
        jsSetPlainColors(Json.encodeToString(msg.plainColors))
        jsSetBlockRegistry(Json.encodeToString(msg.blocks))
        jsSetItemRegistry(Json.encodeToString(msg.items))
        if (msg.npcs.isNotEmpty()) jsInitNpcModels(Json.encodeToString(msg.npcs))
        if (msg.npcWalkBones.isNotEmpty()) jsInitNpcWalkBones(Json.encodeToString(msg.npcWalkBones))
        if (msg.npcDefinitions.isNotEmpty())
            jsSetNpcDefinitions(Json.encodeToString(msg.npcDefinitions))
        if (msg.vehicles.isNotEmpty()) jsInitVehicleModels(Json.encodeToString(msg.vehicles))
        if (msg.vehicleDefinitions.isNotEmpty())
            jsSetVehicleDefinitions(Json.encodeToString(msg.vehicleDefinitions))
        if (msg.placeables.isNotEmpty()) {
            jsInitPlaceableModels(Json.encodeToString(msg.placeables))
            PlaceableRegistry.load(
                msg.placeables.entries.associate { (type, bbmodelPath) ->
                    EntityType(type) to PlaceableDefinition(bbmodelPath)
                })
        }
        if (msg.siegeProjectiles.isNotEmpty())
            jsInitSiegeProjectileModels(Json.encodeToString(msg.siegeProjectiles))
        if (msg.siegeWeaponDefinitions.isNotEmpty()) siegeWeaponDefs = msg.siegeWeaponDefinitions
        jsReloadAttackMeta()
    }
}
