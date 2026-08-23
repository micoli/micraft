// generated with @7nohe/openapi-react-query-codegen@3.0.2 

import { type EnsureQueryDataOptions, type QueryClient } from "@tanstack/react-query";
import { getApiAdminBlocks, getApiAdminChunksDiscovered, getApiAdminClasses, getApiAdminConfigs, getApiAdminConfigsBy, getApiAdminInstances, getApiAdminInstancesById, getApiAdminInstancesByIdBlocks, getApiAdminItems, getApiAdminNpcTypes, getApiAdminNpcs, getApiAdminPlainColors, getApiAdminPlayers, getApiAdminPlayersByName, getApiAdminScenes, getApiAdminScenesById, getApiAdminScenesByIdBlocksRaw, getApiAdminScenesByIdEntities, getApiAdminSchemasByFilename, getApiAdminSimulationDefaults, getApiAdminSkills, getApiAdminStatus, getApiAdminUsers, getApiAdminWorlds, getApiAdminWsInstancesById, getApiAdminWsNpcs, getApiAdminWsScenesById, getApiAdminWsSimulation, getApiArmors, getApiAssetsManifest, getApiAttacks, getApiAuthConfig, getApiAutocompleteByCommandIdByArgIndex, getApiBiomes, getApiChunksByCxByCz, getApiClasses, getApiGameAssets, getApiGameAssetsBbmodelExportBy, getApiGameAssetsBlendPreviewBy, getApiGameAssetsBlendSceneBy, getApiGameAssetsFileBy, getApiI18nByLocale, getApiItemsMeta, getApiKeybindings, getApiLayoutRegistry, getApiMacrosContext, getApiMapHouses, getApiMapRoadRaster, getApiMapRoadRasterPng, getApiMapRoads, getApiMapStaircases, getApiMapState, getApiMapTerrain, getApiMapTerrainRasterPng, getApiMapVoronoi, getApiMapVoronoiBorders, getApiPlayerByIdArmors, getApiPlayerByIdHands, getApiPlayerByIdOwned, getApiPlayerByIdRpg, getApiPlayerByIdSkin, getApiPlayersByEmailByEmail, getApiPlayersNames, getApiQuests, getApiServerInfo, getApiSkins, getApiSkinsByNameConfig, getApiSpells, getApiTools, getApiWeapons, type Options } from "../requests/sdk.gen";
import type { GetApiAdminBlocksData, GetApiAdminChunksDiscoveredData, GetApiAdminClassesData, GetApiAdminConfigsByData, GetApiAdminConfigsData, GetApiAdminInstancesByIdBlocksData, GetApiAdminInstancesByIdData, GetApiAdminInstancesData, GetApiAdminItemsData, GetApiAdminNpcTypesData, GetApiAdminNpcsData, GetApiAdminPlainColorsData, GetApiAdminPlayersByNameData, GetApiAdminPlayersData, GetApiAdminScenesByIdBlocksRawData, GetApiAdminScenesByIdData, GetApiAdminScenesByIdEntitiesData, GetApiAdminScenesData, GetApiAdminSchemasByFilenameData, GetApiAdminSimulationDefaultsData, GetApiAdminSkillsData, GetApiAdminStatusData, GetApiAdminUsersData, GetApiAdminWorldsData, GetApiAdminWsInstancesByIdData, GetApiAdminWsNpcsData, GetApiAdminWsScenesByIdData, GetApiAdminWsSimulationData, GetApiArmorsData, GetApiAssetsManifestData, GetApiAttacksData, GetApiAuthConfigData, GetApiAutocompleteByCommandIdByArgIndexData, GetApiBiomesData, GetApiChunksByCxByCzData, GetApiClassesData, GetApiGameAssetsBbmodelExportByData, GetApiGameAssetsBlendPreviewByData, GetApiGameAssetsBlendSceneByData, GetApiGameAssetsData, GetApiGameAssetsFileByData, GetApiI18nByLocaleData, GetApiItemsMetaData, GetApiKeybindingsData, GetApiLayoutRegistryData, GetApiMacrosContextData, GetApiMapHousesData, GetApiMapRoadRasterData, GetApiMapRoadRasterPngData, GetApiMapRoadsData, GetApiMapStaircasesData, GetApiMapStateData, GetApiMapTerrainData, GetApiMapTerrainRasterPngData, GetApiMapVoronoiBordersData, GetApiMapVoronoiData, GetApiPlayerByIdArmorsData, GetApiPlayerByIdHandsData, GetApiPlayerByIdOwnedData, GetApiPlayerByIdRpgData, GetApiPlayerByIdSkinData, GetApiPlayersByEmailByEmailData, GetApiPlayersNamesData, GetApiQuestsData, GetApiServerInfoData, GetApiSkinsByNameConfigData, GetApiSkinsData, GetApiSpellsData, GetApiToolsData, GetApiWeaponsData } from "../requests/types.gen";
import * as Common from "./common";

/**
 * Active auth provider, used by the client to pick the right login UI
 */
