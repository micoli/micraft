// generated with @7nohe/openapi-react-query-codegen@3.0.2 

import { queryOptions } from "@tanstack/react-query";
import { getApiAdminBlocks, getApiAdminChunksDiscovered, getApiAdminClasses, getApiAdminConfigs, getApiAdminConfigsBy, getApiAdminInstances, getApiAdminInstancesById, getApiAdminInstancesByIdBlocks, getApiAdminItems, getApiAdminNpcTypes, getApiAdminNpcs, getApiAdminPlainColors, getApiAdminPlayers, getApiAdminPlayersByName, getApiAdminScenes, getApiAdminScenesById, getApiAdminScenesByIdBlocksRaw, getApiAdminScenesByIdEntities, getApiAdminSchemasByFilename, getApiAdminSimulationDefaults, getApiAdminSkills, getApiAdminStatus, getApiAdminUsers, getApiAdminWorlds, getApiAdminWsInstancesById, getApiAdminWsNpcs, getApiAdminWsScenesById, getApiAdminWsSimulation, getApiArmors, getApiAssetsManifest, getApiAttacks, getApiAuthConfig, getApiAutocompleteByCommandIdByArgIndex, getApiBiomes, getApiChunksByCxByCz, getApiClasses, getApiGameAssets, getApiGameAssetsBbmodelExportBy, getApiGameAssetsBlendPreviewBy, getApiGameAssetsBlendSceneBy, getApiGameAssetsFileBy, getApiI18nByLocale, getApiItemsMeta, getApiKeybindings, getApiLayoutRegistry, getApiMacrosContext, getApiMapHouses, getApiMapRoadRaster, getApiMapRoadRasterPng, getApiMapRoads, getApiMapStaircases, getApiMapState, getApiMapTerrain, getApiMapTerrainRasterPng, getApiMapVoronoi, getApiMapVoronoiBorders, getApiPlayerByIdArmors, getApiPlayerByIdHands, getApiPlayerByIdOwned, getApiPlayerByIdRpg, getApiPlayerByIdSkin, getApiPlayersByEmailByEmail, getApiPlayersNames, getApiQuests, getApiServerInfo, getApiSkins, getApiSkinsByNameConfig, getApiSpells, getApiTools, getApiWeapons, type Options } from "../requests/sdk.gen";
import type { GetApiAdminBlocksData, GetApiAdminChunksDiscoveredData, GetApiAdminClassesData, GetApiAdminConfigsByData, GetApiAdminConfigsData, GetApiAdminInstancesByIdBlocksData, GetApiAdminInstancesByIdData, GetApiAdminInstancesData, GetApiAdminItemsData, GetApiAdminNpcTypesData, GetApiAdminNpcsData, GetApiAdminPlainColorsData, GetApiAdminPlayersByNameData, GetApiAdminPlayersData, GetApiAdminScenesByIdBlocksRawData, GetApiAdminScenesByIdData, GetApiAdminScenesByIdEntitiesData, GetApiAdminScenesData, GetApiAdminSchemasByFilenameData, GetApiAdminSimulationDefaultsData, GetApiAdminSkillsData, GetApiAdminStatusData, GetApiAdminUsersData, GetApiAdminWorldsData, GetApiAdminWsInstancesByIdData, GetApiAdminWsNpcsData, GetApiAdminWsScenesByIdData, GetApiAdminWsSimulationData, GetApiArmorsData, GetApiAssetsManifestData, GetApiAttacksData, GetApiAuthConfigData, GetApiAutocompleteByCommandIdByArgIndexData, GetApiBiomesData, GetApiChunksByCxByCzData, GetApiClassesData, GetApiGameAssetsBbmodelExportByData, GetApiGameAssetsBlendPreviewByData, GetApiGameAssetsBlendSceneByData, GetApiGameAssetsData, GetApiGameAssetsFileByData, GetApiI18nByLocaleData, GetApiItemsMetaData, GetApiKeybindingsData, GetApiLayoutRegistryData, GetApiMacrosContextData, GetApiMapHousesData, GetApiMapRoadRasterData, GetApiMapRoadRasterPngData, GetApiMapRoadsData, GetApiMapStaircasesData, GetApiMapStateData, GetApiMapTerrainData, GetApiMapTerrainRasterPngData, GetApiMapVoronoiBordersData, GetApiMapVoronoiData, GetApiPlayerByIdArmorsData, GetApiPlayerByIdHandsData, GetApiPlayerByIdOwnedData, GetApiPlayerByIdRpgData, GetApiPlayerByIdSkinData, GetApiPlayersByEmailByEmailData, GetApiPlayersNamesData, GetApiQuestsData, GetApiServerInfoData, GetApiSkinsByNameConfigData, GetApiSkinsData, GetApiSpellsData, GetApiToolsData, GetApiWeaponsData } from "../requests/types.gen";
import * as Common from "./common";

