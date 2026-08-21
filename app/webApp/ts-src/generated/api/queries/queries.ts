// generated with @7nohe/openapi-react-query-codegen@3.0.2 

import { useMutation, useQuery, type UseMutationOptions, type UseQueryOptions } from "@tanstack/react-query";
import { deleteApiAdminInstancesById, deleteApiAdminScenesById, deleteApiAdminUsersByEmail, deleteApiGameAssetsBlendCacheBy, getApiAdminBlocks, getApiAdminChunksDiscovered, getApiAdminClasses, getApiAdminConfigs, getApiAdminConfigsBy, getApiAdminInstances, getApiAdminInstancesById, getApiAdminInstancesByIdBlocks, getApiAdminItems, getApiAdminNpcTypes, getApiAdminNpcs, getApiAdminPlainColors, getApiAdminPlayers, getApiAdminPlayersByName, getApiAdminScenes, getApiAdminScenesById, getApiAdminScenesByIdBlocksRaw, getApiAdminScenesByIdEntities, getApiAdminSchemasByFilename, getApiAdminSimulationDefaults, getApiAdminSkills, getApiAdminStatus, getApiAdminUsers, getApiAdminWorlds, getApiAdminWsInstancesById, getApiAdminWsNpcs, getApiAdminWsScenesById, getApiAdminWsSimulation, getApiArmors, getApiAssetsManifest, getApiAttacks, getApiAuthConfig, getApiAutocompleteByCommandIdByArgIndex, getApiBiomes, getApiChunksByCxByCz, getApiClasses, getApiGameAssets, getApiGameAssetsBbmodelExportBy, getApiGameAssetsBlendPreviewBy, getApiGameAssetsBlendSceneBy, getApiGameAssetsFileBy, getApiI18nByLocale, getApiItemsMeta, getApiKeybindings, getApiLayoutRegistry, getApiMacrosContext, getApiMapHouses, getApiMapRoadRaster, getApiMapRoadRasterPng, getApiMapRoads, getApiMapStaircases, getApiMapState, getApiMapTerrain, getApiMapTerrainRasterPng, getApiMapVoronoi, getApiMapVoronoiBorders, getApiPlayerByIdArmors, getApiPlayerByIdHands, getApiPlayerByIdRpg, getApiPlayerByIdSkin, getApiPlayersByEmailByEmail, getApiPlayersNames, getApiQuests, getApiServerInfo, getApiSkins, getApiSkinsByNameConfig, getApiSpells, getApiTools, getApiWeapons, postApiAdminInstances, postApiAdminPlayersByNameRename, postApiAdminRestart, postApiAdminScenes, postApiAdminScenesByIdDuplicate, postApiAdminUsers, postApiAdminWorlds, postApiAssetsReload, postApiCharacterCreate, postApiCharacterRpgcreate, postApiPlayerByIdScreenshots, postAuthNoauthLogin, putApiAdminConfigsBy, putApiAdminGametime, putApiAdminInstancesById, putApiAdminInstancesByIdBounds, putApiAdminInstancesByIdChunks, putApiAdminInstancesByIdEnabled, putApiAdminInstancesByIdLayout, putApiAdminPlayersByNameKeybindings, putApiAdminPlayersByNamePreferences, putApiAdminPlayersByNameRpg, putApiAdminScenesById, putApiAdminScenesByIdDimensions, putApiAdminScenesByIdLayout, putApiAdminUsersByEmail, putApiPlayerByIdSkin, type Options } from "../requests/sdk.gen";
import type { DeleteApiAdminInstancesByIdData, DeleteApiAdminScenesByIdData, DeleteApiAdminUsersByEmailData, DeleteApiGameAssetsBlendCacheByData, GetApiAdminBlocksData, GetApiAdminChunksDiscoveredData, GetApiAdminClassesData, GetApiAdminConfigsByData, GetApiAdminConfigsData, GetApiAdminInstancesByIdBlocksData, GetApiAdminInstancesByIdData, GetApiAdminInstancesData, GetApiAdminItemsData, GetApiAdminNpcTypesData, GetApiAdminNpcsData, GetApiAdminPlainColorsData, GetApiAdminPlayersByNameData, GetApiAdminPlayersData, GetApiAdminScenesByIdBlocksRawData, GetApiAdminScenesByIdData, GetApiAdminScenesByIdEntitiesData, GetApiAdminScenesData, GetApiAdminSchemasByFilenameData, GetApiAdminSimulationDefaultsData, GetApiAdminSkillsData, GetApiAdminStatusData, GetApiAdminUsersData, GetApiAdminWorldsData, GetApiAdminWsInstancesByIdData, GetApiAdminWsNpcsData, GetApiAdminWsScenesByIdData, GetApiAdminWsSimulationData, GetApiArmorsData, GetApiAssetsManifestData, GetApiAttacksData, GetApiAuthConfigData, GetApiAutocompleteByCommandIdByArgIndexData, GetApiBiomesData, GetApiChunksByCxByCzData, GetApiClassesData, GetApiGameAssetsBbmodelExportByData, GetApiGameAssetsBlendPreviewByData, GetApiGameAssetsBlendSceneByData, GetApiGameAssetsData, GetApiGameAssetsFileByData, GetApiI18nByLocaleData, GetApiItemsMetaData, GetApiKeybindingsData, GetApiLayoutRegistryData, GetApiMacrosContextData, GetApiMapHousesData, GetApiMapRoadRasterData, GetApiMapRoadRasterPngData, GetApiMapRoadsData, GetApiMapStaircasesData, GetApiMapStateData, GetApiMapTerrainData, GetApiMapTerrainRasterPngData, GetApiMapVoronoiBordersData, GetApiMapVoronoiData, GetApiPlayerByIdArmorsData, GetApiPlayerByIdHandsData, GetApiPlayerByIdRpgData, GetApiPlayerByIdSkinData, GetApiPlayersByEmailByEmailData, GetApiPlayersNamesData, GetApiQuestsData, GetApiServerInfoData, GetApiSkinsByNameConfigData, GetApiSkinsData, GetApiSpellsData, GetApiToolsData, GetApiWeaponsData, PostApiAdminInstancesData, PostApiAdminPlayersByNameRenameData, PostApiAdminRestartData, PostApiAdminScenesByIdDuplicateData, PostApiAdminScenesData, PostApiAdminUsersData, PostApiAdminWorldsData, PostApiAssetsReloadData, PostApiCharacterCreateData, PostApiCharacterRpgcreateData, PostApiPlayerByIdScreenshotsData, PostAuthNoauthLoginData, PutApiAdminConfigsByData, PutApiAdminGametimeData, PutApiAdminInstancesByIdBoundsData, PutApiAdminInstancesByIdChunksData, PutApiAdminInstancesByIdData, PutApiAdminInstancesByIdEnabledData, PutApiAdminInstancesByIdLayoutData, PutApiAdminPlayersByNameKeybindingsData, PutApiAdminPlayersByNamePreferencesData, PutApiAdminPlayersByNameRpgData, PutApiAdminScenesByIdData, PutApiAdminScenesByIdDimensionsData, PutApiAdminScenesByIdLayoutData, PutApiAdminUsersByEmailData, PutApiPlayerByIdSkinData } from "../requests/types.gen";
import * as Common from "./common";

/**
 * Active auth provider, used by the client to pick the right login UI
 */
export const useGetApiAuthConfig = <TData = Common.GetApiAuthConfigDefaultResponse, TError = unknown, TQueryKey extends Array<unknown> = unknown[]>(clientOptions: Options<GetApiAuthConfigData, true> = {}, queryKey?: TQueryKey, options?: Omit<UseQueryOptions<TData, TError>, "queryKey" | "queryFn">) => useQuery<TData, TError>({ queryKey: Common.UseGetApiAuthConfigKeyFn(clientOptions, queryKey), queryFn: ({ signal }) => getApiAuthConfig({ ...clientOptions, signal, throwOnError: true }).then(response => response.data as TData) as TData, ...options });
export const useGetApiAssetsManifest = <TData = Common.GetApiAssetsManifestDefaultResponse, TError = unknown, TQueryKey extends Array<unknown> = unknown[]>(clientOptions: Options<GetApiAssetsManifestData, true> = {}, queryKey?: TQueryKey, options?: Omit<UseQueryOptions<TData, TError>, "queryKey" | "queryFn">) => useQuery<TData, TError>({ queryKey: Common.UseGetApiAssetsManifestKeyFn(clientOptions, queryKey), queryFn: ({ signal }) => getApiAssetsManifest({ ...clientOptions, signal, throwOnError: true }).then(response => response.data as TData) as TData, ...options });
export const useGetApiAdminWsNpcs = <TData = Common.GetApiAdminWsNpcsDefaultResponse, TError = unknown, TQueryKey extends Array<unknown> = unknown[]>(clientOptions: Options<GetApiAdminWsNpcsData, true> = {}, queryKey?: TQueryKey, options?: Omit<UseQueryOptions<TData, TError>, "queryKey" | "queryFn">) => useQuery<TData, TError>({ queryKey: Common.UseGetApiAdminWsNpcsKeyFn(clientOptions, queryKey), queryFn: ({ signal }) => getApiAdminWsNpcs({ ...clientOptions, signal, throwOnError: true }).then(response => response.data as TData) as TData, ...options });
export const useGetApiAdminWsScenesById = <TData = Common.GetApiAdminWsScenesByIdDefaultResponse, TError = unknown, TQueryKey extends Array<unknown> = unknown[]>(clientOptions: Options<GetApiAdminWsScenesByIdData, true> = {}, queryKey?: TQueryKey, options?: Omit<UseQueryOptions<TData, TError>, "queryKey" | "queryFn">) => useQuery<TData, TError>({ queryKey: Common.UseGetApiAdminWsScenesByIdKeyFn(clientOptions, queryKey), queryFn: ({ signal }) => getApiAdminWsScenesById({ ...clientOptions, signal, throwOnError: true }).then(response => response.data as TData) as TData, ...options });
export const useGetApiAdminWsInstancesById = <TData = Common.GetApiAdminWsInstancesByIdDefaultResponse, TError = unknown, TQueryKey extends Array<unknown> = unknown[]>(clientOptions: Options<GetApiAdminWsInstancesByIdData, true> = {}, queryKey?: TQueryKey, options?: Omit<UseQueryOptions<TData, TError>, "queryKey" | "queryFn">) => useQuery<TData, TError>({ queryKey: Common.UseGetApiAdminWsInstancesByIdKeyFn(clientOptions, queryKey), queryFn: ({ signal }) => getApiAdminWsInstancesById({ ...clientOptions, signal, throwOnError: true }).then(response => response.data as TData) as TData, ...options });
export const useGetApiAdminWsSimulation = <TData = Common.GetApiAdminWsSimulationDefaultResponse, TError = unknown, TQueryKey extends Array<unknown> = unknown[]>(clientOptions: Options<GetApiAdminWsSimulationData, true> = {}, queryKey?: TQueryKey, options?: Omit<UseQueryOptions<TData, TError>, "queryKey" | "queryFn">) => useQuery<TData, TError>({ queryKey: Common.UseGetApiAdminWsSimulationKeyFn(clientOptions, queryKey), queryFn: ({ signal }) => getApiAdminWsSimulation({ ...clientOptions, signal, throwOnError: true }).then(response => response.data as TData) as TData, ...options });
/**
 * Key bindings — a player's saved bindings if ?player= is given and persistence is available, otherwise the default config
 */