export const ensureUseGetApiAuthConfigData = (queryClient: QueryClient, clientOptions: Options<GetApiAuthConfigData, true> = {}, options?: Omit<EnsureQueryDataOptions<Common.GetApiAuthConfigDefaultResponse>, "queryKey" | "queryFn">) => queryClient.ensureQueryData({ queryKey: Common.UseGetApiAuthConfigKeyFn(clientOptions), queryFn: ({ signal }) => getApiAuthConfig({ ...clientOptions, signal, throwOnError: true }).then(response => response.data), ...options });
export const ensureUseGetApiAssetsManifestData = (queryClient: QueryClient, clientOptions: Options<GetApiAssetsManifestData, true> = {}, options?: Omit<EnsureQueryDataOptions<Common.GetApiAssetsManifestDefaultResponse>, "queryKey" | "queryFn">) => queryClient.ensureQueryData({ queryKey: Common.UseGetApiAssetsManifestKeyFn(clientOptions), queryFn: ({ signal }) => getApiAssetsManifest({ ...clientOptions, signal, throwOnError: true }).then(response => response.data), ...options });
export const ensureUseGetApiAdminWsNpcsData = (queryClient: QueryClient, clientOptions: Options<GetApiAdminWsNpcsData, true> = {}, options?: Omit<EnsureQueryDataOptions<Common.GetApiAdminWsNpcsDefaultResponse>, "queryKey" | "queryFn">) => queryClient.ensureQueryData({ queryKey: Common.UseGetApiAdminWsNpcsKeyFn(clientOptions), queryFn: ({ signal }) => getApiAdminWsNpcs({ ...clientOptions, signal, throwOnError: true }).then(response => response.data), ...options });
export const ensureUseGetApiAdminWsScenesByIdData = (queryClient: QueryClient, clientOptions: Options<GetApiAdminWsScenesByIdData, true> = {}, options?: Omit<EnsureQueryDataOptions<Common.GetApiAdminWsScenesByIdDefaultResponse>, "queryKey" | "queryFn">) => queryClient.ensureQueryData({ queryKey: Common.UseGetApiAdminWsScenesByIdKeyFn(clientOptions), queryFn: ({ signal }) => getApiAdminWsScenesById({ ...clientOptions, signal, throwOnError: true }).then(response => response.data), ...options });
export const ensureUseGetApiAdminWsInstancesByIdData = (queryClient: QueryClient, clientOptions: Options<GetApiAdminWsInstancesByIdData, true> = {}, options?: Omit<EnsureQueryDataOptions<Common.GetApiAdminWsInstancesByIdDefaultResponse>, "queryKey" | "queryFn">) => queryClient.ensureQueryData({ queryKey: Common.UseGetApiAdminWsInstancesByIdKeyFn(clientOptions), queryFn: ({ signal }) => getApiAdminWsInstancesById({ ...clientOptions, signal, throwOnError: true }).then(response => response.data), ...options });
export const ensureUseGetApiAdminWsSimulationData = (queryClient: QueryClient, clientOptions: Options<GetApiAdminWsSimulationData, true> = {}, options?: Omit<EnsureQueryDataOptions<Common.GetApiAdminWsSimulationDefaultResponse>, "queryKey" | "queryFn">) => queryClient.ensureQueryData({ queryKey: Common.UseGetApiAdminWsSimulationKeyFn(clientOptions), queryFn: ({ signal }) => getApiAdminWsSimulation({ ...clientOptions, signal, throwOnError: true }).then(response => response.data), ...options });
/**
 * Key bindings — a player's saved bindings if ?player= is given and persistence is available, otherwise the default config
 */
export const ensureUseGetApiKeybindingsData = (queryClient: QueryClient, clientOptions: Options<GetApiKeybindingsData, true> = {}, options?: Omit<EnsureQueryDataOptions<Common.GetApiKeybindingsDefaultResponse>, "queryKey" | "queryFn">) => queryClient.ensureQueryData({ queryKey: Common.UseGetApiKeybindingsKeyFn(clientOptions), queryFn: ({ signal }) => getApiKeybindings({ ...clientOptions, signal, throwOnError: true }).then(response => response.data), ...options });
/**
 * Autocomplete suggestions for a slash command argument
 */
export const ensureUseGetApiAutocompleteByCommandIdByArgIndexData = (queryClient: QueryClient, clientOptions: Options<GetApiAutocompleteByCommandIdByArgIndexData, true>, options?: Omit<EnsureQueryDataOptions<Common.GetApiAutocompleteByCommandIdByArgIndexDefaultResponse>, "queryKey" | "queryFn">) => queryClient.ensureQueryData({ queryKey: Common.UseGetApiAutocompleteByCommandIdByArgIndexKeyFn(clientOptions), queryFn: ({ signal }) => getApiAutocompleteByCommandIdByArgIndex({ ...clientOptions, signal, throwOnError: true }).then(response => response.data), ...options });
/**
 * Client-facing translation keys for a locale
 */
export const ensureUseGetApiI18nByLocaleData = (queryClient: QueryClient, clientOptions: Options<GetApiI18nByLocaleData, true>, options?: Omit<EnsureQueryDataOptions<Common.GetApiI18nByLocaleDefaultResponse>, "queryKey" | "queryFn">) => queryClient.ensureQueryData({ queryKey: Common.UseGetApiI18nByLocaleKeyFn(clientOptions), queryFn: ({ signal }) => getApiI18nByLocale({ ...clientOptions, signal, throwOnError: true }).then(response => response.data), ...options });
/**
 * All widgets registered for the UI layout editor
 */
export const ensureUseGetApiLayoutRegistryData = (queryClient: QueryClient, clientOptions: Options<GetApiLayoutRegistryData, true> = {}, options?: Omit<EnsureQueryDataOptions<Common.GetApiLayoutRegistryDefaultResponse>, "queryKey" | "queryFn">) => queryClient.ensureQueryData({ queryKey: Common.UseGetApiLayoutRegistryKeyFn(clientOptions), queryFn: ({ signal }) => getApiLayoutRegistry({ ...clientOptions, signal, throwOnError: true }).then(response => response.data), ...options });
/**
 * Item metadata (label, background color, consumable flags) by item type id
 */
export const ensureUseGetApiItemsMetaData = (queryClient: QueryClient, clientOptions: Options<GetApiItemsMetaData, true> = {}, options?: Omit<EnsureQueryDataOptions<Common.GetApiItemsMetaDefaultResponse>, "queryKey" | "queryFn">) => queryClient.ensureQueryData({ queryKey: Common.UseGetApiItemsMetaKeyFn(clientOptions), queryFn: ({ signal }) => getApiItemsMeta({ ...clientOptions, signal, throwOnError: true }).then(response => response.data), ...options });
/**
 * Server build timestamp
 */
export const ensureUseGetApiServerInfoData = (queryClient: QueryClient, clientOptions: Options<GetApiServerInfoData, true> = {}, options?: Omit<EnsureQueryDataOptions<Common.GetApiServerInfoDefaultResponse>, "queryKey" | "queryFn">) => queryClient.ensureQueryData({ queryKey: Common.UseGetApiServerInfoKeyFn(clientOptions), queryFn: ({ signal }) => getApiServerInfo({ ...clientOptions, signal, throwOnError: true }).then(response => response.data), ...options });
/**
 * Attack definitions, flattened by "attackId:level" key
 */