/**
 * Active auth provider, used by the client to pick the right login UI
 */
export const getApiAuthConfigOptions = (clientOptions: Options<GetApiAuthConfigData, true> = {}, queryKey?: Array<unknown>) => queryOptions({ queryKey: Common.UseGetApiAuthConfigKeyFn(clientOptions, queryKey), queryFn: ({ signal }) => getApiAuthConfig({ ...clientOptions, signal, throwOnError: true }).then(response => response.data) });
export const getApiAssetsManifestOptions = (clientOptions: Options<GetApiAssetsManifestData, true> = {}, queryKey?: Array<unknown>) => queryOptions({ queryKey: Common.UseGetApiAssetsManifestKeyFn(clientOptions, queryKey), queryFn: ({ signal }) => getApiAssetsManifest({ ...clientOptions, signal, throwOnError: true }).then(response => response.data) });
export const getApiAdminWsNpcsOptions = (clientOptions: Options<GetApiAdminWsNpcsData, true> = {}, queryKey?: Array<unknown>) => queryOptions({ queryKey: Common.UseGetApiAdminWsNpcsKeyFn(clientOptions, queryKey), queryFn: ({ signal }) => getApiAdminWsNpcs({ ...clientOptions, signal, throwOnError: true }).then(response => response.data) });
export const getApiAdminWsScenesByIdOptions = (clientOptions: Options<GetApiAdminWsScenesByIdData, true> = {}, queryKey?: Array<unknown>) => queryOptions({ queryKey: Common.UseGetApiAdminWsScenesByIdKeyFn(clientOptions, queryKey), queryFn: ({ signal }) => getApiAdminWsScenesById({ ...clientOptions, signal, throwOnError: true }).then(response => response.data) });
export const getApiAdminWsInstancesByIdOptions = (clientOptions: Options<GetApiAdminWsInstancesByIdData, true> = {}, queryKey?: Array<unknown>) => queryOptions({ queryKey: Common.UseGetApiAdminWsInstancesByIdKeyFn(clientOptions, queryKey), queryFn: ({ signal }) => getApiAdminWsInstancesById({ ...clientOptions, signal, throwOnError: true }).then(response => response.data) });
export const getApiAdminWsSimulationOptions = (clientOptions: Options<GetApiAdminWsSimulationData, true> = {}, queryKey?: Array<unknown>) => queryOptions({ queryKey: Common.UseGetApiAdminWsSimulationKeyFn(clientOptions, queryKey), queryFn: ({ signal }) => getApiAdminWsSimulation({ ...clientOptions, signal, throwOnError: true }).then(response => response.data) });
/**
 * Key bindings — a player's saved bindings if ?player= is given and persistence is available, otherwise the default config
 */
export const getApiKeybindingsOptions = (clientOptions: Options<GetApiKeybindingsData, true> = {}, queryKey?: Array<unknown>) => queryOptions({ queryKey: Common.UseGetApiKeybindingsKeyFn(clientOptions, queryKey), queryFn: ({ signal }) => getApiKeybindings({ ...clientOptions, signal, throwOnError: true }).then(response => response.data) });
/**
 * Autocomplete suggestions for a slash command argument
 */
export const getApiAutocompleteByCommandIdByArgIndexOptions = (clientOptions: Options<GetApiAutocompleteByCommandIdByArgIndexData, true>, queryKey?: Array<unknown>) => queryOptions({ queryKey: Common.UseGetApiAutocompleteByCommandIdByArgIndexKeyFn(clientOptions, queryKey), queryFn: ({ signal }) => getApiAutocompleteByCommandIdByArgIndex({ ...clientOptions, signal, throwOnError: true }).then(response => response.data) });
/**
 * Client-facing translation keys for a locale
 */
export const getApiI18nByLocaleOptions = (clientOptions: Options<GetApiI18nByLocaleData, true>, queryKey?: Array<unknown>) => queryOptions({ queryKey: Common.UseGetApiI18nByLocaleKeyFn(clientOptions, queryKey), queryFn: ({ signal }) => getApiI18nByLocale({ ...clientOptions, signal, throwOnError: true }).then(response => response.data) });
/**
 * All widgets registered for the UI layout editor
 */
export const getApiLayoutRegistryOptions = (clientOptions: Options<GetApiLayoutRegistryData, true> = {}, queryKey?: Array<unknown>) => queryOptions({ queryKey: Common.UseGetApiLayoutRegistryKeyFn(clientOptions, queryKey), queryFn: ({ signal }) => getApiLayoutRegistry({ ...clientOptions, signal, throwOnError: true }).then(response => response.data) });
/**
 * Item metadata (label, background color, consumable flags) by item type id
 */