export const useGetApiKeybindings = <TData = Common.GetApiKeybindingsDefaultResponse, TError = unknown, TQueryKey extends Array<unknown> = unknown[]>(clientOptions: Options<GetApiKeybindingsData, true> = {}, queryKey?: TQueryKey, options?: Omit<UseQueryOptions<TData, TError>, "queryKey" | "queryFn">) => useQuery<TData, TError>({ queryKey: Common.UseGetApiKeybindingsKeyFn(clientOptions, queryKey), queryFn: ({ signal }) => getApiKeybindings({ ...clientOptions, signal, throwOnError: true }).then(response => response.data as TData) as TData, ...options });
/**
 * Autocomplete suggestions for a slash command argument
 */
export const useGetApiAutocompleteByCommandIdByArgIndex = <TData = Common.GetApiAutocompleteByCommandIdByArgIndexDefaultResponse, TError = unknown, TQueryKey extends Array<unknown> = unknown[]>(clientOptions: Options<GetApiAutocompleteByCommandIdByArgIndexData, true>, queryKey?: TQueryKey, options?: Omit<UseQueryOptions<TData, TError>, "queryKey" | "queryFn">) => useQuery<TData, TError>({ queryKey: Common.UseGetApiAutocompleteByCommandIdByArgIndexKeyFn(clientOptions, queryKey), queryFn: ({ signal }) => getApiAutocompleteByCommandIdByArgIndex({ ...clientOptions, signal, throwOnError: true }).then(response => response.data as TData) as TData, ...options });
/**
 * Client-facing translation keys for a locale
 */
export const useGetApiI18nByLocale = <TData = Common.GetApiI18nByLocaleDefaultResponse, TError = unknown, TQueryKey extends Array<unknown> = unknown[]>(clientOptions: Options<GetApiI18nByLocaleData, true>, queryKey?: TQueryKey, options?: Omit<UseQueryOptions<TData, TError>, "queryKey" | "queryFn">) => useQuery<TData, TError>({ queryKey: Common.UseGetApiI18nByLocaleKeyFn(clientOptions, queryKey), queryFn: ({ signal }) => getApiI18nByLocale({ ...clientOptions, signal, throwOnError: true }).then(response => response.data as TData) as TData, ...options });
/**
 * All widgets registered for the UI layout editor
 */
export const useGetApiLayoutRegistry = <TData = Common.GetApiLayoutRegistryDefaultResponse, TError = unknown, TQueryKey extends Array<unknown> = unknown[]>(clientOptions: Options<GetApiLayoutRegistryData, true> = {}, queryKey?: TQueryKey, options?: Omit<UseQueryOptions<TData, TError>, "queryKey" | "queryFn">) => useQuery<TData, TError>({ queryKey: Common.UseGetApiLayoutRegistryKeyFn(clientOptions, queryKey), queryFn: ({ signal }) => getApiLayoutRegistry({ ...clientOptions, signal, throwOnError: true }).then(response => response.data as TData) as TData, ...options });
/**
 * Item metadata (label, background color, consumable flags) by item type id
 */
export const useGetApiItemsMeta = <TData = Common.GetApiItemsMetaDefaultResponse, TError = unknown, TQueryKey extends Array<unknown> = unknown[]>(clientOptions: Options<GetApiItemsMetaData, true> = {}, queryKey?: TQueryKey, options?: Omit<UseQueryOptions<TData, TError>, "queryKey" | "queryFn">) => useQuery<TData, TError>({ queryKey: Common.UseGetApiItemsMetaKeyFn(clientOptions, queryKey), queryFn: ({ signal }) => getApiItemsMeta({ ...clientOptions, signal, throwOnError: true }).then(response => response.data as TData) as TData, ...options });
/**
 * Server build timestamp
 */
export const useGetApiServerInfo = <TData = Common.GetApiServerInfoDefaultResponse, TError = unknown, TQueryKey extends Array<unknown> = unknown[]>(clientOptions: Options<GetApiServerInfoData, true> = {}, queryKey?: TQueryKey, options?: Omit<UseQueryOptions<TData, TError>, "queryKey" | "queryFn">) => useQuery<TData, TError>({ queryKey: Common.UseGetApiServerInfoKeyFn(clientOptions, queryKey), queryFn: ({ signal }) => getApiServerInfo({ ...clientOptions, signal, throwOnError: true }).then(response => response.data as TData) as TData, ...options });
/**
 * Attack definitions, flattened by "attackId:level" key
 */
export const useGetApiAttacks = <TData = Common.GetApiAttacksDefaultResponse, TError = unknown, TQueryKey extends Array<unknown> = unknown[]>(clientOptions: Options<GetApiAttacksData, true> = {}, queryKey?: TQueryKey, options?: Omit<UseQueryOptions<TData, TError>, "queryKey" | "queryFn">) => useQuery<TData, TError>({ queryKey: Common.UseGetApiAttacksKeyFn(clientOptions, queryKey), queryFn: ({ signal }) => getApiAttacks({ ...clientOptions, signal, throwOnError: true }).then(response => response.data as TData) as TData, ...options });
/**
 * Attack ids accessible per RPG class, keyed by level
 */
export const useGetApiClasses = <TData = Common.GetApiClassesDefaultResponse, TError = unknown, TQueryKey extends Array<unknown> = unknown[]>(clientOptions: Options<GetApiClassesData, true> = {}, queryKey?: TQueryKey, options?: Omit<UseQueryOptions<TData, TError>, "queryKey" | "queryFn">) => useQuery<TData, TError>({ queryKey: Common.UseGetApiClassesKeyFn(clientOptions, queryKey), queryFn: ({ signal }) => getApiClasses({ ...clientOptions, signal, throwOnError: true }).then(response => response.data as TData) as TData, ...options });
/**
 * Spell definitions, keyed by spell id
 */
export const useGetApiSpells = <TData = Common.GetApiSpellsDefaultResponse, TError = unknown, TQueryKey extends Array<unknown> = unknown[]>(clientOptions: Options<GetApiSpellsData, true> = {}, queryKey?: TQueryKey, options?: Omit<UseQueryOptions<TData, TError>, "queryKey" | "queryFn">) => useQuery<TData, TError>({ queryKey: Common.UseGetApiSpellsKeyFn(clientOptions, queryKey), queryFn: ({ signal }) => getApiSpells({ ...clientOptions, signal, throwOnError: true }).then(response => response.data as TData) as TData, ...options });
/**
 * Variables available to the macro JEXL evaluation context
 */
export const useGetApiMacrosContext = <TData = Common.GetApiMacrosContextDefaultResponse, TError = unknown, TQueryKey extends Array<unknown> = unknown[]>(clientOptions: Options<GetApiMacrosContextData, true> = {}, queryKey?: TQueryKey, options?: Omit<UseQueryOptions<TData, TError>, "queryKey" | "queryFn">) => useQuery<TData, TError>({ queryKey: Common.UseGetApiMacrosContextKeyFn(clientOptions, queryKey), queryFn: ({ signal }) => getApiMacrosContext({ ...clientOptions, signal, throwOnError: true }).then(response => response.data as TData) as TData, ...options });
/**
 * Grass color per biome id, as [r, g, b] in 0..1
 */
export const useGetApiBiomes = <TData = Common.GetApiBiomesDefaultResponse, TError = unknown, TQueryKey extends Array<unknown> = unknown[]>(clientOptions: Options<GetApiBiomesData, true> = {}, queryKey?: TQueryKey, options?: Omit<UseQueryOptions<TData, TError>, "queryKey" | "queryFn">) => useQuery<TData, TError>({ queryKey: Common.UseGetApiBiomesKeyFn(clientOptions, queryKey), queryFn: ({ signal }) => getApiBiomes({ ...clientOptions, signal, throwOnError: true }).then(response => response.data as TData) as TData, ...options });
/**
 * A player's current skin
 */
export const useGetApiPlayerByIdSkin = <TData = Common.GetApiPlayerByIdSkinDefaultResponse, TError = unknown, TQueryKey extends Array<unknown> = unknown[]>(clientOptions: Options<GetApiPlayerByIdSkinData, true>, queryKey?: TQueryKey, options?: Omit<UseQueryOptions<TData, TError>, "queryKey" | "queryFn">) => useQuery<TData, TError>({ queryKey: Common.UseGetApiPlayerByIdSkinKeyFn(clientOptions, queryKey), queryFn: ({ signal }) => getApiPlayerByIdSkin({ ...clientOptions, signal, throwOnError: true }).then(response => response.data as TData) as TData, ...options });
/**
 * Armor names currently equipped by a player
 */
export const useGetApiPlayerByIdArmors = <TData = Common.GetApiPlayerByIdArmorsDefaultResponse, TError = unknown, TQueryKey extends Array<unknown> = unknown[]>(clientOptions: Options<GetApiPlayerByIdArmorsData, true>, queryKey?: TQueryKey, options?: Omit<UseQueryOptions<TData, TError>, "queryKey" | "queryFn">) => useQuery<TData, TError>({ queryKey: Common.UseGetApiPlayerByIdArmorsKeyFn(clientOptions, queryKey), queryFn: ({ signal }) => getApiPlayerByIdArmors({ ...clientOptions, signal, throwOnError: true }).then(response => response.data as TData) as TData, ...options });
/**
 * Wielded weapon/tool names and dominant hand for a player
 */