export const ensureUseGetApiAttacksData = (queryClient: QueryClient, clientOptions: Options<GetApiAttacksData, true> = {}, options?: Omit<EnsureQueryDataOptions<Common.GetApiAttacksDefaultResponse>, "queryKey" | "queryFn">) => queryClient.ensureQueryData({ queryKey: Common.UseGetApiAttacksKeyFn(clientOptions), queryFn: ({ signal }) => getApiAttacks({ ...clientOptions, signal, throwOnError: true }).then(response => response.data), ...options });
/**
 * Attack ids accessible per RPG class, keyed by level
 */
export const ensureUseGetApiClassesData = (queryClient: QueryClient, clientOptions: Options<GetApiClassesData, true> = {}, options?: Omit<EnsureQueryDataOptions<Common.GetApiClassesDefaultResponse>, "queryKey" | "queryFn">) => queryClient.ensureQueryData({ queryKey: Common.UseGetApiClassesKeyFn(clientOptions), queryFn: ({ signal }) => getApiClasses({ ...clientOptions, signal, throwOnError: true }).then(response => response.data), ...options });
/**
 * Spell definitions, keyed by spell id
 */
export const ensureUseGetApiSpellsData = (queryClient: QueryClient, clientOptions: Options<GetApiSpellsData, true> = {}, options?: Omit<EnsureQueryDataOptions<Common.GetApiSpellsDefaultResponse>, "queryKey" | "queryFn">) => queryClient.ensureQueryData({ queryKey: Common.UseGetApiSpellsKeyFn(clientOptions), queryFn: ({ signal }) => getApiSpells({ ...clientOptions, signal, throwOnError: true }).then(response => response.data), ...options });
/**
 * Variables available to the macro JEXL evaluation context
 */
export const ensureUseGetApiMacrosContextData = (queryClient: QueryClient, clientOptions: Options<GetApiMacrosContextData, true> = {}, options?: Omit<EnsureQueryDataOptions<Common.GetApiMacrosContextDefaultResponse>, "queryKey" | "queryFn">) => queryClient.ensureQueryData({ queryKey: Common.UseGetApiMacrosContextKeyFn(clientOptions), queryFn: ({ signal }) => getApiMacrosContext({ ...clientOptions, signal, throwOnError: true }).then(response => response.data), ...options });
/**
 * Grass color per biome id, as [r, g, b] in 0..1
 */
export const ensureUseGetApiBiomesData = (queryClient: QueryClient, clientOptions: Options<GetApiBiomesData, true> = {}, options?: Omit<EnsureQueryDataOptions<Common.GetApiBiomesDefaultResponse>, "queryKey" | "queryFn">) => queryClient.ensureQueryData({ queryKey: Common.UseGetApiBiomesKeyFn(clientOptions), queryFn: ({ signal }) => getApiBiomes({ ...clientOptions, signal, throwOnError: true }).then(response => response.data), ...options });
/**
 * A player's current skin
 */
export const ensureUseGetApiPlayerByIdSkinData = (queryClient: QueryClient, clientOptions: Options<GetApiPlayerByIdSkinData, true>, options?: Omit<EnsureQueryDataOptions<Common.GetApiPlayerByIdSkinDefaultResponse>, "queryKey" | "queryFn">) => queryClient.ensureQueryData({ queryKey: Common.UseGetApiPlayerByIdSkinKeyFn(clientOptions), queryFn: ({ signal }) => getApiPlayerByIdSkin({ ...clientOptions, signal, throwOnError: true }).then(response => response.data), ...options });
/**
 * Armor names currently equipped by a player
 */
export const ensureUseGetApiPlayerByIdArmorsData = (queryClient: QueryClient, clientOptions: Options<GetApiPlayerByIdArmorsData, true>, options?: Omit<EnsureQueryDataOptions<Common.GetApiPlayerByIdArmorsDefaultResponse>, "queryKey" | "queryFn">) => queryClient.ensureQueryData({ queryKey: Common.UseGetApiPlayerByIdArmorsKeyFn(clientOptions), queryFn: ({ signal }) => getApiPlayerByIdArmors({ ...clientOptions, signal, throwOnError: true }).then(response => response.data), ...options });
/**
 * Wielded weapon/tool names and dominant hand for a player
 */
export const ensureUseGetApiPlayerByIdHandsData = (queryClient: QueryClient, clientOptions: Options<GetApiPlayerByIdHandsData, true>, options?: Omit<EnsureQueryDataOptions<Common.GetApiPlayerByIdHandsDefaultResponse>, "queryKey" | "queryFn">) => queryClient.ensureQueryData({ queryKey: Common.UseGetApiPlayerByIdHandsKeyFn(clientOptions), queryFn: ({ signal }) => getApiPlayerByIdHands({ ...clientOptions, signal, throwOnError: true }).then(response => response.data), ...options });
/**
 * Armor/weapon/tool names owned by a player
 */
export const ensureUseGetApiPlayerByIdOwnedData = (queryClient: QueryClient, clientOptions: Options<GetApiPlayerByIdOwnedData, true>, options?: Omit<EnsureQueryDataOptions<Common.GetApiPlayerByIdOwnedDefaultResponse>, "queryKey" | "queryFn">) => queryClient.ensureQueryData({ queryKey: Common.UseGetApiPlayerByIdOwnedKeyFn(clientOptions), queryFn: ({ signal }) => getApiPlayerByIdOwned({ ...clientOptions, signal, throwOnError: true }).then(response => response.data), ...options });
/**
 * A player's RPG character class
 */
export const ensureUseGetApiPlayerByIdRpgData = (queryClient: QueryClient, clientOptions: Options<GetApiPlayerByIdRpgData, true>, options?: Omit<EnsureQueryDataOptions<Common.GetApiPlayerByIdRpgDefaultResponse>, "queryKey" | "queryFn">) => queryClient.ensureQueryData({ queryKey: Common.UseGetApiPlayerByIdRpgKeyFn(clientOptions), queryFn: ({ signal }) => getApiPlayerByIdRpg({ ...clientOptions, signal, throwOnError: true }).then(response => response.data), ...options });
/**
 * Names of all available player skins
 */