export const getApiItemsMetaOptions = (clientOptions: Options<GetApiItemsMetaData, true> = {}, queryKey?: Array<unknown>) => queryOptions({ queryKey: Common.UseGetApiItemsMetaKeyFn(clientOptions, queryKey), queryFn: ({ signal }) => getApiItemsMeta({ ...clientOptions, signal, throwOnError: true }).then(response => response.data) });
/**
 * Server build timestamp
 */
export const getApiServerInfoOptions = (clientOptions: Options<GetApiServerInfoData, true> = {}, queryKey?: Array<unknown>) => queryOptions({ queryKey: Common.UseGetApiServerInfoKeyFn(clientOptions, queryKey), queryFn: ({ signal }) => getApiServerInfo({ ...clientOptions, signal, throwOnError: true }).then(response => response.data) });
/**
 * Attack definitions, flattened by "attackId:level" key
 */
export const getApiAttacksOptions = (clientOptions: Options<GetApiAttacksData, true> = {}, queryKey?: Array<unknown>) => queryOptions({ queryKey: Common.UseGetApiAttacksKeyFn(clientOptions, queryKey), queryFn: ({ signal }) => getApiAttacks({ ...clientOptions, signal, throwOnError: true }).then(response => response.data) });
/**
 * Attack ids accessible per RPG class, keyed by level
 */
export const getApiClassesOptions = (clientOptions: Options<GetApiClassesData, true> = {}, queryKey?: Array<unknown>) => queryOptions({ queryKey: Common.UseGetApiClassesKeyFn(clientOptions, queryKey), queryFn: ({ signal }) => getApiClasses({ ...clientOptions, signal, throwOnError: true }).then(response => response.data) });
/**
 * Spell definitions, keyed by spell id
 */
export const getApiSpellsOptions = (clientOptions: Options<GetApiSpellsData, true> = {}, queryKey?: Array<unknown>) => queryOptions({ queryKey: Common.UseGetApiSpellsKeyFn(clientOptions, queryKey), queryFn: ({ signal }) => getApiSpells({ ...clientOptions, signal, throwOnError: true }).then(response => response.data) });
/**
 * Variables available to the macro JEXL evaluation context
 */
export const getApiMacrosContextOptions = (clientOptions: Options<GetApiMacrosContextData, true> = {}, queryKey?: Array<unknown>) => queryOptions({ queryKey: Common.UseGetApiMacrosContextKeyFn(clientOptions, queryKey), queryFn: ({ signal }) => getApiMacrosContext({ ...clientOptions, signal, throwOnError: true }).then(response => response.data) });
/**
 * Grass color per biome id, as [r, g, b] in 0..1
 */
export const getApiBiomesOptions = (clientOptions: Options<GetApiBiomesData, true> = {}, queryKey?: Array<unknown>) => queryOptions({ queryKey: Common.UseGetApiBiomesKeyFn(clientOptions, queryKey), queryFn: ({ signal }) => getApiBiomes({ ...clientOptions, signal, throwOnError: true }).then(response => response.data) });
/**
 * A player's current skin
 */
export const getApiPlayerByIdSkinOptions = (clientOptions: Options<GetApiPlayerByIdSkinData, true>, queryKey?: Array<unknown>) => queryOptions({ queryKey: Common.UseGetApiPlayerByIdSkinKeyFn(clientOptions, queryKey), queryFn: ({ signal }) => getApiPlayerByIdSkin({ ...clientOptions, signal, throwOnError: true }).then(response => response.data) });
/**
 * Armor names currently equipped by a player
 */
export const getApiPlayerByIdArmorsOptions = (clientOptions: Options<GetApiPlayerByIdArmorsData, true>, queryKey?: Array<unknown>) => queryOptions({ queryKey: Common.UseGetApiPlayerByIdArmorsKeyFn(clientOptions, queryKey), queryFn: ({ signal }) => getApiPlayerByIdArmors({ ...clientOptions, signal, throwOnError: true }).then(response => response.data) });
/**
 * Wielded weapon/tool names and dominant hand for a player
 */
export const getApiPlayerByIdHandsOptions = (clientOptions: Options<GetApiPlayerByIdHandsData, true>, queryKey?: Array<unknown>) => queryOptions({ queryKey: Common.UseGetApiPlayerByIdHandsKeyFn(clientOptions, queryKey), queryFn: ({ signal }) => getApiPlayerByIdHands({ ...clientOptions, signal, throwOnError: true }).then(response => response.data) });
/**
 * Armor/weapon/tool names owned by a player
 */
export const getApiPlayerByIdOwnedOptions = (clientOptions: Options<GetApiPlayerByIdOwnedData, true>, queryKey?: Array<unknown>) => queryOptions({ queryKey: Common.UseGetApiPlayerByIdOwnedKeyFn(clientOptions, queryKey), queryFn: ({ signal }) => getApiPlayerByIdOwned({ ...clientOptions, signal, throwOnError: true }).then(response => response.data) });
/**
 * A player's RPG character class
 */