export const useGetApiPlayerByIdHands = <TData = Common.GetApiPlayerByIdHandsDefaultResponse, TError = unknown, TQueryKey extends Array<unknown> = unknown[]>(clientOptions: Options<GetApiPlayerByIdHandsData, true>, queryKey?: TQueryKey, options?: Omit<UseQueryOptions<TData, TError>, "queryKey" | "queryFn">) => useQuery<TData, TError>({ queryKey: Common.UseGetApiPlayerByIdHandsKeyFn(clientOptions, queryKey), queryFn: ({ signal }) => getApiPlayerByIdHands({ ...clientOptions, signal, throwOnError: true }).then(response => response.data as TData) as TData, ...options });
/**
 * A player's RPG character class
 */
export const useGetApiPlayerByIdRpg = <TData = Common.GetApiPlayerByIdRpgDefaultResponse, TError = unknown, TQueryKey extends Array<unknown> = unknown[]>(clientOptions: Options<GetApiPlayerByIdRpgData, true>, queryKey?: TQueryKey, options?: Omit<UseQueryOptions<TData, TError>, "queryKey" | "queryFn">) => useQuery<TData, TError>({ queryKey: Common.UseGetApiPlayerByIdRpgKeyFn(clientOptions, queryKey), queryFn: ({ signal }) => getApiPlayerByIdRpg({ ...clientOptions, signal, throwOnError: true }).then(response => response.data as TData) as TData, ...options });
/**
 * Names of all available player skins
 */
export const useGetApiSkins = <TData = Common.GetApiSkinsDefaultResponse, TError = unknown, TQueryKey extends Array<unknown> = unknown[]>(clientOptions: Options<GetApiSkinsData, true> = {}, queryKey?: TQueryKey, options?: Omit<UseQueryOptions<TData, TError>, "queryKey" | "queryFn">) => useQuery<TData, TError>({ queryKey: Common.UseGetApiSkinsKeyFn(clientOptions, queryKey), queryFn: ({ signal }) => getApiSkins({ ...clientOptions, signal, throwOnError: true }).then(response => response.data as TData) as TData, ...options });
/**
 * Skin config (eye offset, hidden bones) for a named skin
 */
export const useGetApiSkinsByNameConfig = <TData = Common.GetApiSkinsByNameConfigDefaultResponse, TError = unknown, TQueryKey extends Array<unknown> = unknown[]>(clientOptions: Options<GetApiSkinsByNameConfigData, true>, queryKey?: TQueryKey, options?: Omit<UseQueryOptions<TData, TError>, "queryKey" | "queryFn">) => useQuery<TData, TError>({ queryKey: Common.UseGetApiSkinsByNameConfigKeyFn(clientOptions, queryKey), queryFn: ({ signal }) => getApiSkinsByNameConfig({ ...clientOptions, signal, throwOnError: true }).then(response => response.data as TData) as TData, ...options });
/**
 * List all armor definitions
 */
export const useGetApiArmors = <TData = Common.GetApiArmorsDefaultResponse, TError = unknown, TQueryKey extends Array<unknown> = unknown[]>(clientOptions: Options<GetApiArmorsData, true> = {}, queryKey?: TQueryKey, options?: Omit<UseQueryOptions<TData, TError>, "queryKey" | "queryFn">) => useQuery<TData, TError>({ queryKey: Common.UseGetApiArmorsKeyFn(clientOptions, queryKey), queryFn: ({ signal }) => getApiArmors({ ...clientOptions, signal, throwOnError: true }).then(response => response.data as TData) as TData, ...options });
/**
 * List all weapon definitions
 */
export const useGetApiWeapons = <TData = Common.GetApiWeaponsDefaultResponse, TError = unknown, TQueryKey extends Array<unknown> = unknown[]>(clientOptions: Options<GetApiWeaponsData, true> = {}, queryKey?: TQueryKey, options?: Omit<UseQueryOptions<TData, TError>, "queryKey" | "queryFn">) => useQuery<TData, TError>({ queryKey: Common.UseGetApiWeaponsKeyFn(clientOptions, queryKey), queryFn: ({ signal }) => getApiWeapons({ ...clientOptions, signal, throwOnError: true }).then(response => response.data as TData) as TData, ...options });
/**
 * List all tool definitions
 */
export const useGetApiTools = <TData = Common.GetApiToolsDefaultResponse, TError = unknown, TQueryKey extends Array<unknown> = unknown[]>(clientOptions: Options<GetApiToolsData, true> = {}, queryKey?: TQueryKey, options?: Omit<UseQueryOptions<TData, TError>, "queryKey" | "queryFn">) => useQuery<TData, TError>({ queryKey: Common.UseGetApiToolsKeyFn(clientOptions, queryKey), queryFn: ({ signal }) => getApiTools({ ...clientOptions, signal, throwOnError: true }).then(response => response.data as TData) as TData, ...options });
/**
 * 3D game asset files discovered under resources/game-assets
 */
export const useGetApiGameAssets = <TData = Common.GetApiGameAssetsDefaultResponse, TError = unknown, TQueryKey extends Array<unknown> = unknown[]>(clientOptions: Options<GetApiGameAssetsData, true> = {}, queryKey?: TQueryKey, options?: Omit<UseQueryOptions<TData, TError>, "queryKey" | "queryFn">) => useQuery<TData, TError>({ queryKey: Common.UseGetApiGameAssetsKeyFn(clientOptions, queryKey), queryFn: ({ signal }) => getApiGameAssets({ ...clientOptions, signal, throwOnError: true }).then(response => response.data as TData) as TData, ...options });
/**
 * Raw asset file bytes (glb/gltf/fbx/textures)
 */
export const useGetApiGameAssetsFileBy = <TData = Common.GetApiGameAssetsFileByDefaultResponse, TError = unknown, TQueryKey extends Array<unknown> = unknown[]>(clientOptions: Options<GetApiGameAssetsFileByData, true>, queryKey?: TQueryKey, options?: Omit<UseQueryOptions<TData, TError>, "queryKey" | "queryFn">) => useQuery<TData, TError>({ queryKey: Common.UseGetApiGameAssetsFileByKeyFn(clientOptions, queryKey), queryFn: ({ signal }) => getApiGameAssetsFileBy({ ...clientOptions, signal, throwOnError: true }).then(response => response.data as TData) as TData, ...options });
/**
 * Reads a .blend file's collection/object tree via headless Blender (cached)
 */
export const useGetApiGameAssetsBlendSceneBy = <TData = Common.GetApiGameAssetsBlendSceneByDefaultResponse, TError = unknown, TQueryKey extends Array<unknown> = unknown[]>(clientOptions: Options<GetApiGameAssetsBlendSceneByData, true>, queryKey?: TQueryKey, options?: Omit<UseQueryOptions<TData, TError>, "queryKey" | "queryFn">) => useQuery<TData, TError>({ queryKey: Common.UseGetApiGameAssetsBlendSceneByKeyFn(clientOptions, queryKey), queryFn: ({ signal }) => getApiGameAssetsBlendSceneBy({ ...clientOptions, signal, throwOnError: true }).then(response => response.data as TData) as TData, ...options });
/**
 * Converts a .blend file to OBJ/MTL via headless Blender (cached) and returns the OBJ asset path
 */
export const useGetApiGameAssetsBlendPreviewBy = <TData = Common.GetApiGameAssetsBlendPreviewByDefaultResponse, TError = unknown, TQueryKey extends Array<unknown> = unknown[]>(clientOptions: Options<GetApiGameAssetsBlendPreviewByData, true>, queryKey?: TQueryKey, options?: Omit<UseQueryOptions<TData, TError>, "queryKey" | "queryFn">) => useQuery<TData, TError>({ queryKey: Common.UseGetApiGameAssetsBlendPreviewByKeyFn(clientOptions, queryKey), queryFn: ({ signal }) => getApiGameAssetsBlendPreviewBy({ ...clientOptions, signal, throwOnError: true }).then(response => response.data as TData) as TData, ...options });
/**
 * Converts an OBJ/MTL mesh into a Blockbench-compatible mesh .bbmodel (cached). The generated mesh elements are not rendered by the admin viewer — open the result in Blockbench to edit it.
 */
export const useGetApiGameAssetsBbmodelExportBy = <TData = Common.GetApiGameAssetsBbmodelExportByDefaultResponse, TError = unknown, TQueryKey extends Array<unknown> = unknown[]>(clientOptions: Options<GetApiGameAssetsBbmodelExportByData, true>, queryKey?: TQueryKey, options?: Omit<UseQueryOptions<TData, TError>, "queryKey" | "queryFn">) => useQuery<TData, TError>({ queryKey: Common.UseGetApiGameAssetsBbmodelExportByKeyFn(clientOptions, queryKey), queryFn: ({ signal }) => getApiGameAssetsBbmodelExportBy({ ...clientOptions, signal, throwOnError: true }).then(response => response.data as TData) as TData, ...options });
/**
 * All quest definitions
 */
export const useGetApiQuests = <TData = Common.GetApiQuestsDefaultResponse, TError = unknown, TQueryKey extends Array<unknown> = unknown[]>(clientOptions: Options<GetApiQuestsData, true> = {}, queryKey?: TQueryKey, options?: Omit<UseQueryOptions<TData, TError>, "queryKey" | "queryFn">) => useQuery<TData, TError>({ queryKey: Common.UseGetApiQuestsKeyFn(clientOptions, queryKey), queryFn: ({ signal }) => getApiQuests({ ...clientOptions, signal, throwOnError: true }).then(response => response.data as TData) as TData, ...options });
/**
 * Names of all known players
 */
export const useGetApiPlayersNames = <TData = Common.GetApiPlayersNamesDefaultResponse, TError = unknown, TQueryKey extends Array<unknown> = unknown[]>(clientOptions: Options<GetApiPlayersNamesData, true> = {}, queryKey?: TQueryKey, options?: Omit<UseQueryOptions<TData, TError>, "queryKey" | "queryFn">) => useQuery<TData, TError>({ queryKey: Common.UseGetApiPlayersNamesKeyFn(clientOptions, queryKey), queryFn: ({ signal }) => getApiPlayersNames({ ...clientOptions, signal, throwOnError: true }).then(response => response.data as TData) as TData, ...options });
/**
 * Live players, NPCs and weather zones for the map overlay
 */