export const ensureUseGetApiSkinsData = (queryClient: QueryClient, clientOptions: Options<GetApiSkinsData, true> = {}, options?: Omit<EnsureQueryDataOptions<Common.GetApiSkinsDefaultResponse>, "queryKey" | "queryFn">) => queryClient.ensureQueryData({ queryKey: Common.UseGetApiSkinsKeyFn(clientOptions), queryFn: ({ signal }) => getApiSkins({ ...clientOptions, signal, throwOnError: true }).then(response => response.data), ...options });
/**
 * Skin config (eye offset, hidden bones) for a named skin
 */
export const ensureUseGetApiSkinsByNameConfigData = (queryClient: QueryClient, clientOptions: Options<GetApiSkinsByNameConfigData, true>, options?: Omit<EnsureQueryDataOptions<Common.GetApiSkinsByNameConfigDefaultResponse>, "queryKey" | "queryFn">) => queryClient.ensureQueryData({ queryKey: Common.UseGetApiSkinsByNameConfigKeyFn(clientOptions), queryFn: ({ signal }) => getApiSkinsByNameConfig({ ...clientOptions, signal, throwOnError: true }).then(response => response.data), ...options });
/**
 * List all armor definitions
 */
export const ensureUseGetApiArmorsData = (queryClient: QueryClient, clientOptions: Options<GetApiArmorsData, true> = {}, options?: Omit<EnsureQueryDataOptions<Common.GetApiArmorsDefaultResponse>, "queryKey" | "queryFn">) => queryClient.ensureQueryData({ queryKey: Common.UseGetApiArmorsKeyFn(clientOptions), queryFn: ({ signal }) => getApiArmors({ ...clientOptions, signal, throwOnError: true }).then(response => response.data), ...options });
/**
 * List all weapon definitions
 */
export const ensureUseGetApiWeaponsData = (queryClient: QueryClient, clientOptions: Options<GetApiWeaponsData, true> = {}, options?: Omit<EnsureQueryDataOptions<Common.GetApiWeaponsDefaultResponse>, "queryKey" | "queryFn">) => queryClient.ensureQueryData({ queryKey: Common.UseGetApiWeaponsKeyFn(clientOptions), queryFn: ({ signal }) => getApiWeapons({ ...clientOptions, signal, throwOnError: true }).then(response => response.data), ...options });
/**
 * List all tool definitions
 */
export const ensureUseGetApiToolsData = (queryClient: QueryClient, clientOptions: Options<GetApiToolsData, true> = {}, options?: Omit<EnsureQueryDataOptions<Common.GetApiToolsDefaultResponse>, "queryKey" | "queryFn">) => queryClient.ensureQueryData({ queryKey: Common.UseGetApiToolsKeyFn(clientOptions), queryFn: ({ signal }) => getApiTools({ ...clientOptions, signal, throwOnError: true }).then(response => response.data), ...options });
/**
 * 3D game asset files discovered under resources/game-assets
 */
export const ensureUseGetApiGameAssetsData = (queryClient: QueryClient, clientOptions: Options<GetApiGameAssetsData, true> = {}, options?: Omit<EnsureQueryDataOptions<Common.GetApiGameAssetsDefaultResponse>, "queryKey" | "queryFn">) => queryClient.ensureQueryData({ queryKey: Common.UseGetApiGameAssetsKeyFn(clientOptions), queryFn: ({ signal }) => getApiGameAssets({ ...clientOptions, signal, throwOnError: true }).then(response => response.data), ...options });
/**
 * Raw asset file bytes (glb/gltf/fbx/textures)
 */
export const ensureUseGetApiGameAssetsFileByData = (queryClient: QueryClient, clientOptions: Options<GetApiGameAssetsFileByData, true>, options?: Omit<EnsureQueryDataOptions<Common.GetApiGameAssetsFileByDefaultResponse>, "queryKey" | "queryFn">) => queryClient.ensureQueryData({ queryKey: Common.UseGetApiGameAssetsFileByKeyFn(clientOptions), queryFn: ({ signal }) => getApiGameAssetsFileBy({ ...clientOptions, signal, throwOnError: true }).then(response => response.data), ...options });
/**
 * Reads a .blend file's collection/object tree via headless Blender (cached)
 */
export const ensureUseGetApiGameAssetsBlendSceneByData = (queryClient: QueryClient, clientOptions: Options<GetApiGameAssetsBlendSceneByData, true>, options?: Omit<EnsureQueryDataOptions<Common.GetApiGameAssetsBlendSceneByDefaultResponse>, "queryKey" | "queryFn">) => queryClient.ensureQueryData({ queryKey: Common.UseGetApiGameAssetsBlendSceneByKeyFn(clientOptions), queryFn: ({ signal }) => getApiGameAssetsBlendSceneBy({ ...clientOptions, signal, throwOnError: true }).then(response => response.data), ...options });
/**
 * Converts a .blend file to OBJ/MTL via headless Blender (cached) and returns the OBJ asset path
 */
export const ensureUseGetApiGameAssetsBlendPreviewByData = (queryClient: QueryClient, clientOptions: Options<GetApiGameAssetsBlendPreviewByData, true>, options?: Omit<EnsureQueryDataOptions<Common.GetApiGameAssetsBlendPreviewByDefaultResponse>, "queryKey" | "queryFn">) => queryClient.ensureQueryData({ queryKey: Common.UseGetApiGameAssetsBlendPreviewByKeyFn(clientOptions), queryFn: ({ signal }) => getApiGameAssetsBlendPreviewBy({ ...clientOptions, signal, throwOnError: true }).then(response => response.data), ...options });
/**
 * Converts an OBJ/MTL mesh into a Blockbench-compatible mesh .bbmodel (cached). The generated mesh elements are not rendered by the admin viewer — open the result in Blockbench to edit it.
 */
export const ensureUseGetApiGameAssetsBbmodelExportByData = (queryClient: QueryClient, clientOptions: Options<GetApiGameAssetsBbmodelExportByData, true>, options?: Omit<EnsureQueryDataOptions<Common.GetApiGameAssetsBbmodelExportByDefaultResponse>, "queryKey" | "queryFn">) => queryClient.ensureQueryData({ queryKey: Common.UseGetApiGameAssetsBbmodelExportByKeyFn(clientOptions), queryFn: ({ signal }) => getApiGameAssetsBbmodelExportBy({ ...clientOptions, signal, throwOnError: true }).then(response => response.data), ...options });
/**
 * All quest definitions
 */