export const getApiPlayerByIdRpgOptions = (clientOptions: Options<GetApiPlayerByIdRpgData, true>, queryKey?: Array<unknown>) => queryOptions({ queryKey: Common.UseGetApiPlayerByIdRpgKeyFn(clientOptions, queryKey), queryFn: ({ signal }) => getApiPlayerByIdRpg({ ...clientOptions, signal, throwOnError: true }).then(response => response.data) });
/**
 * Names of all available player skins
 */
export const getApiSkinsOptions = (clientOptions: Options<GetApiSkinsData, true> = {}, queryKey?: Array<unknown>) => queryOptions({ queryKey: Common.UseGetApiSkinsKeyFn(clientOptions, queryKey), queryFn: ({ signal }) => getApiSkins({ ...clientOptions, signal, throwOnError: true }).then(response => response.data) });
/**
 * Skin config (eye offset, hidden bones) for a named skin
 */
export const getApiSkinsByNameConfigOptions = (clientOptions: Options<GetApiSkinsByNameConfigData, true>, queryKey?: Array<unknown>) => queryOptions({ queryKey: Common.UseGetApiSkinsByNameConfigKeyFn(clientOptions, queryKey), queryFn: ({ signal }) => getApiSkinsByNameConfig({ ...clientOptions, signal, throwOnError: true }).then(response => response.data) });
/**
 * List all armor definitions
 */
export const getApiArmorsOptions = (clientOptions: Options<GetApiArmorsData, true> = {}, queryKey?: Array<unknown>) => queryOptions({ queryKey: Common.UseGetApiArmorsKeyFn(clientOptions, queryKey), queryFn: ({ signal }) => getApiArmors({ ...clientOptions, signal, throwOnError: true }).then(response => response.data) });
/**
 * List all weapon definitions
 */
export const getApiWeaponsOptions = (clientOptions: Options<GetApiWeaponsData, true> = {}, queryKey?: Array<unknown>) => queryOptions({ queryKey: Common.UseGetApiWeaponsKeyFn(clientOptions, queryKey), queryFn: ({ signal }) => getApiWeapons({ ...clientOptions, signal, throwOnError: true }).then(response => response.data) });
/**
 * List all tool definitions
 */
export const getApiToolsOptions = (clientOptions: Options<GetApiToolsData, true> = {}, queryKey?: Array<unknown>) => queryOptions({ queryKey: Common.UseGetApiToolsKeyFn(clientOptions, queryKey), queryFn: ({ signal }) => getApiTools({ ...clientOptions, signal, throwOnError: true }).then(response => response.data) });
/**
 * 3D game asset files discovered under resources/game-assets
 */
export const getApiGameAssetsOptions = (clientOptions: Options<GetApiGameAssetsData, true> = {}, queryKey?: Array<unknown>) => queryOptions({ queryKey: Common.UseGetApiGameAssetsKeyFn(clientOptions, queryKey), queryFn: ({ signal }) => getApiGameAssets({ ...clientOptions, signal, throwOnError: true }).then(response => response.data) });
/**
 * Raw asset file bytes (glb/gltf/fbx/textures)
 */
export const getApiGameAssetsFileByOptions = (clientOptions: Options<GetApiGameAssetsFileByData, true>, queryKey?: Array<unknown>) => queryOptions({ queryKey: Common.UseGetApiGameAssetsFileByKeyFn(clientOptions, queryKey), queryFn: ({ signal }) => getApiGameAssetsFileBy({ ...clientOptions, signal, throwOnError: true }).then(response => response.data) });
/**
 * Reads a .blend file's collection/object tree via headless Blender (cached)
 */
export const getApiGameAssetsBlendSceneByOptions = (clientOptions: Options<GetApiGameAssetsBlendSceneByData, true>, queryKey?: Array<unknown>) => queryOptions({ queryKey: Common.UseGetApiGameAssetsBlendSceneByKeyFn(clientOptions, queryKey), queryFn: ({ signal }) => getApiGameAssetsBlendSceneBy({ ...clientOptions, signal, throwOnError: true }).then(response => response.data) });
/**
 * Converts a .blend file to OBJ/MTL via headless Blender (cached) and returns the OBJ asset path
 */
export const getApiGameAssetsBlendPreviewByOptions = (clientOptions: Options<GetApiGameAssetsBlendPreviewByData, true>, queryKey?: Array<unknown>) => queryOptions({ queryKey: Common.UseGetApiGameAssetsBlendPreviewByKeyFn(clientOptions, queryKey), queryFn: ({ signal }) => getApiGameAssetsBlendPreviewBy({ ...clientOptions, signal, throwOnError: true }).then(response => response.data) });
/**
 * Converts an OBJ/MTL mesh into a Blockbench-compatible mesh .bbmodel (cached). The generated mesh elements are not rendered by the admin viewer — open the result in Blockbench to edit it.
 */