export const useGetApiMapState = <TData = Common.GetApiMapStateDefaultResponse, TError = unknown, TQueryKey extends Array<unknown> = unknown[]>(clientOptions: Options<GetApiMapStateData, true> = {}, queryKey?: TQueryKey, options?: Omit<UseQueryOptions<TData, TError>, "queryKey" | "queryFn">) => useQuery<TData, TError>({ queryKey: Common.UseGetApiMapStateKeyFn(clientOptions, queryKey), queryFn: ({ signal }) => getApiMapState({ ...clientOptions, signal, throwOnError: true }).then(response => response.data as TData) as TData, ...options });
/**
 * Cached terrain summary JSON for the map overlay
 */
export const useGetApiMapTerrain = <TData = Common.GetApiMapTerrainDefaultResponse, TError = unknown, TQueryKey extends Array<unknown> = unknown[]>(clientOptions: Options<GetApiMapTerrainData, true> = {}, queryKey?: TQueryKey, options?: Omit<UseQueryOptions<TData, TError>, "queryKey" | "queryFn">) => useQuery<TData, TError>({ queryKey: Common.UseGetApiMapTerrainKeyFn(clientOptions, queryKey), queryFn: ({ signal }) => getApiMapTerrain({ ...clientOptions, signal, throwOnError: true }).then(response => response.data as TData) as TData, ...options });
/**
 * Voronoi biome cells around a point
 */
export const useGetApiMapVoronoi = <TData = Common.GetApiMapVoronoiDefaultResponse, TError = unknown, TQueryKey extends Array<unknown> = unknown[]>(clientOptions: Options<GetApiMapVoronoiData, true> = {}, queryKey?: TQueryKey, options?: Omit<UseQueryOptions<TData, TError>, "queryKey" | "queryFn">) => useQuery<TData, TError>({ queryKey: Common.UseGetApiMapVoronoiKeyFn(clientOptions, queryKey), queryFn: ({ signal }) => getApiMapVoronoi({ ...clientOptions, signal, throwOnError: true }).then(response => response.data as TData) as TData, ...options });
/**
 * Generated houses within an area
 */
export const useGetApiMapHouses = <TData = Common.GetApiMapHousesDefaultResponse, TError = unknown, TQueryKey extends Array<unknown> = unknown[]>(clientOptions: Options<GetApiMapHousesData, true> = {}, queryKey?: TQueryKey, options?: Omit<UseQueryOptions<TData, TError>, "queryKey" | "queryFn">) => useQuery<TData, TError>({ queryKey: Common.UseGetApiMapHousesKeyFn(clientOptions, queryKey), queryFn: ({ signal }) => getApiMapHouses({ ...clientOptions, signal, throwOnError: true }).then(response => response.data as TData) as TData, ...options });
/**
 * Named staircase points for the map overlay
 */
export const useGetApiMapStaircases = <TData = Common.GetApiMapStaircasesDefaultResponse, TError = unknown, TQueryKey extends Array<unknown> = unknown[]>(clientOptions: Options<GetApiMapStaircasesData, true> = {}, queryKey?: TQueryKey, options?: Omit<UseQueryOptions<TData, TError>, "queryKey" | "queryFn">) => useQuery<TData, TError>({ queryKey: Common.UseGetApiMapStaircasesKeyFn(clientOptions, queryKey), queryFn: ({ signal }) => getApiMapStaircases({ ...clientOptions, signal, throwOnError: true }).then(response => response.data as TData) as TData, ...options });
/**
 * Road vertex segments within an area
 */
export const useGetApiMapRoads = <TData = Common.GetApiMapRoadsDefaultResponse, TError = unknown, TQueryKey extends Array<unknown> = unknown[]>(clientOptions: Options<GetApiMapRoadsData, true> = {}, queryKey?: TQueryKey, options?: Omit<UseQueryOptions<TData, TError>, "queryKey" | "queryFn">) => useQuery<TData, TError>({ queryKey: Common.UseGetApiMapRoadsKeyFn(clientOptions, queryKey), queryFn: ({ signal }) => getApiMapRoads({ ...clientOptions, signal, throwOnError: true }).then(response => response.data as TData) as TData, ...options });
/**
 * Per-chunk road bitmask within an area
 */
export const useGetApiMapRoadRaster = <TData = Common.GetApiMapRoadRasterDefaultResponse, TError = unknown, TQueryKey extends Array<unknown> = unknown[]>(clientOptions: Options<GetApiMapRoadRasterData, true> = {}, queryKey?: TQueryKey, options?: Omit<UseQueryOptions<TData, TError>, "queryKey" | "queryFn">) => useQuery<TData, TError>({ queryKey: Common.UseGetApiMapRoadRasterKeyFn(clientOptions, queryKey), queryFn: ({ signal }) => getApiMapRoadRaster({ ...clientOptions, signal, throwOnError: true }).then(response => response.data as TData) as TData, ...options });
/**
 * Rasterized road overlay as a PNG image
 */
export const useGetApiMapRoadRasterPng = <TData = Common.GetApiMapRoadRasterPngDefaultResponse, TError = unknown, TQueryKey extends Array<unknown> = unknown[]>(clientOptions: Options<GetApiMapRoadRasterPngData, true> = {}, queryKey?: TQueryKey, options?: Omit<UseQueryOptions<TData, TError>, "queryKey" | "queryFn">) => useQuery<TData, TError>({ queryKey: Common.UseGetApiMapRoadRasterPngKeyFn(clientOptions, queryKey), queryFn: ({ signal }) => getApiMapRoadRasterPng({ ...clientOptions, signal, throwOnError: true }).then(response => response.data as TData) as TData, ...options });
/**
 * Rasterized terrain overlay as a PNG image
 */
export const useGetApiMapTerrainRasterPng = <TData = Common.GetApiMapTerrainRasterPngDefaultResponse, TError = unknown, TQueryKey extends Array<unknown> = unknown[]>(clientOptions: Options<GetApiMapTerrainRasterPngData, true> = {}, queryKey?: TQueryKey, options?: Omit<UseQueryOptions<TData, TError>, "queryKey" | "queryFn">) => useQuery<TData, TError>({ queryKey: Common.UseGetApiMapTerrainRasterPngKeyFn(clientOptions, queryKey), queryFn: ({ signal }) => getApiMapTerrainRasterPng({ ...clientOptions, signal, throwOnError: true }).then(response => response.data as TData) as TData, ...options });
/**
 * Voronoi cell border segments within an area
 */
export const useGetApiMapVoronoiBorders = <TData = Common.GetApiMapVoronoiBordersDefaultResponse, TError = unknown, TQueryKey extends Array<unknown> = unknown[]>(clientOptions: Options<GetApiMapVoronoiBordersData, true> = {}, queryKey?: TQueryKey, options?: Omit<UseQueryOptions<TData, TError>, "queryKey" | "queryFn">) => useQuery<TData, TError>({ queryKey: Common.UseGetApiMapVoronoiBordersKeyFn(clientOptions, queryKey), queryFn: ({ signal }) => getApiMapVoronoiBorders({ ...clientOptions, signal, throwOnError: true }).then(response => response.data as TData) as TData, ...options });
/**
 * Server status snapshot (TPS, players, chunks, heap, CPU)
 */
export const useGetApiAdminStatus = <TData = Common.GetApiAdminStatusDefaultResponse, TError = unknown, TQueryKey extends Array<unknown> = unknown[]>(clientOptions: Options<GetApiAdminStatusData, true> = {}, queryKey?: TQueryKey, options?: Omit<UseQueryOptions<TData, TError>, "queryKey" | "queryFn">) => useQuery<TData, TError>({ queryKey: Common.UseGetApiAdminStatusKeyFn(clientOptions, queryKey), queryFn: ({ signal }) => getApiAdminStatus({ ...clientOptions, signal, throwOnError: true }).then(response => response.data as TData) as TData, ...options });
/**
 * All local/no-auth accounts
 */
export const useGetApiAdminUsers = <TData = Common.GetApiAdminUsersDefaultResponse, TError = unknown, TQueryKey extends Array<unknown> = unknown[]>(clientOptions: Options<GetApiAdminUsersData, true> = {}, queryKey?: TQueryKey, options?: Omit<UseQueryOptions<TData, TError>, "queryKey" | "queryFn">) => useQuery<TData, TError>({ queryKey: Common.UseGetApiAdminUsersKeyFn(clientOptions, queryKey), queryFn: ({ signal }) => getApiAdminUsers({ ...clientOptions, signal, throwOnError: true }).then(response => response.data as TData) as TData, ...options });
/**
 * All player names
 */
export const useGetApiAdminPlayers = <TData = Common.GetApiAdminPlayersDefaultResponse, TError = unknown, TQueryKey extends Array<unknown> = unknown[]>(clientOptions: Options<GetApiAdminPlayersData, true> = {}, queryKey?: TQueryKey, options?: Omit<UseQueryOptions<TData, TError>, "queryKey" | "queryFn">) => useQuery<TData, TError>({ queryKey: Common.UseGetApiAdminPlayersKeyFn(clientOptions, queryKey), queryFn: ({ signal }) => getApiAdminPlayers({ ...clientOptions, signal, throwOnError: true }).then(response => response.data as TData) as TData, ...options });
/**
 * Full player file (state, keybindings, RPG data)
 */
export const useGetApiAdminPlayersByName = <TData = Common.GetApiAdminPlayersByNameDefaultResponse, TError = unknown, TQueryKey extends Array<unknown> = unknown[]>(clientOptions: Options<GetApiAdminPlayersByNameData, true>, queryKey?: TQueryKey, options?: Omit<UseQueryOptions<TData, TError>, "queryKey" | "queryFn">) => useQuery<TData, TError>({ queryKey: Common.UseGetApiAdminPlayersByNameKeyFn(clientOptions, queryKey), queryFn: ({ signal }) => getApiAdminPlayersByName({ ...clientOptions, signal, throwOnError: true }).then(response => response.data as TData) as TData, ...options });
/**
 * All worlds on disk, with stats
 */