export const ensureUseGetApiQuestsData = (queryClient: QueryClient, clientOptions: Options<GetApiQuestsData, true> = {}, options?: Omit<EnsureQueryDataOptions<Common.GetApiQuestsDefaultResponse>, "queryKey" | "queryFn">) => queryClient.ensureQueryData({ queryKey: Common.UseGetApiQuestsKeyFn(clientOptions), queryFn: ({ signal }) => getApiQuests({ ...clientOptions, signal, throwOnError: true }).then(response => response.data), ...options });
/**
 * Names of all known players
 */
export const ensureUseGetApiPlayersNamesData = (queryClient: QueryClient, clientOptions: Options<GetApiPlayersNamesData, true> = {}, options?: Omit<EnsureQueryDataOptions<Common.GetApiPlayersNamesDefaultResponse>, "queryKey" | "queryFn">) => queryClient.ensureQueryData({ queryKey: Common.UseGetApiPlayersNamesKeyFn(clientOptions), queryFn: ({ signal }) => getApiPlayersNames({ ...clientOptions, signal, throwOnError: true }).then(response => response.data), ...options });
/**
 * Live players, NPCs and weather zones for the map overlay
 */
export const ensureUseGetApiMapStateData = (queryClient: QueryClient, clientOptions: Options<GetApiMapStateData, true> = {}, options?: Omit<EnsureQueryDataOptions<Common.GetApiMapStateDefaultResponse>, "queryKey" | "queryFn">) => queryClient.ensureQueryData({ queryKey: Common.UseGetApiMapStateKeyFn(clientOptions), queryFn: ({ signal }) => getApiMapState({ ...clientOptions, signal, throwOnError: true }).then(response => response.data), ...options });
/**
 * Cached terrain summary JSON for the map overlay
 */
export const ensureUseGetApiMapTerrainData = (queryClient: QueryClient, clientOptions: Options<GetApiMapTerrainData, true> = {}, options?: Omit<EnsureQueryDataOptions<Common.GetApiMapTerrainDefaultResponse>, "queryKey" | "queryFn">) => queryClient.ensureQueryData({ queryKey: Common.UseGetApiMapTerrainKeyFn(clientOptions), queryFn: ({ signal }) => getApiMapTerrain({ ...clientOptions, signal, throwOnError: true }).then(response => response.data), ...options });
/**
 * Voronoi biome cells around a point
 */
export const ensureUseGetApiMapVoronoiData = (queryClient: QueryClient, clientOptions: Options<GetApiMapVoronoiData, true> = {}, options?: Omit<EnsureQueryDataOptions<Common.GetApiMapVoronoiDefaultResponse>, "queryKey" | "queryFn">) => queryClient.ensureQueryData({ queryKey: Common.UseGetApiMapVoronoiKeyFn(clientOptions), queryFn: ({ signal }) => getApiMapVoronoi({ ...clientOptions, signal, throwOnError: true }).then(response => response.data), ...options });
/**
 * Generated houses within an area
 */
export const ensureUseGetApiMapHousesData = (queryClient: QueryClient, clientOptions: Options<GetApiMapHousesData, true> = {}, options?: Omit<EnsureQueryDataOptions<Common.GetApiMapHousesDefaultResponse>, "queryKey" | "queryFn">) => queryClient.ensureQueryData({ queryKey: Common.UseGetApiMapHousesKeyFn(clientOptions), queryFn: ({ signal }) => getApiMapHouses({ ...clientOptions, signal, throwOnError: true }).then(response => response.data), ...options });
/**
 * Named staircase points for the map overlay
 */
export const ensureUseGetApiMapStaircasesData = (queryClient: QueryClient, clientOptions: Options<GetApiMapStaircasesData, true> = {}, options?: Omit<EnsureQueryDataOptions<Common.GetApiMapStaircasesDefaultResponse>, "queryKey" | "queryFn">) => queryClient.ensureQueryData({ queryKey: Common.UseGetApiMapStaircasesKeyFn(clientOptions), queryFn: ({ signal }) => getApiMapStaircases({ ...clientOptions, signal, throwOnError: true }).then(response => response.data), ...options });
/**
 * Road vertex segments within an area
 */
export const ensureUseGetApiMapRoadsData = (queryClient: QueryClient, clientOptions: Options<GetApiMapRoadsData, true> = {}, options?: Omit<EnsureQueryDataOptions<Common.GetApiMapRoadsDefaultResponse>, "queryKey" | "queryFn">) => queryClient.ensureQueryData({ queryKey: Common.UseGetApiMapRoadsKeyFn(clientOptions), queryFn: ({ signal }) => getApiMapRoads({ ...clientOptions, signal, throwOnError: true }).then(response => response.data), ...options });
/**
 * Per-chunk road bitmask within an area
 */
export const ensureUseGetApiMapRoadRasterData = (queryClient: QueryClient, clientOptions: Options<GetApiMapRoadRasterData, true> = {}, options?: Omit<EnsureQueryDataOptions<Common.GetApiMapRoadRasterDefaultResponse>, "queryKey" | "queryFn">) => queryClient.ensureQueryData({ queryKey: Common.UseGetApiMapRoadRasterKeyFn(clientOptions), queryFn: ({ signal }) => getApiMapRoadRaster({ ...clientOptions, signal, throwOnError: true }).then(response => response.data), ...options });
/**
 * Rasterized road overlay as a PNG image
 */
export const ensureUseGetApiMapRoadRasterPngData = (queryClient: QueryClient, clientOptions: Options<GetApiMapRoadRasterPngData, true> = {}, options?: Omit<EnsureQueryDataOptions<Common.GetApiMapRoadRasterPngDefaultResponse>, "queryKey" | "queryFn">) => queryClient.ensureQueryData({ queryKey: Common.UseGetApiMapRoadRasterPngKeyFn(clientOptions), queryFn: ({ signal }) => getApiMapRoadRasterPng({ ...clientOptions, signal, throwOnError: true }).then(response => response.data), ...options });
/**
 * Rasterized terrain overlay as a PNG image
 */