export const getApiGameAssetsBbmodelExportByOptions = (clientOptions: Options<GetApiGameAssetsBbmodelExportByData, true>, queryKey?: Array<unknown>) => queryOptions({ queryKey: Common.UseGetApiGameAssetsBbmodelExportByKeyFn(clientOptions, queryKey), queryFn: ({ signal }) => getApiGameAssetsBbmodelExportBy({ ...clientOptions, signal, throwOnError: true }).then(response => response.data) });
/**
 * All quest definitions
 */
export const getApiQuestsOptions = (clientOptions: Options<GetApiQuestsData, true> = {}, queryKey?: Array<unknown>) => queryOptions({ queryKey: Common.UseGetApiQuestsKeyFn(clientOptions, queryKey), queryFn: ({ signal }) => getApiQuests({ ...clientOptions, signal, throwOnError: true }).then(response => response.data) });
/**
 * Names of all known players
 */
export const getApiPlayersNamesOptions = (clientOptions: Options<GetApiPlayersNamesData, true> = {}, queryKey?: Array<unknown>) => queryOptions({ queryKey: Common.UseGetApiPlayersNamesKeyFn(clientOptions, queryKey), queryFn: ({ signal }) => getApiPlayersNames({ ...clientOptions, signal, throwOnError: true }).then(response => response.data) });
/**
 * Live players, NPCs and weather zones for the map overlay
 */
export const getApiMapStateOptions = (clientOptions: Options<GetApiMapStateData, true> = {}, queryKey?: Array<unknown>) => queryOptions({ queryKey: Common.UseGetApiMapStateKeyFn(clientOptions, queryKey), queryFn: ({ signal }) => getApiMapState({ ...clientOptions, signal, throwOnError: true }).then(response => response.data) });
/**
 * Cached terrain summary JSON for the map overlay
 */
export const getApiMapTerrainOptions = (clientOptions: Options<GetApiMapTerrainData, true> = {}, queryKey?: Array<unknown>) => queryOptions({ queryKey: Common.UseGetApiMapTerrainKeyFn(clientOptions, queryKey), queryFn: ({ signal }) => getApiMapTerrain({ ...clientOptions, signal, throwOnError: true }).then(response => response.data) });
/**
 * Voronoi biome cells around a point
 */
export const getApiMapVoronoiOptions = (clientOptions: Options<GetApiMapVoronoiData, true> = {}, queryKey?: Array<unknown>) => queryOptions({ queryKey: Common.UseGetApiMapVoronoiKeyFn(clientOptions, queryKey), queryFn: ({ signal }) => getApiMapVoronoi({ ...clientOptions, signal, throwOnError: true }).then(response => response.data) });
/**
 * Generated houses within an area
 */
export const getApiMapHousesOptions = (clientOptions: Options<GetApiMapHousesData, true> = {}, queryKey?: Array<unknown>) => queryOptions({ queryKey: Common.UseGetApiMapHousesKeyFn(clientOptions, queryKey), queryFn: ({ signal }) => getApiMapHouses({ ...clientOptions, signal, throwOnError: true }).then(response => response.data) });
/**
 * Named staircase points for the map overlay
 */
export const getApiMapStaircasesOptions = (clientOptions: Options<GetApiMapStaircasesData, true> = {}, queryKey?: Array<unknown>) => queryOptions({ queryKey: Common.UseGetApiMapStaircasesKeyFn(clientOptions, queryKey), queryFn: ({ signal }) => getApiMapStaircases({ ...clientOptions, signal, throwOnError: true }).then(response => response.data) });
/**
 * Road vertex segments within an area
 */
export const getApiMapRoadsOptions = (clientOptions: Options<GetApiMapRoadsData, true> = {}, queryKey?: Array<unknown>) => queryOptions({ queryKey: Common.UseGetApiMapRoadsKeyFn(clientOptions, queryKey), queryFn: ({ signal }) => getApiMapRoads({ ...clientOptions, signal, throwOnError: true }).then(response => response.data) });
/**
 * Per-chunk road bitmask within an area
 */
export const getApiMapRoadRasterOptions = (clientOptions: Options<GetApiMapRoadRasterData, true> = {}, queryKey?: Array<unknown>) => queryOptions({ queryKey: Common.UseGetApiMapRoadRasterKeyFn(clientOptions, queryKey), queryFn: ({ signal }) => getApiMapRoadRaster({ ...clientOptions, signal, throwOnError: true }).then(response => response.data) });
/**
 * Rasterized road overlay as a PNG image
 */