export const useGetApiAdminWorlds = <TData = Common.GetApiAdminWorldsDefaultResponse, TError = unknown, TQueryKey extends Array<unknown> = unknown[]>(clientOptions: Options<GetApiAdminWorldsData, true> = {}, queryKey?: TQueryKey, options?: Omit<UseQueryOptions<TData, TError>, "queryKey" | "queryFn">) => useQuery<TData, TError>({ queryKey: Common.UseGetApiAdminWorldsKeyFn(clientOptions, queryKey), queryFn: ({ signal }) => getApiAdminWorlds({ ...clientOptions, signal, throwOnError: true }).then(response => response.data as TData) as TData, ...options });
/**
 * Names of all whitelisted editable YAML config files
 */
export const useGetApiAdminConfigs = <TData = Common.GetApiAdminConfigsDefaultResponse, TError = unknown, TQueryKey extends Array<unknown> = unknown[]>(clientOptions: Options<GetApiAdminConfigsData, true> = {}, queryKey?: TQueryKey, options?: Omit<UseQueryOptions<TData, TError>, "queryKey" | "queryFn">) => useQuery<TData, TError>({ queryKey: Common.UseGetApiAdminConfigsKeyFn(clientOptions, queryKey), queryFn: ({ signal }) => getApiAdminConfigs({ ...clientOptions, signal, throwOnError: true }).then(response => response.data as TData) as TData, ...options });
/**
 * Raw YAML content of a whitelisted config file
 */
export const useGetApiAdminConfigsBy = <TData = Common.GetApiAdminConfigsByDefaultResponse, TError = unknown, TQueryKey extends Array<unknown> = unknown[]>(clientOptions: Options<GetApiAdminConfigsByData, true>, queryKey?: TQueryKey, options?: Omit<UseQueryOptions<TData, TError>, "queryKey" | "queryFn">) => useQuery<TData, TError>({ queryKey: Common.UseGetApiAdminConfigsByKeyFn(clientOptions, queryKey), queryFn: ({ signal }) => getApiAdminConfigsBy({ ...clientOptions, signal, throwOnError: true }).then(response => response.data as TData) as TData, ...options });
/**
 * RPG class definitions, keyed by class name
 */
export const useGetApiAdminClasses = <TData = Common.GetApiAdminClassesDefaultResponse, TError = unknown, TQueryKey extends Array<unknown> = unknown[]>(clientOptions: Options<GetApiAdminClassesData, true> = {}, queryKey?: TQueryKey, options?: Omit<UseQueryOptions<TData, TError>, "queryKey" | "queryFn">) => useQuery<TData, TError>({ queryKey: Common.UseGetApiAdminClassesKeyFn(clientOptions, queryKey), queryFn: ({ signal }) => getApiAdminClasses({ ...clientOptions, signal, throwOnError: true }).then(response => response.data as TData) as TData, ...options });
/**
 * All attack and spell ids
 */
export const useGetApiAdminSkills = <TData = Common.GetApiAdminSkillsDefaultResponse, TError = unknown, TQueryKey extends Array<unknown> = unknown[]>(clientOptions: Options<GetApiAdminSkillsData, true> = {}, queryKey?: TQueryKey, options?: Omit<UseQueryOptions<TData, TError>, "queryKey" | "queryFn">) => useQuery<TData, TError>({ queryKey: Common.UseGetApiAdminSkillsKeyFn(clientOptions, queryKey), queryFn: ({ signal }) => getApiAdminSkills({ ...clientOptions, signal, throwOnError: true }).then(response => response.data as TData) as TData, ...options });
/**
 * Live NPC instances with full animal/combat state
 */
export const useGetApiAdminNpcs = <TData = Common.GetApiAdminNpcsDefaultResponse, TError = unknown, TQueryKey extends Array<unknown> = unknown[]>(clientOptions: Options<GetApiAdminNpcsData, true> = {}, queryKey?: TQueryKey, options?: Omit<UseQueryOptions<TData, TError>, "queryKey" | "queryFn">) => useQuery<TData, TError>({ queryKey: Common.UseGetApiAdminNpcsKeyFn(clientOptions, queryKey), queryFn: ({ signal }) => getApiAdminNpcs({ ...clientOptions, signal, throwOnError: true }).then(response => response.data as TData) as TData, ...options });
/**
 * All registered block definitions
 */
export const useGetApiAdminBlocks = <TData = Common.GetApiAdminBlocksDefaultResponse, TError = unknown, TQueryKey extends Array<unknown> = unknown[]>(clientOptions: Options<GetApiAdminBlocksData, true> = {}, queryKey?: TQueryKey, options?: Omit<UseQueryOptions<TData, TError>, "queryKey" | "queryFn">) => useQuery<TData, TError>({ queryKey: Common.UseGetApiAdminBlocksKeyFn(clientOptions, queryKey), queryFn: ({ signal }) => getApiAdminBlocks({ ...clientOptions, signal, throwOnError: true }).then(response => response.data as TData) as TData, ...options });
/**
 * All registered plain paint colors
 */
export const useGetApiAdminPlainColors = <TData = Common.GetApiAdminPlainColorsDefaultResponse, TError = unknown, TQueryKey extends Array<unknown> = unknown[]>(clientOptions: Options<GetApiAdminPlainColorsData, true> = {}, queryKey?: TQueryKey, options?: Omit<UseQueryOptions<TData, TError>, "queryKey" | "queryFn">) => useQuery<TData, TError>({ queryKey: Common.UseGetApiAdminPlainColorsKeyFn(clientOptions, queryKey), queryFn: ({ signal }) => getApiAdminPlainColors({ ...clientOptions, signal, throwOnError: true }).then(response => response.data as TData) as TData, ...options });
/**
 * All chunk coordinates generated so far (in-memory ∪ persisted)
 */
export const useGetApiAdminChunksDiscovered = <TData = Common.GetApiAdminChunksDiscoveredDefaultResponse, TError = unknown, TQueryKey extends Array<unknown> = unknown[]>(clientOptions: Options<GetApiAdminChunksDiscoveredData, true> = {}, queryKey?: TQueryKey, options?: Omit<UseQueryOptions<TData, TError>, "queryKey" | "queryFn">) => useQuery<TData, TError>({ queryKey: Common.UseGetApiAdminChunksDiscoveredKeyFn(clientOptions, queryKey), queryFn: ({ signal }) => getApiAdminChunksDiscovered({ ...clientOptions, signal, throwOnError: true }).then(response => response.data as TData) as TData, ...options });
/**
 * All instance zones
 */
export const useGetApiAdminInstances = <TData = Common.GetApiAdminInstancesDefaultResponse, TError = unknown, TQueryKey extends Array<unknown> = unknown[]>(clientOptions: Options<GetApiAdminInstancesData, true> = {}, queryKey?: TQueryKey, options?: Omit<UseQueryOptions<TData, TError>, "queryKey" | "queryFn">) => useQuery<TData, TError>({ queryKey: Common.UseGetApiAdminInstancesKeyFn(clientOptions, queryKey), queryFn: ({ signal }) => getApiAdminInstances({ ...clientOptions, signal, throwOnError: true }).then(response => response.data as TData) as TData, ...options });
/**
 * A single instance zone
 */
export const useGetApiAdminInstancesById = <TData = Common.GetApiAdminInstancesByIdDefaultResponse, TError = unknown, TQueryKey extends Array<unknown> = unknown[]>(clientOptions: Options<GetApiAdminInstancesByIdData, true>, queryKey?: TQueryKey, options?: Omit<UseQueryOptions<TData, TError>, "queryKey" | "queryFn">) => useQuery<TData, TError>({ queryKey: Common.UseGetApiAdminInstancesByIdKeyFn(clientOptions, queryKey), queryFn: ({ signal }) => getApiAdminInstancesById({ ...clientOptions, signal, throwOnError: true }).then(response => response.data as TData) as TData, ...options });
/**
 * Non-air blocks in an instance zone, streamed as newline-delimited JSON (application/x-ndjson, one InstanceBlockDto per line), capped at 300000
 */
export const useGetApiAdminInstancesByIdBlocks = <TData = Common.GetApiAdminInstancesByIdBlocksDefaultResponse, TError = unknown, TQueryKey extends Array<unknown> = unknown[]>(clientOptions: Options<GetApiAdminInstancesByIdBlocksData, true>, queryKey?: TQueryKey, options?: Omit<UseQueryOptions<TData, TError>, "queryKey" | "queryFn">) => useQuery<TData, TError>({ queryKey: Common.UseGetApiAdminInstancesByIdBlocksKeyFn(clientOptions, queryKey), queryFn: ({ signal }) => getApiAdminInstancesByIdBlocks({ ...clientOptions, signal, throwOnError: true }).then(response => response.data as TData) as TData, ...options });
/**
 * All scenes (bounded off-world block-structure buffers)
 */
export const useGetApiAdminScenes = <TData = Common.GetApiAdminScenesDefaultResponse, TError = unknown, TQueryKey extends Array<unknown> = unknown[]>(clientOptions: Options<GetApiAdminScenesData, true> = {}, queryKey?: TQueryKey, options?: Omit<UseQueryOptions<TData, TError>, "queryKey" | "queryFn">) => useQuery<TData, TError>({ queryKey: Common.UseGetApiAdminScenesKeyFn(clientOptions, queryKey), queryFn: ({ signal }) => getApiAdminScenes({ ...clientOptions, signal, throwOnError: true }).then(response => response.data as TData) as TData, ...options });
/**
 * A single scene's metadata
 */
export const useGetApiAdminScenesById = <TData = Common.GetApiAdminScenesByIdDefaultResponse, TError = unknown, TQueryKey extends Array<unknown> = unknown[]>(clientOptions: Options<GetApiAdminScenesByIdData, true>, queryKey?: TQueryKey, options?: Omit<UseQueryOptions<TData, TError>, "queryKey" | "queryFn">) => useQuery<TData, TError>({ queryKey: Common.UseGetApiAdminScenesByIdKeyFn(clientOptions, queryKey), queryFn: ({ signal }) => getApiAdminScenesById({ ...clientOptions, signal, throwOnError: true }).then(response => response.data as TData) as TData, ...options });
/**
 * Scene block/state/extraState buffers as a binary blob: 3×4-byte big-endian dimensions (width,height,depth) followed by the blocks byte array, then the states byte array, then the extraStates byte array (wire-index-per-byte, 0 = AIR)
 */