export const ensureUseGetApiMapTerrainRasterPngData = (queryClient: QueryClient, clientOptions: Options<GetApiMapTerrainRasterPngData, true> = {}, options?: Omit<EnsureQueryDataOptions<Common.GetApiMapTerrainRasterPngDefaultResponse>, "queryKey" | "queryFn">) => queryClient.ensureQueryData({ queryKey: Common.UseGetApiMapTerrainRasterPngKeyFn(clientOptions), queryFn: ({ signal }) => getApiMapTerrainRasterPng({ ...clientOptions, signal, throwOnError: true }).then(response => response.data), ...options });
/**
 * Voronoi cell border segments within an area
 */
export const ensureUseGetApiMapVoronoiBordersData = (queryClient: QueryClient, clientOptions: Options<GetApiMapVoronoiBordersData, true> = {}, options?: Omit<EnsureQueryDataOptions<Common.GetApiMapVoronoiBordersDefaultResponse>, "queryKey" | "queryFn">) => queryClient.ensureQueryData({ queryKey: Common.UseGetApiMapVoronoiBordersKeyFn(clientOptions), queryFn: ({ signal }) => getApiMapVoronoiBorders({ ...clientOptions, signal, throwOnError: true }).then(response => response.data), ...options });
/**
 * Server status snapshot (TPS, players, chunks, heap, CPU)
 */
export const ensureUseGetApiAdminStatusData = (queryClient: QueryClient, clientOptions: Options<GetApiAdminStatusData, true> = {}, options?: Omit<EnsureQueryDataOptions<Common.GetApiAdminStatusDefaultResponse>, "queryKey" | "queryFn">) => queryClient.ensureQueryData({ queryKey: Common.UseGetApiAdminStatusKeyFn(clientOptions), queryFn: ({ signal }) => getApiAdminStatus({ ...clientOptions, signal, throwOnError: true }).then(response => response.data), ...options });
/**
 * All local/no-auth accounts
 */
export const ensureUseGetApiAdminUsersData = (queryClient: QueryClient, clientOptions: Options<GetApiAdminUsersData, true> = {}, options?: Omit<EnsureQueryDataOptions<Common.GetApiAdminUsersDefaultResponse>, "queryKey" | "queryFn">) => queryClient.ensureQueryData({ queryKey: Common.UseGetApiAdminUsersKeyFn(clientOptions), queryFn: ({ signal }) => getApiAdminUsers({ ...clientOptions, signal, throwOnError: true }).then(response => response.data), ...options });
/**
 * All player names
 */
export const ensureUseGetApiAdminPlayersData = (queryClient: QueryClient, clientOptions: Options<GetApiAdminPlayersData, true> = {}, options?: Omit<EnsureQueryDataOptions<Common.GetApiAdminPlayersDefaultResponse>, "queryKey" | "queryFn">) => queryClient.ensureQueryData({ queryKey: Common.UseGetApiAdminPlayersKeyFn(clientOptions), queryFn: ({ signal }) => getApiAdminPlayers({ ...clientOptions, signal, throwOnError: true }).then(response => response.data), ...options });
/**
 * Full player file (state, keybindings, RPG data)
 */
export const ensureUseGetApiAdminPlayersByNameData = (queryClient: QueryClient, clientOptions: Options<GetApiAdminPlayersByNameData, true>, options?: Omit<EnsureQueryDataOptions<Common.GetApiAdminPlayersByNameDefaultResponse>, "queryKey" | "queryFn">) => queryClient.ensureQueryData({ queryKey: Common.UseGetApiAdminPlayersByNameKeyFn(clientOptions), queryFn: ({ signal }) => getApiAdminPlayersByName({ ...clientOptions, signal, throwOnError: true }).then(response => response.data), ...options });
/**
 * All worlds on disk, with stats
 */
export const ensureUseGetApiAdminWorldsData = (queryClient: QueryClient, clientOptions: Options<GetApiAdminWorldsData, true> = {}, options?: Omit<EnsureQueryDataOptions<Common.GetApiAdminWorldsDefaultResponse>, "queryKey" | "queryFn">) => queryClient.ensureQueryData({ queryKey: Common.UseGetApiAdminWorldsKeyFn(clientOptions), queryFn: ({ signal }) => getApiAdminWorlds({ ...clientOptions, signal, throwOnError: true }).then(response => response.data), ...options });
/**
 * Names of all whitelisted editable YAML config files
 */
export const ensureUseGetApiAdminConfigsData = (queryClient: QueryClient, clientOptions: Options<GetApiAdminConfigsData, true> = {}, options?: Omit<EnsureQueryDataOptions<Common.GetApiAdminConfigsDefaultResponse>, "queryKey" | "queryFn">) => queryClient.ensureQueryData({ queryKey: Common.UseGetApiAdminConfigsKeyFn(clientOptions), queryFn: ({ signal }) => getApiAdminConfigs({ ...clientOptions, signal, throwOnError: true }).then(response => response.data), ...options });
/**
 * Raw YAML content of a whitelisted config file
 */
export const ensureUseGetApiAdminConfigsByData = (queryClient: QueryClient, clientOptions: Options<GetApiAdminConfigsByData, true>, options?: Omit<EnsureQueryDataOptions<Common.GetApiAdminConfigsByDefaultResponse>, "queryKey" | "queryFn">) => queryClient.ensureQueryData({ queryKey: Common.UseGetApiAdminConfigsByKeyFn(clientOptions), queryFn: ({ signal }) => getApiAdminConfigsBy({ ...clientOptions, signal, throwOnError: true }).then(response => response.data), ...options });
/**
 * RPG class definitions, keyed by class name
 */
export const ensureUseGetApiAdminClassesData = (queryClient: QueryClient, clientOptions: Options<GetApiAdminClassesData, true> = {}, options?: Omit<EnsureQueryDataOptions<Common.GetApiAdminClassesDefaultResponse>, "queryKey" | "queryFn">) => queryClient.ensureQueryData({ queryKey: Common.UseGetApiAdminClassesKeyFn(clientOptions), queryFn: ({ signal }) => getApiAdminClasses({ ...clientOptions, signal, throwOnError: true }).then(response => response.data), ...options });
/**
 * All attack and spell ids
 */