export const getApiMapRoadRasterPngOptions = (clientOptions: Options<GetApiMapRoadRasterPngData, true> = {}, queryKey?: Array<unknown>) => queryOptions({ queryKey: Common.UseGetApiMapRoadRasterPngKeyFn(clientOptions, queryKey), queryFn: ({ signal }) => getApiMapRoadRasterPng({ ...clientOptions, signal, throwOnError: true }).then(response => response.data) });
/**
 * Rasterized terrain overlay as a PNG image
 */
export const getApiMapTerrainRasterPngOptions = (clientOptions: Options<GetApiMapTerrainRasterPngData, true> = {}, queryKey?: Array<unknown>) => queryOptions({ queryKey: Common.UseGetApiMapTerrainRasterPngKeyFn(clientOptions, queryKey), queryFn: ({ signal }) => getApiMapTerrainRasterPng({ ...clientOptions, signal, throwOnError: true }).then(response => response.data) });
/**
 * Voronoi cell border segments within an area
 */
export const getApiMapVoronoiBordersOptions = (clientOptions: Options<GetApiMapVoronoiBordersData, true> = {}, queryKey?: Array<unknown>) => queryOptions({ queryKey: Common.UseGetApiMapVoronoiBordersKeyFn(clientOptions, queryKey), queryFn: ({ signal }) => getApiMapVoronoiBorders({ ...clientOptions, signal, throwOnError: true }).then(response => response.data) });
/**
 * Server status snapshot (TPS, players, chunks, heap, CPU)
 */
export const getApiAdminStatusOptions = (clientOptions: Options<GetApiAdminStatusData, true> = {}, queryKey?: Array<unknown>) => queryOptions({ queryKey: Common.UseGetApiAdminStatusKeyFn(clientOptions, queryKey), queryFn: ({ signal }) => getApiAdminStatus({ ...clientOptions, signal, throwOnError: true }).then(response => response.data) });
/**
 * All local/no-auth accounts
 */
export const getApiAdminUsersOptions = (clientOptions: Options<GetApiAdminUsersData, true> = {}, queryKey?: Array<unknown>) => queryOptions({ queryKey: Common.UseGetApiAdminUsersKeyFn(clientOptions, queryKey), queryFn: ({ signal }) => getApiAdminUsers({ ...clientOptions, signal, throwOnError: true }).then(response => response.data) });
/**
 * All player names
 */
export const getApiAdminPlayersOptions = (clientOptions: Options<GetApiAdminPlayersData, true> = {}, queryKey?: Array<unknown>) => queryOptions({ queryKey: Common.UseGetApiAdminPlayersKeyFn(clientOptions, queryKey), queryFn: ({ signal }) => getApiAdminPlayers({ ...clientOptions, signal, throwOnError: true }).then(response => response.data) });
/**
 * Full player file (state, keybindings, RPG data)
 */
export const getApiAdminPlayersByNameOptions = (clientOptions: Options<GetApiAdminPlayersByNameData, true>, queryKey?: Array<unknown>) => queryOptions({ queryKey: Common.UseGetApiAdminPlayersByNameKeyFn(clientOptions, queryKey), queryFn: ({ signal }) => getApiAdminPlayersByName({ ...clientOptions, signal, throwOnError: true }).then(response => response.data) });
/**
 * All worlds on disk, with stats
 */
export const getApiAdminWorldsOptions = (clientOptions: Options<GetApiAdminWorldsData, true> = {}, queryKey?: Array<unknown>) => queryOptions({ queryKey: Common.UseGetApiAdminWorldsKeyFn(clientOptions, queryKey), queryFn: ({ signal }) => getApiAdminWorlds({ ...clientOptions, signal, throwOnError: true }).then(response => response.data) });
/**
 * Names of all whitelisted editable YAML config files
 */
export const getApiAdminConfigsOptions = (clientOptions: Options<GetApiAdminConfigsData, true> = {}, queryKey?: Array<unknown>) => queryOptions({ queryKey: Common.UseGetApiAdminConfigsKeyFn(clientOptions, queryKey), queryFn: ({ signal }) => getApiAdminConfigs({ ...clientOptions, signal, throwOnError: true }).then(response => response.data) });
/**
 * Raw YAML content of a whitelisted config file
 */
export const getApiAdminConfigsByOptions = (clientOptions: Options<GetApiAdminConfigsByData, true>, queryKey?: Array<unknown>) => queryOptions({ queryKey: Common.UseGetApiAdminConfigsByKeyFn(clientOptions, queryKey), queryFn: ({ signal }) => getApiAdminConfigsBy({ ...clientOptions, signal, throwOnError: true }).then(response => response.data) });
/**
 * RPG class definitions, keyed by class name
 */