export const useGetApiAdminScenesByIdBlocksRaw = <TData = Common.GetApiAdminScenesByIdBlocksRawDefaultResponse, TError = unknown, TQueryKey extends Array<unknown> = unknown[]>(clientOptions: Options<GetApiAdminScenesByIdBlocksRawData, true>, queryKey?: TQueryKey, options?: Omit<UseQueryOptions<TData, TError>, "queryKey" | "queryFn">) => useQuery<TData, TError>({ queryKey: Common.UseGetApiAdminScenesByIdBlocksRawKeyFn(clientOptions, queryKey), queryFn: ({ signal }) => getApiAdminScenesByIdBlocksRaw({ ...clientOptions, signal, throwOnError: true }).then(response => response.data as TData) as TData, ...options });
/**
 * Fractional (lego/plate/arch) block entities placed in this scene — not carried by the blocks/raw binary blob, so the client loads them separately on scene open
 */
export const useGetApiAdminScenesByIdEntities = <TData = Common.GetApiAdminScenesByIdEntitiesDefaultResponse, TError = unknown, TQueryKey extends Array<unknown> = unknown[]>(clientOptions: Options<GetApiAdminScenesByIdEntitiesData, true>, queryKey?: TQueryKey, options?: Omit<UseQueryOptions<TData, TError>, "queryKey" | "queryFn">) => useQuery<TData, TError>({ queryKey: Common.UseGetApiAdminScenesByIdEntitiesKeyFn(clientOptions, queryKey), queryFn: ({ signal }) => getApiAdminScenesByIdEntities({ ...clientOptions, signal, throwOnError: true }).then(response => response.data as TData) as TData, ...options });
/**
 * NPC type definitions (codex info), keyed by type id
 */
export const useGetApiAdminNpcTypes = <TData = Common.GetApiAdminNpcTypesDefaultResponse, TError = unknown, TQueryKey extends Array<unknown> = unknown[]>(clientOptions: Options<GetApiAdminNpcTypesData, true> = {}, queryKey?: TQueryKey, options?: Omit<UseQueryOptions<TData, TError>, "queryKey" | "queryFn">) => useQuery<TData, TError>({ queryKey: Common.UseGetApiAdminNpcTypesKeyFn(clientOptions, queryKey), queryFn: ({ signal }) => getApiAdminNpcTypes({ ...clientOptions, signal, throwOnError: true }).then(response => response.data as TData) as TData, ...options });
/**
 * Item definitions, keyed by item type id
 */
export const useGetApiAdminItems = <TData = Common.GetApiAdminItemsDefaultResponse, TError = unknown, TQueryKey extends Array<unknown> = unknown[]>(clientOptions: Options<GetApiAdminItemsData, true> = {}, queryKey?: TQueryKey, options?: Omit<UseQueryOptions<TData, TError>, "queryKey" | "queryFn">) => useQuery<TData, TError>({ queryKey: Common.UseGetApiAdminItemsKeyFn(clientOptions, queryKey), queryFn: ({ signal }) => getApiAdminItems({ ...clientOptions, signal, throwOnError: true }).then(response => response.data as TData) as TData, ...options });
/**
 * A JSON Schema file (data/config/schemas*.schema.json) for the config editor
 */
export const useGetApiAdminSchemasByFilename = <TData = Common.GetApiAdminSchemasByFilenameDefaultResponse, TError = unknown, TQueryKey extends Array<unknown> = unknown[]>(clientOptions: Options<GetApiAdminSchemasByFilenameData, true>, queryKey?: TQueryKey, options?: Omit<UseQueryOptions<TData, TError>, "queryKey" | "queryFn">) => useQuery<TData, TError>({ queryKey: Common.UseGetApiAdminSchemasByFilenameKeyFn(clientOptions, queryKey), queryFn: ({ signal }) => getApiAdminSchemasByFilename({ ...clientOptions, signal, throwOnError: true }).then(response => response.data as TData) as TData, ...options });
/**
 * Defaults the world simulator admin UI prefills its editors with
 */
export const useGetApiAdminSimulationDefaults = <TData = Common.GetApiAdminSimulationDefaultsDefaultResponse, TError = unknown, TQueryKey extends Array<unknown> = unknown[]>(clientOptions: Options<GetApiAdminSimulationDefaultsData, true> = {}, queryKey?: TQueryKey, options?: Omit<UseQueryOptions<TData, TError>, "queryKey" | "queryFn">) => useQuery<TData, TError>({ queryKey: Common.UseGetApiAdminSimulationDefaultsKeyFn(clientOptions, queryKey), queryFn: ({ signal }) => getApiAdminSimulationDefaults({ ...clientOptions, signal, throwOnError: true }).then(response => response.data as TData) as TData, ...options });
/**
 * Binary-encoded chunk data (protocol.ServerMessage.ChunkData wire format). Not a JSON API — used by the game client, not by TanStack Query hooks.
 */
export const useGetApiChunksByCxByCz = <TData = Common.GetApiChunksByCxByCzDefaultResponse, TError = unknown, TQueryKey extends Array<unknown> = unknown[]>(clientOptions: Options<GetApiChunksByCxByCzData, true>, queryKey?: TQueryKey, options?: Omit<UseQueryOptions<TData, TError>, "queryKey" | "queryFn">) => useQuery<TData, TError>({ queryKey: Common.UseGetApiChunksByCxByCzKeyFn(clientOptions, queryKey), queryFn: ({ signal }) => getApiChunksByCxByCz({ ...clientOptions, signal, throwOnError: true }).then(response => response.data as TData) as TData, ...options });
/**
 * Player characters (name + id) linked to an account email
 */
export const useGetApiPlayersByEmailByEmail = <TData = Common.GetApiPlayersByEmailByEmailDefaultResponse, TError = unknown, TQueryKey extends Array<unknown> = unknown[]>(clientOptions: Options<GetApiPlayersByEmailByEmailData, true>, queryKey?: TQueryKey, options?: Omit<UseQueryOptions<TData, TError>, "queryKey" | "queryFn">) => useQuery<TData, TError>({ queryKey: Common.UseGetApiPlayersByEmailByEmailKeyFn(clientOptions, queryKey), queryFn: ({ signal }) => getApiPlayersByEmailByEmail({ ...clientOptions, signal, throwOnError: true }).then(response => response.data as TData) as TData, ...options });
/**
 * Create/reuse an account by email when auth is disabled (auth.provider=none)
 */
export const usePostAuthNoauthLogin = <TData = Common.PostAuthNoauthLoginMutationResult, TError = unknown, TQueryKey extends Array<unknown> = unknown[], TContext = unknown>(mutationKey?: TQueryKey, options?: Omit<UseMutationOptions<TData, TError, Options<PostAuthNoauthLoginData, true>, TContext>, "mutationKey" | "mutationFn">) => useMutation<TData, TError, Options<PostAuthNoauthLoginData, true>, TContext>({ mutationKey: Common.UsePostAuthNoauthLoginKeyFn(mutationKey), mutationFn: clientOptions => postAuthNoauthLogin({ ...clientOptions, throwOnError: true }) as unknown as Promise<TData>, ...options });
export const usePostApiAssetsReload = <TData = Common.PostApiAssetsReloadMutationResult, TError = unknown, TQueryKey extends Array<unknown> = unknown[], TContext = unknown>(mutationKey?: TQueryKey, options?: Omit<UseMutationOptions<TData, TError, Options<PostApiAssetsReloadData, true>, TContext>, "mutationKey" | "mutationFn">) => useMutation<TData, TError, Options<PostApiAssetsReloadData, true>, TContext>({ mutationKey: Common.UsePostApiAssetsReloadKeyFn(mutationKey), mutationFn: clientOptions => postApiAssetsReload({ ...clientOptions, throwOnError: true }) as unknown as Promise<TData>, ...options });
/**
 * Change a player's skin
 */
export const usePutApiPlayerByIdSkin = <TData = Common.PutApiPlayerByIdSkinMutationResult, TError = unknown, TQueryKey extends Array<unknown> = unknown[], TContext = unknown>(mutationKey?: TQueryKey, options?: Omit<UseMutationOptions<TData, TError, Options<PutApiPlayerByIdSkinData, true>, TContext>, "mutationKey" | "mutationFn">) => useMutation<TData, TError, Options<PutApiPlayerByIdSkinData, true>, TContext>({ mutationKey: Common.UsePutApiPlayerByIdSkinKeyFn(mutationKey), mutationFn: clientOptions => putApiPlayerByIdSkin({ ...clientOptions, throwOnError: true }) as unknown as Promise<TData>, ...options });
/**
 * Upload a player screenshot (base64 PNG, optionally as a data: URI)
 */
export const usePostApiPlayerByIdScreenshots = <TData = Common.PostApiPlayerByIdScreenshotsMutationResult, TError = unknown, TQueryKey extends Array<unknown> = unknown[], TContext = unknown>(mutationKey?: TQueryKey, options?: Omit<UseMutationOptions<TData, TError, Options<PostApiPlayerByIdScreenshotsData, true>, TContext>, "mutationKey" | "mutationFn">) => useMutation<TData, TError, Options<PostApiPlayerByIdScreenshotsData, true>, TContext>({ mutationKey: Common.UsePostApiPlayerByIdScreenshotsKeyFn(mutationKey), mutationFn: clientOptions => postApiPlayerByIdScreenshots({ ...clientOptions, throwOnError: true }) as unknown as Promise<TData>, ...options });
/**
 * Create a new (non-RPG) character
 */
export const usePostApiCharacterCreate = <TData = Common.PostApiCharacterCreateMutationResult, TError = unknown, TQueryKey extends Array<unknown> = unknown[], TContext = unknown>(mutationKey?: TQueryKey, options?: Omit<UseMutationOptions<TData, TError, Options<PostApiCharacterCreateData, true>, TContext>, "mutationKey" | "mutationFn">) => useMutation<TData, TError, Options<PostApiCharacterCreateData, true>, TContext>({ mutationKey: Common.UsePostApiCharacterCreateKeyFn(mutationKey), mutationFn: clientOptions => postApiCharacterCreate({ ...clientOptions, throwOnError: true }) as unknown as Promise<TData>, ...options });
/**
 * Create a new RPG character (point-buy base stats + class)
 */