export const ensureUseGetApiAdminSkillsData = (queryClient: QueryClient, clientOptions: Options<GetApiAdminSkillsData, true> = {}, options?: Omit<EnsureQueryDataOptions<Common.GetApiAdminSkillsDefaultResponse>, "queryKey" | "queryFn">) => queryClient.ensureQueryData({ queryKey: Common.UseGetApiAdminSkillsKeyFn(clientOptions), queryFn: ({ signal }) => getApiAdminSkills({ ...clientOptions, signal, throwOnError: true }).then(response => response.data), ...options });
/**
 * Live NPC instances with full animal/combat state
 */
export const ensureUseGetApiAdminNpcsData = (queryClient: QueryClient, clientOptions: Options<GetApiAdminNpcsData, true> = {}, options?: Omit<EnsureQueryDataOptions<Common.GetApiAdminNpcsDefaultResponse>, "queryKey" | "queryFn">) => queryClient.ensureQueryData({ queryKey: Common.UseGetApiAdminNpcsKeyFn(clientOptions), queryFn: ({ signal }) => getApiAdminNpcs({ ...clientOptions, signal, throwOnError: true }).then(response => response.data), ...options });
/**
 * All registered block definitions
 */
export const ensureUseGetApiAdminBlocksData = (queryClient: QueryClient, clientOptions: Options<GetApiAdminBlocksData, true> = {}, options?: Omit<EnsureQueryDataOptions<Common.GetApiAdminBlocksDefaultResponse>, "queryKey" | "queryFn">) => queryClient.ensureQueryData({ queryKey: Common.UseGetApiAdminBlocksKeyFn(clientOptions), queryFn: ({ signal }) => getApiAdminBlocks({ ...clientOptions, signal, throwOnError: true }).then(response => response.data), ...options });
/**
 * All registered plain paint colors
 */
export const ensureUseGetApiAdminPlainColorsData = (queryClient: QueryClient, clientOptions: Options<GetApiAdminPlainColorsData, true> = {}, options?: Omit<EnsureQueryDataOptions<Common.GetApiAdminPlainColorsDefaultResponse>, "queryKey" | "queryFn">) => queryClient.ensureQueryData({ queryKey: Common.UseGetApiAdminPlainColorsKeyFn(clientOptions), queryFn: ({ signal }) => getApiAdminPlainColors({ ...clientOptions, signal, throwOnError: true }).then(response => response.data), ...options });
/**
 * All chunk coordinates generated so far (in-memory ∪ persisted)
 */
export const ensureUseGetApiAdminChunksDiscoveredData = (queryClient: QueryClient, clientOptions: Options<GetApiAdminChunksDiscoveredData, true> = {}, options?: Omit<EnsureQueryDataOptions<Common.GetApiAdminChunksDiscoveredDefaultResponse>, "queryKey" | "queryFn">) => queryClient.ensureQueryData({ queryKey: Common.UseGetApiAdminChunksDiscoveredKeyFn(clientOptions), queryFn: ({ signal }) => getApiAdminChunksDiscovered({ ...clientOptions, signal, throwOnError: true }).then(response => response.data), ...options });
/**
 * All instance zones
 */
export const ensureUseGetApiAdminInstancesData = (queryClient: QueryClient, clientOptions: Options<GetApiAdminInstancesData, true> = {}, options?: Omit<EnsureQueryDataOptions<Common.GetApiAdminInstancesDefaultResponse>, "queryKey" | "queryFn">) => queryClient.ensureQueryData({ queryKey: Common.UseGetApiAdminInstancesKeyFn(clientOptions), queryFn: ({ signal }) => getApiAdminInstances({ ...clientOptions, signal, throwOnError: true }).then(response => response.data), ...options });
/**
 * A single instance zone
 */
export const ensureUseGetApiAdminInstancesByIdData = (queryClient: QueryClient, clientOptions: Options<GetApiAdminInstancesByIdData, true>, options?: Omit<EnsureQueryDataOptions<Common.GetApiAdminInstancesByIdDefaultResponse>, "queryKey" | "queryFn">) => queryClient.ensureQueryData({ queryKey: Common.UseGetApiAdminInstancesByIdKeyFn(clientOptions), queryFn: ({ signal }) => getApiAdminInstancesById({ ...clientOptions, signal, throwOnError: true }).then(response => response.data), ...options });
/**
 * Non-air blocks in an instance zone, streamed as newline-delimited JSON (application/x-ndjson, one InstanceBlockDto per line), capped at 300000
 */
export const ensureUseGetApiAdminInstancesByIdBlocksData = (queryClient: QueryClient, clientOptions: Options<GetApiAdminInstancesByIdBlocksData, true>, options?: Omit<EnsureQueryDataOptions<Common.GetApiAdminInstancesByIdBlocksDefaultResponse>, "queryKey" | "queryFn">) => queryClient.ensureQueryData({ queryKey: Common.UseGetApiAdminInstancesByIdBlocksKeyFn(clientOptions), queryFn: ({ signal }) => getApiAdminInstancesByIdBlocks({ ...clientOptions, signal, throwOnError: true }).then(response => response.data), ...options });
/**
 * All scenes (bounded off-world block-structure buffers)
 */
export const ensureUseGetApiAdminScenesData = (queryClient: QueryClient, clientOptions: Options<GetApiAdminScenesData, true> = {}, options?: Omit<EnsureQueryDataOptions<Common.GetApiAdminScenesDefaultResponse>, "queryKey" | "queryFn">) => queryClient.ensureQueryData({ queryKey: Common.UseGetApiAdminScenesKeyFn(clientOptions), queryFn: ({ signal }) => getApiAdminScenes({ ...clientOptions, signal, throwOnError: true }).then(response => response.data), ...options });
/**
 * A single scene's metadata
 */