export const getApiAdminClassesOptions = (clientOptions: Options<GetApiAdminClassesData, true> = {}, queryKey?: Array<unknown>) => queryOptions({ queryKey: Common.UseGetApiAdminClassesKeyFn(clientOptions, queryKey), queryFn: ({ signal }) => getApiAdminClasses({ ...clientOptions, signal, throwOnError: true }).then(response => response.data) });
/**
 * All attack and spell ids
 */
export const getApiAdminSkillsOptions = (clientOptions: Options<GetApiAdminSkillsData, true> = {}, queryKey?: Array<unknown>) => queryOptions({ queryKey: Common.UseGetApiAdminSkillsKeyFn(clientOptions, queryKey), queryFn: ({ signal }) => getApiAdminSkills({ ...clientOptions, signal, throwOnError: true }).then(response => response.data) });
/**
 * Live NPC instances with full animal/combat state
 */
export const getApiAdminNpcsOptions = (clientOptions: Options<GetApiAdminNpcsData, true> = {}, queryKey?: Array<unknown>) => queryOptions({ queryKey: Common.UseGetApiAdminNpcsKeyFn(clientOptions, queryKey), queryFn: ({ signal }) => getApiAdminNpcs({ ...clientOptions, signal, throwOnError: true }).then(response => response.data) });
/**
 * All registered block definitions
 */
export const getApiAdminBlocksOptions = (clientOptions: Options<GetApiAdminBlocksData, true> = {}, queryKey?: Array<unknown>) => queryOptions({ queryKey: Common.UseGetApiAdminBlocksKeyFn(clientOptions, queryKey), queryFn: ({ signal }) => getApiAdminBlocks({ ...clientOptions, signal, throwOnError: true }).then(response => response.data) });
/**
 * All registered plain paint colors
 */
export const getApiAdminPlainColorsOptions = (clientOptions: Options<GetApiAdminPlainColorsData, true> = {}, queryKey?: Array<unknown>) => queryOptions({ queryKey: Common.UseGetApiAdminPlainColorsKeyFn(clientOptions, queryKey), queryFn: ({ signal }) => getApiAdminPlainColors({ ...clientOptions, signal, throwOnError: true }).then(response => response.data) });
/**
 * All chunk coordinates generated so far (in-memory ∪ persisted)
 */
export const getApiAdminChunksDiscoveredOptions = (clientOptions: Options<GetApiAdminChunksDiscoveredData, true> = {}, queryKey?: Array<unknown>) => queryOptions({ queryKey: Common.UseGetApiAdminChunksDiscoveredKeyFn(clientOptions, queryKey), queryFn: ({ signal }) => getApiAdminChunksDiscovered({ ...clientOptions, signal, throwOnError: true }).then(response => response.data) });
/**
 * All instance zones
 */
export const getApiAdminInstancesOptions = (clientOptions: Options<GetApiAdminInstancesData, true> = {}, queryKey?: Array<unknown>) => queryOptions({ queryKey: Common.UseGetApiAdminInstancesKeyFn(clientOptions, queryKey), queryFn: ({ signal }) => getApiAdminInstances({ ...clientOptions, signal, throwOnError: true }).then(response => response.data) });
/**
 * A single instance zone
 */
export const getApiAdminInstancesByIdOptions = (clientOptions: Options<GetApiAdminInstancesByIdData, true>, queryKey?: Array<unknown>) => queryOptions({ queryKey: Common.UseGetApiAdminInstancesByIdKeyFn(clientOptions, queryKey), queryFn: ({ signal }) => getApiAdminInstancesById({ ...clientOptions, signal, throwOnError: true }).then(response => response.data) });
/**
 * Non-air blocks in an instance zone, streamed as newline-delimited JSON (application/x-ndjson, one InstanceBlockDto per line), capped at 300000
 */
export const getApiAdminInstancesByIdBlocksOptions = (clientOptions: Options<GetApiAdminInstancesByIdBlocksData, true>, queryKey?: Array<unknown>) => queryOptions({ queryKey: Common.UseGetApiAdminInstancesByIdBlocksKeyFn(clientOptions, queryKey), queryFn: ({ signal }) => getApiAdminInstancesByIdBlocks({ ...clientOptions, signal, throwOnError: true }).then(response => response.data) });
/**
 * All scenes (bounded off-world block-structure buffers)
 */
export const getApiAdminScenesOptions = (clientOptions: Options<GetApiAdminScenesData, true> = {}, queryKey?: Array<unknown>) => queryOptions({ queryKey: Common.UseGetApiAdminScenesKeyFn(clientOptions, queryKey), queryFn: ({ signal }) => getApiAdminScenes({ ...clientOptions, signal, throwOnError: true }).then(response => response.data) });
/**
 * A single scene's metadata
 */