export const usePostApiCharacterRpgcreate = <TData = Common.PostApiCharacterRpgcreateMutationResult, TError = unknown, TQueryKey extends Array<unknown> = unknown[], TContext = unknown>(mutationKey?: TQueryKey, options?: Omit<UseMutationOptions<TData, TError, Options<PostApiCharacterRpgcreateData, true>, TContext>, "mutationKey" | "mutationFn">) => useMutation<TData, TError, Options<PostApiCharacterRpgcreateData, true>, TContext>({ mutationKey: Common.UsePostApiCharacterRpgcreateKeyFn(mutationKey), mutationFn: clientOptions => postApiCharacterRpgcreate({ ...clientOptions, throwOnError: true }) as unknown as Promise<TData>, ...options });
/**
 * Clears the cached Blender conversion (scene tree + all OBJ/bbmodel exports) for a .blend file
 */
export const useDeleteApiGameAssetsBlendCacheBy = <TData = Common.DeleteApiGameAssetsBlendCacheByMutationResult, TError = unknown, TQueryKey extends Array<unknown> = unknown[], TContext = unknown>(mutationKey?: TQueryKey, options?: Omit<UseMutationOptions<TData, TError, Options<DeleteApiGameAssetsBlendCacheByData, true>, TContext>, "mutationKey" | "mutationFn">) => useMutation<TData, TError, Options<DeleteApiGameAssetsBlendCacheByData, true>, TContext>({ mutationKey: Common.UseDeleteApiGameAssetsBlendCacheByKeyFn(mutationKey), mutationFn: clientOptions => deleteApiGameAssetsBlendCacheBy({ ...clientOptions, throwOnError: true }) as unknown as Promise<TData>, ...options });
/**
 * Trigger a pitchfork server restart
 */
export const usePostApiAdminRestart = <TData = Common.PostApiAdminRestartMutationResult, TError = unknown, TQueryKey extends Array<unknown> = unknown[], TContext = unknown>(mutationKey?: TQueryKey, options?: Omit<UseMutationOptions<TData, TError, Options<PostApiAdminRestartData, true>, TContext>, "mutationKey" | "mutationFn">) => useMutation<TData, TError, Options<PostApiAdminRestartData, true>, TContext>({ mutationKey: Common.UsePostApiAdminRestartKeyFn(mutationKey), mutationFn: clientOptions => postApiAdminRestart({ ...clientOptions, throwOnError: true }) as unknown as Promise<TData>, ...options });
/**
 * Set the in-game time of day
 */
export const usePutApiAdminGametime = <TData = Common.PutApiAdminGametimeMutationResult, TError = unknown, TQueryKey extends Array<unknown> = unknown[], TContext = unknown>(mutationKey?: TQueryKey, options?: Omit<UseMutationOptions<TData, TError, Options<PutApiAdminGametimeData, true>, TContext>, "mutationKey" | "mutationFn">) => useMutation<TData, TError, Options<PutApiAdminGametimeData, true>, TContext>({ mutationKey: Common.UsePutApiAdminGametimeKeyFn(mutationKey), mutationFn: clientOptions => putApiAdminGametime({ ...clientOptions, throwOnError: true }) as unknown as Promise<TData>, ...options });
/**
 * Create a local/no-auth user account
 */
export const usePostApiAdminUsers = <TData = Common.PostApiAdminUsersMutationResult, TError = unknown, TQueryKey extends Array<unknown> = unknown[], TContext = unknown>(mutationKey?: TQueryKey, options?: Omit<UseMutationOptions<TData, TError, Options<PostApiAdminUsersData, true>, TContext>, "mutationKey" | "mutationFn">) => useMutation<TData, TError, Options<PostApiAdminUsersData, true>, TContext>({ mutationKey: Common.UsePostApiAdminUsersKeyFn(mutationKey), mutationFn: clientOptions => postApiAdminUsers({ ...clientOptions, throwOnError: true }) as unknown as Promise<TData>, ...options });
/**
 * Delete a user account
 */
export const useDeleteApiAdminUsersByEmail = <TData = Common.DeleteApiAdminUsersByEmailMutationResult, TError = unknown, TQueryKey extends Array<unknown> = unknown[], TContext = unknown>(mutationKey?: TQueryKey, options?: Omit<UseMutationOptions<TData, TError, Options<DeleteApiAdminUsersByEmailData, true>, TContext>, "mutationKey" | "mutationFn">) => useMutation<TData, TError, Options<DeleteApiAdminUsersByEmailData, true>, TContext>({ mutationKey: Common.UseDeleteApiAdminUsersByEmailKeyFn(mutationKey), mutationFn: clientOptions => deleteApiAdminUsersByEmail({ ...clientOptions, throwOnError: true }) as unknown as Promise<TData>, ...options });
/**
 * Update a local user's display name/groups
 */
export const usePutApiAdminUsersByEmail = <TData = Common.PutApiAdminUsersByEmailMutationResult, TError = unknown, TQueryKey extends Array<unknown> = unknown[], TContext = unknown>(mutationKey?: TQueryKey, options?: Omit<UseMutationOptions<TData, TError, Options<PutApiAdminUsersByEmailData, true>, TContext>, "mutationKey" | "mutationFn">) => useMutation<TData, TError, Options<PutApiAdminUsersByEmailData, true>, TContext>({ mutationKey: Common.UsePutApiAdminUsersByEmailKeyFn(mutationKey), mutationFn: clientOptions => putApiAdminUsersByEmail({ ...clientOptions, throwOnError: true }) as unknown as Promise<TData>, ...options });
/**
 * Overwrite a player's saved key bindings
 */
export const usePutApiAdminPlayersByNameKeybindings = <TData = Common.PutApiAdminPlayersByNameKeybindingsMutationResult, TError = unknown, TQueryKey extends Array<unknown> = unknown[], TContext = unknown>(mutationKey?: TQueryKey, options?: Omit<UseMutationOptions<TData, TError, Options<PutApiAdminPlayersByNameKeybindingsData, true>, TContext>, "mutationKey" | "mutationFn">) => useMutation<TData, TError, Options<PutApiAdminPlayersByNameKeybindingsData, true>, TContext>({ mutationKey: Common.UsePutApiAdminPlayersByNameKeybindingsKeyFn(mutationKey), mutationFn: clientOptions => putApiAdminPlayersByNameKeybindings({ ...clientOptions, throwOnError: true }) as unknown as Promise<TData>, ...options });
/**
 * Partially update a player's preferences (only given fields change)
 */
export const usePutApiAdminPlayersByNamePreferences = <TData = Common.PutApiAdminPlayersByNamePreferencesMutationResult, TError = unknown, TQueryKey extends Array<unknown> = unknown[], TContext = unknown>(mutationKey?: TQueryKey, options?: Omit<UseMutationOptions<TData, TError, Options<PutApiAdminPlayersByNamePreferencesData, true>, TContext>, "mutationKey" | "mutationFn">) => useMutation<TData, TError, Options<PutApiAdminPlayersByNamePreferencesData, true>, TContext>({ mutationKey: Common.UsePutApiAdminPlayersByNamePreferencesKeyFn(mutationKey), mutationFn: clientOptions => putApiAdminPlayersByNamePreferences({ ...clientOptions, throwOnError: true }) as unknown as Promise<TData>, ...options });
/**
 * Rename a player
 */
export const usePostApiAdminPlayersByNameRename = <TData = Common.PostApiAdminPlayersByNameRenameMutationResult, TError = unknown, TQueryKey extends Array<unknown> = unknown[], TContext = unknown>(mutationKey?: TQueryKey, options?: Omit<UseMutationOptions<TData, TError, Options<PostApiAdminPlayersByNameRenameData, true>, TContext>, "mutationKey" | "mutationFn">) => useMutation<TData, TError, Options<PostApiAdminPlayersByNameRenameData, true>, TContext>({ mutationKey: Common.UsePostApiAdminPlayersByNameRenameKeyFn(mutationKey), mutationFn: clientOptions => postApiAdminPlayersByNameRename({ ...clientOptions, throwOnError: true }) as unknown as Promise<TData>, ...options });
/**
 * Partially update a player's RPG class/base stats
 */
export const usePutApiAdminPlayersByNameRpg = <TData = Common.PutApiAdminPlayersByNameRpgMutationResult, TError = unknown, TQueryKey extends Array<unknown> = unknown[], TContext = unknown>(mutationKey?: TQueryKey, options?: Omit<UseMutationOptions<TData, TError, Options<PutApiAdminPlayersByNameRpgData, true>, TContext>, "mutationKey" | "mutationFn">) => useMutation<TData, TError, Options<PutApiAdminPlayersByNameRpgData, true>, TContext>({ mutationKey: Common.UsePutApiAdminPlayersByNameRpgKeyFn(mutationKey), mutationFn: clientOptions => putApiAdminPlayersByNameRpg({ ...clientOptions, throwOnError: true }) as unknown as Promise<TData>, ...options });
/**
 * Create a new world
 */
export const usePostApiAdminWorlds = <TData = Common.PostApiAdminWorldsMutationResult, TError = unknown, TQueryKey extends Array<unknown> = unknown[], TContext = unknown>(mutationKey?: TQueryKey, options?: Omit<UseMutationOptions<TData, TError, Options<PostApiAdminWorldsData, true>, TContext>, "mutationKey" | "mutationFn">) => useMutation<TData, TError, Options<PostApiAdminWorldsData, true>, TContext>({ mutationKey: Common.UsePostApiAdminWorldsKeyFn(mutationKey), mutationFn: clientOptions => postApiAdminWorlds({ ...clientOptions, throwOnError: true }) as unknown as Promise<TData>, ...options });
/**
 * Overwrite a whitelisted config file's raw YAML content
 */
export const usePutApiAdminConfigsBy = <TData = Common.PutApiAdminConfigsByMutationResult, TError = unknown, TQueryKey extends Array<unknown> = unknown[], TContext = unknown>(mutationKey?: TQueryKey, options?: Omit<UseMutationOptions<TData, TError, Options<PutApiAdminConfigsByData, true>, TContext>, "mutationKey" | "mutationFn">) => useMutation<TData, TError, Options<PutApiAdminConfigsByData, true>, TContext>({ mutationKey: Common.UsePutApiAdminConfigsByKeyFn(mutationKey), mutationFn: clientOptions => putApiAdminConfigsBy({ ...clientOptions, throwOnError: true }) as unknown as Promise<TData>, ...options });
/**
 * Create an instance zone covering already-generated chunks
 */