export const ensureUseGetApiAdminScenesByIdData = (queryClient: QueryClient, clientOptions: Options<GetApiAdminScenesByIdData, true>, options?: Omit<EnsureQueryDataOptions<Common.GetApiAdminScenesByIdDefaultResponse>, "queryKey" | "queryFn">) => queryClient.ensureQueryData({ queryKey: Common.UseGetApiAdminScenesByIdKeyFn(clientOptions), queryFn: ({ signal }) => getApiAdminScenesById({ ...clientOptions, signal, throwOnError: true }).then(response => response.data), ...options });
/**
 * Scene block/state/extraState buffers as a binary blob: 3×4-byte big-endian dimensions (width,height,depth) followed by the blocks byte array, then the states byte array, then the extraStates byte array (wire-index-per-byte, 0 = AIR)
 */
export const ensureUseGetApiAdminScenesByIdBlocksRawData = (queryClient: QueryClient, clientOptions: Options<GetApiAdminScenesByIdBlocksRawData, true>, options?: Omit<EnsureQueryDataOptions<Common.GetApiAdminScenesByIdBlocksRawDefaultResponse>, "queryKey" | "queryFn">) => queryClient.ensureQueryData({ queryKey: Common.UseGetApiAdminScenesByIdBlocksRawKeyFn(clientOptions), queryFn: ({ signal }) => getApiAdminScenesByIdBlocksRaw({ ...clientOptions, signal, throwOnError: true }).then(response => response.data), ...options });
/**
 * Fractional (lego/plate/arch) block entities placed in this scene — not carried by the blocks/raw binary blob, so the client loads them separately on scene open
 */
export const ensureUseGetApiAdminScenesByIdEntitiesData = (queryClient: QueryClient, clientOptions: Options<GetApiAdminScenesByIdEntitiesData, true>, options?: Omit<EnsureQueryDataOptions<Common.GetApiAdminScenesByIdEntitiesDefaultResponse>, "queryKey" | "queryFn">) => queryClient.ensureQueryData({ queryKey: Common.UseGetApiAdminScenesByIdEntitiesKeyFn(clientOptions), queryFn: ({ signal }) => getApiAdminScenesByIdEntities({ ...clientOptions, signal, throwOnError: true }).then(response => response.data), ...options });
/**
 * NPC type definitions (codex info), keyed by type id
 */
export const ensureUseGetApiAdminNpcTypesData = (queryClient: QueryClient, clientOptions: Options<GetApiAdminNpcTypesData, true> = {}, options?: Omit<EnsureQueryDataOptions<Common.GetApiAdminNpcTypesDefaultResponse>, "queryKey" | "queryFn">) => queryClient.ensureQueryData({ queryKey: Common.UseGetApiAdminNpcTypesKeyFn(clientOptions), queryFn: ({ signal }) => getApiAdminNpcTypes({ ...clientOptions, signal, throwOnError: true }).then(response => response.data), ...options });
/**
 * Item definitions, keyed by item type id
 */
export const ensureUseGetApiAdminItemsData = (queryClient: QueryClient, clientOptions: Options<GetApiAdminItemsData, true> = {}, options?: Omit<EnsureQueryDataOptions<Common.GetApiAdminItemsDefaultResponse>, "queryKey" | "queryFn">) => queryClient.ensureQueryData({ queryKey: Common.UseGetApiAdminItemsKeyFn(clientOptions), queryFn: ({ signal }) => getApiAdminItems({ ...clientOptions, signal, throwOnError: true }).then(response => response.data), ...options });
/**
 * A JSON Schema file (data/config/schemas*.schema.json) for the config editor
 */
export const ensureUseGetApiAdminSchemasByFilenameData = (queryClient: QueryClient, clientOptions: Options<GetApiAdminSchemasByFilenameData, true>, options?: Omit<EnsureQueryDataOptions<Common.GetApiAdminSchemasByFilenameDefaultResponse>, "queryKey" | "queryFn">) => queryClient.ensureQueryData({ queryKey: Common.UseGetApiAdminSchemasByFilenameKeyFn(clientOptions), queryFn: ({ signal }) => getApiAdminSchemasByFilename({ ...clientOptions, signal, throwOnError: true }).then(response => response.data), ...options });
/**
 * Defaults the world simulator admin UI prefills its editors with
 */
export const ensureUseGetApiAdminSimulationDefaultsData = (queryClient: QueryClient, clientOptions: Options<GetApiAdminSimulationDefaultsData, true> = {}, options?: Omit<EnsureQueryDataOptions<Common.GetApiAdminSimulationDefaultsDefaultResponse>, "queryKey" | "queryFn">) => queryClient.ensureQueryData({ queryKey: Common.UseGetApiAdminSimulationDefaultsKeyFn(clientOptions), queryFn: ({ signal }) => getApiAdminSimulationDefaults({ ...clientOptions, signal, throwOnError: true }).then(response => response.data), ...options });
/**
 * Binary-encoded chunk data (protocol.ServerMessage.ChunkData wire format). Not a JSON API — used by the game client, not by TanStack Query hooks.
 */
export const ensureUseGetApiChunksByCxByCzData = (queryClient: QueryClient, clientOptions: Options<GetApiChunksByCxByCzData, true>, options?: Omit<EnsureQueryDataOptions<Common.GetApiChunksByCxByCzDefaultResponse>, "queryKey" | "queryFn">) => queryClient.ensureQueryData({ queryKey: Common.UseGetApiChunksByCxByCzKeyFn(clientOptions), queryFn: ({ signal }) => getApiChunksByCxByCz({ ...clientOptions, signal, throwOnError: true }).then(response => response.data), ...options });
/**
 * Player characters (name + id) linked to an account email
 */
export const ensureUseGetApiPlayersByEmailByEmailData = (queryClient: QueryClient, clientOptions: Options<GetApiPlayersByEmailByEmailData, true>, options?: Omit<EnsureQueryDataOptions<Common.GetApiPlayersByEmailByEmailDefaultResponse>, "queryKey" | "queryFn">) => queryClient.ensureQueryData({ queryKey: Common.UseGetApiPlayersByEmailByEmailKeyFn(clientOptions), queryFn: ({ signal }) => getApiPlayersByEmailByEmail({ ...clientOptions, signal, throwOnError: true }).then(response => response.data), ...options });