export const getApiAdminScenesByIdOptions = (clientOptions: Options<GetApiAdminScenesByIdData, true>, queryKey?: Array<unknown>) => queryOptions({ queryKey: Common.UseGetApiAdminScenesByIdKeyFn(clientOptions, queryKey), queryFn: ({ signal }) => getApiAdminScenesById({ ...clientOptions, signal, throwOnError: true }).then(response => response.data) });
/**
 * Scene block/state/extraState buffers as a binary blob: 3×4-byte big-endian dimensions (width,height,depth) followed by the blocks byte array, then the states byte array, then the extraStates byte array (wire-index-per-byte, 0 = AIR)
 */
export const getApiAdminScenesByIdBlocksRawOptions = (clientOptions: Options<GetApiAdminScenesByIdBlocksRawData, true>, queryKey?: Array<unknown>) => queryOptions({ queryKey: Common.UseGetApiAdminScenesByIdBlocksRawKeyFn(clientOptions, queryKey), queryFn: ({ signal }) => getApiAdminScenesByIdBlocksRaw({ ...clientOptions, signal, throwOnError: true }).then(response => response.data) });
/**
 * Fractional (lego/plate/arch) block entities placed in this scene — not carried by the blocks/raw binary blob, so the client loads them separately on scene open
 */
export const getApiAdminScenesByIdEntitiesOptions = (clientOptions: Options<GetApiAdminScenesByIdEntitiesData, true>, queryKey?: Array<unknown>) => queryOptions({ queryKey: Common.UseGetApiAdminScenesByIdEntitiesKeyFn(clientOptions, queryKey), queryFn: ({ signal }) => getApiAdminScenesByIdEntities({ ...clientOptions, signal, throwOnError: true }).then(response => response.data) });
/**
 * NPC type definitions (codex info), keyed by type id
 */
export const getApiAdminNpcTypesOptions = (clientOptions: Options<GetApiAdminNpcTypesData, true> = {}, queryKey?: Array<unknown>) => queryOptions({ queryKey: Common.UseGetApiAdminNpcTypesKeyFn(clientOptions, queryKey), queryFn: ({ signal }) => getApiAdminNpcTypes({ ...clientOptions, signal, throwOnError: true }).then(response => response.data) });
/**
 * Item definitions, keyed by item type id
 */
export const getApiAdminItemsOptions = (clientOptions: Options<GetApiAdminItemsData, true> = {}, queryKey?: Array<unknown>) => queryOptions({ queryKey: Common.UseGetApiAdminItemsKeyFn(clientOptions, queryKey), queryFn: ({ signal }) => getApiAdminItems({ ...clientOptions, signal, throwOnError: true }).then(response => response.data) });
/**
 * A JSON Schema file (data/config/schemas*.schema.json) for the config editor
 */
export const getApiAdminSchemasByFilenameOptions = (clientOptions: Options<GetApiAdminSchemasByFilenameData, true>, queryKey?: Array<unknown>) => queryOptions({ queryKey: Common.UseGetApiAdminSchemasByFilenameKeyFn(clientOptions, queryKey), queryFn: ({ signal }) => getApiAdminSchemasByFilename({ ...clientOptions, signal, throwOnError: true }).then(response => response.data) });
/**
 * Defaults the world simulator admin UI prefills its editors with
 */
export const getApiAdminSimulationDefaultsOptions = (clientOptions: Options<GetApiAdminSimulationDefaultsData, true> = {}, queryKey?: Array<unknown>) => queryOptions({ queryKey: Common.UseGetApiAdminSimulationDefaultsKeyFn(clientOptions, queryKey), queryFn: ({ signal }) => getApiAdminSimulationDefaults({ ...clientOptions, signal, throwOnError: true }).then(response => response.data) });
/**
 * Binary-encoded chunk data (protocol.ServerMessage.ChunkData wire format). Not a JSON API — used by the game client, not by TanStack Query hooks.
 */
export const getApiChunksByCxByCzOptions = (clientOptions: Options<GetApiChunksByCxByCzData, true>, queryKey?: Array<unknown>) => queryOptions({ queryKey: Common.UseGetApiChunksByCxByCzKeyFn(clientOptions, queryKey), queryFn: ({ signal }) => getApiChunksByCxByCz({ ...clientOptions, signal, throwOnError: true }).then(response => response.data) });
/**
 * Player characters (name + id) linked to an account email
 */
export const getApiPlayersByEmailByEmailOptions = (clientOptions: Options<GetApiPlayersByEmailByEmailData, true>, queryKey?: Array<unknown>) => queryOptions({ queryKey: Common.UseGetApiPlayersByEmailByEmailKeyFn(clientOptions, queryKey), queryFn: ({ signal }) => getApiPlayersByEmailByEmail({ ...clientOptions, signal, throwOnError: true }).then(response => response.data) });