export const usePostApiAdminInstances = <TData = Common.PostApiAdminInstancesMutationResult, TError = unknown, TQueryKey extends Array<unknown> = unknown[], TContext = unknown>(mutationKey?: TQueryKey, options?: Omit<UseMutationOptions<TData, TError, Options<PostApiAdminInstancesData, true>, TContext>, "mutationKey" | "mutationFn">) => useMutation<TData, TError, Options<PostApiAdminInstancesData, true>, TContext>({ mutationKey: Common.UsePostApiAdminInstancesKeyFn(mutationKey), mutationFn: clientOptions => postApiAdminInstances({ ...clientOptions, throwOnError: true }) as unknown as Promise<TData>, ...options });
/**
 * Delete an instance zone
 */
export const useDeleteApiAdminInstancesById = <TData = Common.DeleteApiAdminInstancesByIdMutationResult, TError = unknown, TQueryKey extends Array<unknown> = unknown[], TContext = unknown>(mutationKey?: TQueryKey, options?: Omit<UseMutationOptions<TData, TError, Options<DeleteApiAdminInstancesByIdData, true>, TContext>, "mutationKey" | "mutationFn">) => useMutation<TData, TError, Options<DeleteApiAdminInstancesByIdData, true>, TContext>({ mutationKey: Common.UseDeleteApiAdminInstancesByIdKeyFn(mutationKey), mutationFn: clientOptions => deleteApiAdminInstancesById({ ...clientOptions, throwOnError: true }) as unknown as Promise<TData>, ...options });
/**
 * Rename an instance zone
 */
export const usePutApiAdminInstancesById = <TData = Common.PutApiAdminInstancesByIdMutationResult, TError = unknown, TQueryKey extends Array<unknown> = unknown[], TContext = unknown>(mutationKey?: TQueryKey, options?: Omit<UseMutationOptions<TData, TError, Options<PutApiAdminInstancesByIdData, true>, TContext>, "mutationKey" | "mutationFn">) => useMutation<TData, TError, Options<PutApiAdminInstancesByIdData, true>, TContext>({ mutationKey: Common.UsePutApiAdminInstancesByIdKeyFn(mutationKey), mutationFn: clientOptions => putApiAdminInstancesById({ ...clientOptions, throwOnError: true }) as unknown as Promise<TData>, ...options });
/**
 * Update an instance zone's Y bounds
 */
export const usePutApiAdminInstancesByIdBounds = <TData = Common.PutApiAdminInstancesByIdBoundsMutationResult, TError = unknown, TQueryKey extends Array<unknown> = unknown[], TContext = unknown>(mutationKey?: TQueryKey, options?: Omit<UseMutationOptions<TData, TError, Options<PutApiAdminInstancesByIdBoundsData, true>, TContext>, "mutationKey" | "mutationFn">) => useMutation<TData, TError, Options<PutApiAdminInstancesByIdBoundsData, true>, TContext>({ mutationKey: Common.UsePutApiAdminInstancesByIdBoundsKeyFn(mutationKey), mutationFn: clientOptions => putApiAdminInstancesByIdBounds({ ...clientOptions, throwOnError: true }) as unknown as Promise<TData>, ...options });
/**
 * Enable/disable an instance zone
 */
export const usePutApiAdminInstancesByIdEnabled = <TData = Common.PutApiAdminInstancesByIdEnabledMutationResult, TError = unknown, TQueryKey extends Array<unknown> = unknown[], TContext = unknown>(mutationKey?: TQueryKey, options?: Omit<UseMutationOptions<TData, TError, Options<PutApiAdminInstancesByIdEnabledData, true>, TContext>, "mutationKey" | "mutationFn">) => useMutation<TData, TError, Options<PutApiAdminInstancesByIdEnabledData, true>, TContext>({ mutationKey: Common.UsePutApiAdminInstancesByIdEnabledKeyFn(mutationKey), mutationFn: clientOptions => putApiAdminInstancesByIdEnabled({ ...clientOptions, throwOnError: true }) as unknown as Promise<TData>, ...options });
/**
 * Update the set of chunks covered by an instance zone
 */
export const usePutApiAdminInstancesByIdChunks = <TData = Common.PutApiAdminInstancesByIdChunksMutationResult, TError = unknown, TQueryKey extends Array<unknown> = unknown[], TContext = unknown>(mutationKey?: TQueryKey, options?: Omit<UseMutationOptions<TData, TError, Options<PutApiAdminInstancesByIdChunksData, true>, TContext>, "mutationKey" | "mutationFn">) => useMutation<TData, TError, Options<PutApiAdminInstancesByIdChunksData, true>, TContext>({ mutationKey: Common.UsePutApiAdminInstancesByIdChunksKeyFn(mutationKey), mutationFn: clientOptions => putApiAdminInstancesByIdChunks({ ...clientOptions, throwOnError: true }) as unknown as Promise<TData>, ...options });
/**
 * Update an instance zone's clip planes and shortcut bar layout
 */
export const usePutApiAdminInstancesByIdLayout = <TData = Common.PutApiAdminInstancesByIdLayoutMutationResult, TError = unknown, TQueryKey extends Array<unknown> = unknown[], TContext = unknown>(mutationKey?: TQueryKey, options?: Omit<UseMutationOptions<TData, TError, Options<PutApiAdminInstancesByIdLayoutData, true>, TContext>, "mutationKey" | "mutationFn">) => useMutation<TData, TError, Options<PutApiAdminInstancesByIdLayoutData, true>, TContext>({ mutationKey: Common.UsePutApiAdminInstancesByIdLayoutKeyFn(mutationKey), mutationFn: clientOptions => putApiAdminInstancesByIdLayout({ ...clientOptions, throwOnError: true }) as unknown as Promise<TData>, ...options });
/**
 * Create a scene
 */
export const usePostApiAdminScenes = <TData = Common.PostApiAdminScenesMutationResult, TError = unknown, TQueryKey extends Array<unknown> = unknown[], TContext = unknown>(mutationKey?: TQueryKey, options?: Omit<UseMutationOptions<TData, TError, Options<PostApiAdminScenesData, true>, TContext>, "mutationKey" | "mutationFn">) => useMutation<TData, TError, Options<PostApiAdminScenesData, true>, TContext>({ mutationKey: Common.UsePostApiAdminScenesKeyFn(mutationKey), mutationFn: clientOptions => postApiAdminScenes({ ...clientOptions, throwOnError: true }) as unknown as Promise<TData>, ...options });
/**
 * Delete a scene
 */
export const useDeleteApiAdminScenesById = <TData = Common.DeleteApiAdminScenesByIdMutationResult, TError = unknown, TQueryKey extends Array<unknown> = unknown[], TContext = unknown>(mutationKey?: TQueryKey, options?: Omit<UseMutationOptions<TData, TError, Options<DeleteApiAdminScenesByIdData, true>, TContext>, "mutationKey" | "mutationFn">) => useMutation<TData, TError, Options<DeleteApiAdminScenesByIdData, true>, TContext>({ mutationKey: Common.UseDeleteApiAdminScenesByIdKeyFn(mutationKey), mutationFn: clientOptions => deleteApiAdminScenesById({ ...clientOptions, throwOnError: true }) as unknown as Promise<TData>, ...options });
/**
 * Rename a scene
 */
export const usePutApiAdminScenesById = <TData = Common.PutApiAdminScenesByIdMutationResult, TError = unknown, TQueryKey extends Array<unknown> = unknown[], TContext = unknown>(mutationKey?: TQueryKey, options?: Omit<UseMutationOptions<TData, TError, Options<PutApiAdminScenesByIdData, true>, TContext>, "mutationKey" | "mutationFn">) => useMutation<TData, TError, Options<PutApiAdminScenesByIdData, true>, TContext>({ mutationKey: Common.UsePutApiAdminScenesByIdKeyFn(mutationKey), mutationFn: clientOptions => putApiAdminScenesById({ ...clientOptions, throwOnError: true }) as unknown as Promise<TData>, ...options });
/**
 * Duplicate a scene (copies name, dimensions, and blocks)
 */
export const usePostApiAdminScenesByIdDuplicate = <TData = Common.PostApiAdminScenesByIdDuplicateMutationResult, TError = unknown, TQueryKey extends Array<unknown> = unknown[], TContext = unknown>(mutationKey?: TQueryKey, options?: Omit<UseMutationOptions<TData, TError, Options<PostApiAdminScenesByIdDuplicateData, true>, TContext>, "mutationKey" | "mutationFn">) => useMutation<TData, TError, Options<PostApiAdminScenesByIdDuplicateData, true>, TContext>({ mutationKey: Common.UsePostApiAdminScenesByIdDuplicateKeyFn(mutationKey), mutationFn: clientOptions => postApiAdminScenesByIdDuplicate({ ...clientOptions, throwOnError: true }) as unknown as Promise<TData>, ...options });
/**
 * Resize a scene
 */
export const usePutApiAdminScenesByIdDimensions = <TData = Common.PutApiAdminScenesByIdDimensionsMutationResult, TError = unknown, TQueryKey extends Array<unknown> = unknown[], TContext = unknown>(mutationKey?: TQueryKey, options?: Omit<UseMutationOptions<TData, TError, Options<PutApiAdminScenesByIdDimensionsData, true>, TContext>, "mutationKey" | "mutationFn">) => useMutation<TData, TError, Options<PutApiAdminScenesByIdDimensionsData, true>, TContext>({ mutationKey: Common.UsePutApiAdminScenesByIdDimensionsKeyFn(mutationKey), mutationFn: clientOptions => putApiAdminScenesByIdDimensions({ ...clientOptions, throwOnError: true }) as unknown as Promise<TData>, ...options });
/**
 * Update a scene's shortcut bar layout
 */
export const usePutApiAdminScenesByIdLayout = <TData = Common.PutApiAdminScenesByIdLayoutMutationResult, TError = unknown, TQueryKey extends Array<unknown> = unknown[], TContext = unknown>(mutationKey?: TQueryKey, options?: Omit<UseMutationOptions<TData, TError, Options<PutApiAdminScenesByIdLayoutData, true>, TContext>, "mutationKey" | "mutationFn">) => useMutation<TData, TError, Options<PutApiAdminScenesByIdLayoutData, true>, TContext>({ mutationKey: Common.UsePutApiAdminScenesByIdLayoutKeyFn(mutationKey), mutationFn: clientOptions => putApiAdminScenesByIdLayout({ ...clientOptions, throwOnError: true }) as unknown as Promise<TData>, ...options });
