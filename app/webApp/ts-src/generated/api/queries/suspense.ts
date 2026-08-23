// generated with @7nohe/openapi-react-query-codegen@3.0.2 

import { useSuspenseQuery, type UseSuspenseQueryOptions } from "@tanstack/react-query";
import { getApiAdminBlocks, getApiAdminChunksDiscovered, getApiAdminClasses, getApiAdminConfigs, getApiAdminConfigsBy, getApiAdminInstances, getApiAdminInstancesById, getApiAdminInstancesByIdBlocks, getApiAdminItems, getApiAdminNpcTypes, getApiAdminNpcs, getApiAdminPlainColors, getApiAdminPlayers, getApiAdminPlayersByName, getApiAdminScenes, getApiAdminScenesById, getApiAdminScenesByIdBlocksRaw, getApiAdminScenesByIdEntities, getApiAdminSchemasByFilename, getApiAdminSimulationDefaults, getApiAdminSkills, getApiAdminStatus, getApiAdminUsers, getApiAdminWorlds, getApiAdminWsInstancesById, getApiAdminWsNpcs, getApiAdminWsScenesById, getApiAdminWsSimulation, getApiArmors, getApiAssetsManifest, getApiAttacks, getApiAuthConfig, getApiAutocompleteByCommandIdByArgIndex, getApiBiomes, getApiChunksByCxByCz, getApiClasses, getApiGameAssets, getApiGameAssetsBbmodelExportBy, getApiGameAssetsBlendPreviewBy, getApiGameAssetsBlendSceneBy, getApiGameAssetsFileBy, getApiI18nByLocale, getApiItemsMeta, getApiKeybindings, getApiLayoutRegistry, getApiMacrosContext, getApiMapHouses, getApiMapRoadRaster, getApiMapRoadRasterPng, getApiMapRoads, getApiMapStaircases, getApiMapState, getApiMapTerrain, getApiMapTerrainRasterPng, getApiMapVoronoi, getApiMapVoronoiBorders, getApiPlayerByIdArmors, getApiPlayerByIdHands, getApiPlayerByIdOwned, getApiPlayerByIdRpg, getApiPlayerByIdSkin, getApiPlayersByEmailByEmail, getApiPlayersNames, getApiQuests, getApiServerInfo, getApiSkins, getApiSkinsByNameConfig, getApiSpells, getApiTools, getApiWeapons, type Options } from "../requests/sdk.gen";
import type { GetApiAdminBlocksData, GetApiAdminChunksDiscoveredData, GetApiAdminClassesData, GetApiAdminConfigsByData, GetApiAdminConfigsData, GetApiAdminInstancesByIdBlocksData, GetApiAdminInstancesByIdData, GetApiAdminInstancesData, GetApiAdminItemsData, GetApiAdminNpcTypesData, GetApiAdminNpcsData, GetApiAdminPlainColorsData, GetApiAdminPlayersByNameData, GetApiAdminPlayersData, GetApiAdminScenesByIdBlocksRawData, GetApiAdminScenesByIdData, GetApiAdminScenesByIdEntitiesData, GetApiAdminScenesData, GetApiAdminSchemasByFilenameData, GetApiAdminSimulationDefaultsData, GetApiAdminSkillsData, GetApiAdminStatusData, GetApiAdminUsersData, GetApiAdminWorldsData, GetApiAdminWsInstancesByIdData, GetApiAdminWsNpcsData, GetApiAdminWsScenesByIdData, GetApiAdminWsSimulationData, GetApiArmorsData, GetApiAssetsManifestData, GetApiAttacksData, GetApiAuthConfigData, GetApiAutocompleteByCommandIdByArgIndexData, GetApiBiomesData, GetApiChunksByCxByCzData, GetApiClassesData, GetApiGameAssetsBbmodelExportByData, GetApiGameAssetsBlendPreviewByData, GetApiGameAssetsBlendSceneByData, GetApiGameAssetsData, GetApiGameAssetsFileByData, GetApiI18nByLocaleData, GetApiItemsMetaData, GetApiKeybindingsData, GetApiLayoutRegistryData, GetApiMacrosContextData, GetApiMapHousesData, GetApiMapRoadRasterData, GetApiMapRoadRasterPngData, GetApiMapRoadsData, GetApiMapStaircasesData, GetApiMapStateData, GetApiMapTerrainData, GetApiMapTerrainRasterPngData, GetApiMapVoronoiBordersData, GetApiMapVoronoiData, GetApiPlayerByIdArmorsData, GetApiPlayerByIdHandsData, GetApiPlayerByIdOwnedData, GetApiPlayerByIdRpgData, GetApiPlayerByIdSkinData, GetApiPlayersByEmailByEmailData, GetApiPlayersNamesData, GetApiQuestsData, GetApiServerInfoData, GetApiSkinsByNameConfigData, GetApiSkinsData, GetApiSpellsData, GetApiToolsData, GetApiWeaponsData } from "../requests/types.gen";
import * as Common from "./common";

/**
 * Active auth provider, used by the client to pick the right login UI
 */
export const useGetApiAuthConfigSuspense = <TData = NonNullable<Common.GetApiAuthConfigDefaultResponse>, TError = unknown, TQueryKey extends Array<unknown> = unknown[]>(clientOptions: Options<GetApiAuthConfigData, true> = {}, queryKey?: TQueryKey, options?: Omit<UseSuspenseQueryOptions<TData, TError>, "queryKey" | "queryFn">) => useSuspenseQuery<TData, TError>({ queryKey: Common.UseGetApiAuthConfigKeyFn(clientOptions, queryKey), queryFn: ({ signal }) => getApiAuthConfig({ ...clientOptions, signal, throwOnError: true }).then(response => response.data as TData) as TData, ...options });
export const useGetApiAssetsManifestSuspense = <TData = NonNullable<Common.GetApiAssetsManifestDefaultResponse>, TError = unknown, TQueryKey extends Array<unknown> = unknown[]>(clientOptions: Options<GetApiAssetsManifestData, true> = {}, queryKey?: TQueryKey, options?: Omit<UseSuspenseQueryOptions<TData, TError>, "queryKey" | "queryFn">) => useSuspenseQuery<TData, TError>({ queryKey: Common.UseGetApiAssetsManifestKeyFn(clientOptions, queryKey), queryFn: ({ signal }) => getApiAssetsManifest({ ...clientOptions, signal, throwOnError: true }).then(response => response.data as TData) as TData, ...options });
export const useGetApiAdminWsNpcsSuspense = <TData = NonNullable<Common.GetApiAdminWsNpcsDefaultResponse>, TError = unknown, TQueryKey extends Array<unknown> = unknown[]>(clientOptions: Options<GetApiAdminWsNpcsData, true> = {}, queryKey?: TQueryKey, options?: Omit<UseSuspenseQueryOptions<TData, TError>, "queryKey" | "queryFn">) => useSuspenseQuery<TData, TError>({ queryKey: Common.UseGetApiAdminWsNpcsKeyFn(clientOptions, queryKey), queryFn: ({ signal }) => getApiAdminWsNpcs({ ...clientOptions, signal, throwOnError: true }).then(response => response.data as TData) as TData, ...options });
export const useGetApiAdminWsScenesByIdSuspense = <TData = NonNullable<Common.GetApiAdminWsScenesByIdDefaultResponse>, TError = unknown, TQueryKey extends Array<unknown> = unknown[]>(clientOptions: Options<GetApiAdminWsScenesByIdData, true> = {}, queryKey?: TQueryKey, options?: Omit<UseSuspenseQueryOptions<TData, TError>, "queryKey" | "queryFn">) => useSuspenseQuery<TData, TError>({ queryKey: Common.UseGetApiAdminWsScenesByIdKeyFn(clientOptions, queryKey), queryFn: ({ signal }) => getApiAdminWsScenesById({ ...clientOptions, signal, throwOnError: true }).then(response => response.data as TData) as TData, ...options });
export const useGetApiAdminWsInstancesByIdSuspense = <TData = NonNullable<Common.GetApiAdminWsInstancesByIdDefaultResponse>, TError = unknown, TQueryKey extends Array<unknown> = unknown[]>(clientOptions: Options<GetApiAdminWsInstancesByIdData, true> = {}, queryKey?: TQueryKey, options?: Omit<UseSuspenseQueryOptions<TData, TError>, "queryKey" | "queryFn">) => useSuspenseQuery<TData, TError>({ queryKey: Common.UseGetApiAdminWsInstancesByIdKeyFn(clientOptions, queryKey), queryFn: ({ signal }) => getApiAdminWsInstancesById({ ...clientOptions, signal, throwOnError: true }).then(response => response.data as TData) as TData, ...options });
export const useGetApiAdminWsSimulationSuspense = <TData = NonNullable<Common.GetApiAdminWsSimulationDefaultResponse>, TError = unknown, TQueryKey extends Array<unknown> = unknown[]>(clientOptions: Options<GetApiAdminWsSimulationData, true> = {}, queryKey?: TQueryKey, options?: Omit<UseSuspenseQueryOptions<TData, TError>, "queryKey" | "queryFn">) => useSuspenseQuery<TData, TError>({ queryKey: Common.UseGetApiAdminWsSimulationKeyFn(clientOptions, queryKey), queryFn: ({ signal }) => getApiAdminWsSimulation({ ...clientOptions, signal, throwOnError: true }).then(response => response.data as TData) as TData, ...options });
/**
 * Key bindings — a player's saved bindings if ?player= is given and persistence is available, otherwise the default config
 */
export const useGetApiKeybindingsSuspense = <TData = NonNullable<Common.GetApiKeybindingsDefaultResponse>, TError = unknown, TQueryKey extends Array<unknown> = unknown[]>(clientOptions: Options<GetApiKeybindingsData, true> = {}, queryKey?: TQueryKey, options?: Omit<UseSuspenseQueryOptions<TData, TError>, "queryKey" | "queryFn">) => useSuspenseQuery<TData, TError>({ queryKey: Common.UseGetApiKeybindingsKeyFn(clientOptions, queryKey), queryFn: ({ signal }) => getApiKeybindings({ ...clientOptions, signal, throwOnError: true }).then(response => response.data as TData) as TData, ...options });
/**
 * Autocomplete suggestions for a slash command argument
 */
export const useGetApiAutocompleteByCommandIdByArgIndexSuspense = <TData = NonNullable<Common.GetApiAutocompleteByCommandIdByArgIndexDefaultResponse>, TError = unknown, TQueryKey extends Array<unknown> = unknown[]>(clientOptions: Options<GetApiAutocompleteByCommandIdByArgIndexData, true>, queryKey?: TQueryKey, options?: Omit<UseSuspenseQueryOptions<TData, TError>, "queryKey" | "queryFn">) => useSuspenseQuery<TData, TError>({ queryKey: Common.UseGetApiAutocompleteByCommandIdByArgIndexKeyFn(clientOptions, queryKey), queryFn: ({ signal }) => getApiAutocompleteByCommandIdByArgIndex({ ...clientOptions, signal, throwOnError: true }).then(response => response.data as TData) as TData, ...options });
/**
 * Client-facing translation keys for a locale
 */
export const useGetApiI18nByLocaleSuspense = <TData = NonNullable<Common.GetApiI18nByLocaleDefaultResponse>, TError = unknown, TQueryKey extends Array<unknown> = unknown[]>(clientOptions: Options<GetApiI18nByLocaleData, true>, queryKey?: TQueryKey, options?: Omit<UseSuspenseQueryOptions<TData, TError>, "queryKey" | "queryFn">) => useSuspenseQuery<TData, TError>({ queryKey: Common.UseGetApiI18nByLocaleKeyFn(clientOptions, queryKey), queryFn: ({ signal }) => getApiI18nByLocale({ ...clientOptions, signal, throwOnError: true }).then(response => response.data as TData) as TData, ...options });
/**
 * All widgets registered for the UI layout editor
 */
export const useGetApiLayoutRegistrySuspense = <TData = NonNullable<Common.GetApiLayoutRegistryDefaultResponse>, TError = unknown, TQueryKey extends Array<unknown> = unknown[]>(clientOptions: Options<GetApiLayoutRegistryData, true> = {}, queryKey?: TQueryKey, options?: Omit<UseSuspenseQueryOptions<TData, TError>, "queryKey" | "queryFn">) => useSuspenseQuery<TData, TError>({ queryKey: Common.UseGetApiLayoutRegistryKeyFn(clientOptions, queryKey), queryFn: ({ signal }) => getApiLayoutRegistry({ ...clientOptions, signal, throwOnError: true }).then(response => response.data as TData) as TData, ...options });
/**
 * Item metadata (label, background color, consumable flags) by item type id
 */
export const useGetApiItemsMetaSuspense = <TData = NonNullable<Common.GetApiItemsMetaDefaultResponse>, TError = unknown, TQueryKey extends Array<unknown> = unknown[]>(clientOptions: Options<GetApiItemsMetaData, true> = {}, queryKey?: TQueryKey, options?: Omit<UseSuspenseQueryOptions<TData, TError>, "queryKey" | "queryFn">) => useSuspenseQuery<TData, TError>({ queryKey: Common.UseGetApiItemsMetaKeyFn(clientOptions, queryKey), queryFn: ({ signal }) => getApiItemsMeta({ ...clientOptions, signal, throwOnError: true }).then(response => response.data as TData) as TData, ...options });
/**
 * Server build timestamp
 */
export const useGetApiServerInfoSuspense = <TData = NonNullable<Common.GetApiServerInfoDefaultResponse>, TError = unknown, TQueryKey extends Array<unknown> = unknown[]>(clientOptions: Options<GetApiServerInfoData, true> = {}, queryKey?: TQueryKey, options?: Omit<UseSuspenseQueryOptions<TData, TError>, "queryKey" | "queryFn">) => useSuspenseQuery<TData, TError>({ queryKey: Common.UseGetApiServerInfoKeyFn(clientOptions, queryKey), queryFn: ({ signal }) => getApiServerInfo({ ...clientOptions, signal, throwOnError: true }).then(response => response.data as TData) as TData, ...options });
/**
 * Attack definitions, flattened by "attackId:level" key
 */
export const useGetApiAttacksSuspense = <TData = NonNullable<Common.GetApiAttacksDefaultResponse>, TError = unknown, TQueryKey extends Array<unknown> = unknown[]>(clientOptions: Options<GetApiAttacksData, true> = {}, queryKey?: TQueryKey, options?: Omit<UseSuspenseQueryOptions<TData, TError>, "queryKey" | "queryFn">) => useSuspenseQuery<TData, TError>({ queryKey: Common.UseGetApiAttacksKeyFn(clientOptions, queryKey), queryFn: ({ signal }) => getApiAttacks({ ...clientOptions, signal, throwOnError: true }).then(response => response.data as TData) as TData, ...options });
/**
 * Attack ids accessible per RPG class, keyed by level
 */
export const useGetApiClassesSuspense = <TData = NonNullable<Common.GetApiClassesDefaultResponse>, TError = unknown, TQueryKey extends Array<unknown> = unknown[]>(clientOptions: Options<GetApiClassesData, true> = {}, queryKey?: TQueryKey, options?: Omit<UseSuspenseQueryOptions<TData, TError>, "queryKey" | "queryFn">) => useSuspenseQuery<TData, TError>({ queryKey: Common.UseGetApiClassesKeyFn(clientOptions, queryKey), queryFn: ({ signal }) => getApiClasses({ ...clientOptions, signal, throwOnError: true }).then(response => response.data as TData) as TData, ...options });
/**
 * Spell definitions, keyed by spell id
 */
export const useGetApiSpellsSuspense = <TData = NonNullable<Common.GetApiSpellsDefaultResponse>, TError = unknown, TQueryKey extends Array<unknown> = unknown[]>(clientOptions: Options<GetApiSpellsData, true> = {}, queryKey?: TQueryKey, options?: Omit<UseSuspenseQueryOptions<TData, TError>, "queryKey" | "queryFn">) => useSuspenseQuery<TData, TError>({ queryKey: Common.UseGetApiSpellsKeyFn(clientOptions, queryKey), queryFn: ({ signal }) => getApiSpells({ ...clientOptions, signal, throwOnError: true }).then(response => response.data as TData) as TData, ...options });
/**
 * Variables available to the macro JEXL evaluation context
 */
export const useGetApiMacrosContextSuspense = <TData = NonNullable<Common.GetApiMacrosContextDefaultResponse>, TError = unknown, TQueryKey extends Array<unknown> = unknown[]>(clientOptions: Options<GetApiMacrosContextData, true> = {}, queryKey?: TQueryKey, options?: Omit<UseSuspenseQueryOptions<TData, TError>, "queryKey" | "queryFn">) => useSuspenseQuery<TData, TError>({ queryKey: Common.UseGetApiMacrosContextKeyFn(clientOptions, queryKey), queryFn: ({ signal }) => getApiMacrosContext({ ...clientOptions, signal, throwOnError: true }).then(response => response.data as TData) as TData, ...options });
/**
 * Grass color per biome id, as [r, g, b] in 0..1
 */
export const useGetApiBiomesSuspense = <TData = NonNullable<Common.GetApiBiomesDefaultResponse>, TError = unknown, TQueryKey extends Array<unknown> = unknown[]>(clientOptions: Options<GetApiBiomesData, true> = {}, queryKey?: TQueryKey, options?: Omit<UseSuspenseQueryOptions<TData, TError>, "queryKey" | "queryFn">) => useSuspenseQuery<TData, TError>({ queryKey: Common.UseGetApiBiomesKeyFn(clientOptions, queryKey), queryFn: ({ signal }) => getApiBiomes({ ...clientOptions, signal, throwOnError: true }).then(response => response.data as TData) as TData, ...options });
/**
 * A player's current skin
 */
export const useGetApiPlayerByIdSkinSuspense = <TData = NonNullable<Common.GetApiPlayerByIdSkinDefaultResponse>, TError = unknown, TQueryKey extends Array<unknown> = unknown[]>(clientOptions: Options<GetApiPlayerByIdSkinData, true>, queryKey?: TQueryKey, options?: Omit<UseSuspenseQueryOptions<TData, TError>, "queryKey" | "queryFn">) => useSuspenseQuery<TData, TError>({ queryKey: Common.UseGetApiPlayerByIdSkinKeyFn(clientOptions, queryKey), queryFn: ({ signal }) => getApiPlayerByIdSkin({ ...clientOptions, signal, throwOnError: true }).then(response => response.data as TData) as TData, ...options });
/**
 * Armor names currently equipped by a player
 */
export const useGetApiPlayerByIdArmorsSuspense = <TData = NonNullable<Common.GetApiPlayerByIdArmorsDefaultResponse>, TError = unknown, TQueryKey extends Array<unknown> = unknown[]>(clientOptions: Options<GetApiPlayerByIdArmorsData, true>, queryKey?: TQueryKey, options?: Omit<UseSuspenseQueryOptions<TData, TError>, "queryKey" | "queryFn">) => useSuspenseQuery<TData, TError>({ queryKey: Common.UseGetApiPlayerByIdArmorsKeyFn(clientOptions, queryKey), queryFn: ({ signal }) => getApiPlayerByIdArmors({ ...clientOptions, signal, throwOnError: true }).then(response => response.data as TData) as TData, ...options });
/**
 * Wielded weapon/tool names and dominant hand for a player
 */
export const useGetApiPlayerByIdHandsSuspense = <TData = NonNullable<Common.GetApiPlayerByIdHandsDefaultResponse>, TError = unknown, TQueryKey extends Array<unknown> = unknown[]>(clientOptions: Options<GetApiPlayerByIdHandsData, true>, queryKey?: TQueryKey, options?: Omit<UseSuspenseQueryOptions<TData, TError>, "queryKey" | "queryFn">) => useSuspenseQuery<TData, TError>({ queryKey: Common.UseGetApiPlayerByIdHandsKeyFn(clientOptions, queryKey), queryFn: ({ signal }) => getApiPlayerByIdHands({ ...clientOptions, signal, throwOnError: true }).then(response => response.data as TData) as TData, ...options });
/**
 * Armor/weapon/tool names owned by a player
 */
export const useGetApiPlayerByIdOwnedSuspense = <TData = NonNullable<Common.GetApiPlayerByIdOwnedDefaultResponse>, TError = unknown, TQueryKey extends Array<unknown> = unknown[]>(clientOptions: Options<GetApiPlayerByIdOwnedData, true>, queryKey?: TQueryKey, options?: Omit<UseSuspenseQueryOptions<TData, TError>, "queryKey" | "queryFn">) => useSuspenseQuery<TData, TError>({ queryKey: Common.UseGetApiPlayerByIdOwnedKeyFn(clientOptions, queryKey), queryFn: ({ signal }) => getApiPlayerByIdOwned({ ...clientOptions, signal, throwOnError: true }).then(response => response.data as TData) as TData, ...options });
/**
 * A player's RPG character class
 */
export const useGetApiPlayerByIdRpgSuspense = <TData = NonNullable<Common.GetApiPlayerByIdRpgDefaultResponse>, TError = unknown, TQueryKey extends Array<unknown> = unknown[]>(clientOptions: Options<GetApiPlayerByIdRpgData, true>, queryKey?: TQueryKey, options?: Omit<UseSuspenseQueryOptions<TData, TError>, "queryKey" | "queryFn">) => useSuspenseQuery<TData, TError>({ queryKey: Common.UseGetApiPlayerByIdRpgKeyFn(clientOptions, queryKey), queryFn: ({ signal }) => getApiPlayerByIdRpg({ ...clientOptions, signal, throwOnError: true }).then(response => response.data as TData) as TData, ...options });
/**
 * Names of all available player skins
 */
export const useGetApiSkinsSuspense = <TData = NonNullable<Common.GetApiSkinsDefaultResponse>, TError = unknown, TQueryKey extends Array<unknown> = unknown[]>(clientOptions: Options<GetApiSkinsData, true> = {}, queryKey?: TQueryKey, options?: Omit<UseSuspenseQueryOptions<TData, TError>, "queryKey" | "queryFn">) => useSuspenseQuery<TData, TError>({ queryKey: Common.UseGetApiSkinsKeyFn(clientOptions, queryKey), queryFn: ({ signal }) => getApiSkins({ ...clientOptions, signal, throwOnError: true }).then(response => response.data as TData) as TData, ...options });
/**
 * Skin config (eye offset, hidden bones) for a named skin
 */
export const useGetApiSkinsByNameConfigSuspense = <TData = NonNullable<Common.GetApiSkinsByNameConfigDefaultResponse>, TError = unknown, TQueryKey extends Array<unknown> = unknown[]>(clientOptions: Options<GetApiSkinsByNameConfigData, true>, queryKey?: TQueryKey, options?: Omit<UseSuspenseQueryOptions<TData, TError>, "queryKey" | "queryFn">) => useSuspenseQuery<TData, TError>({ queryKey: Common.UseGetApiSkinsByNameConfigKeyFn(clientOptions, queryKey), queryFn: ({ signal }) => getApiSkinsByNameConfig({ ...clientOptions, signal, throwOnError: true }).then(response => response.data as TData) as TData, ...options });
/**
 * List all armor definitions
 */
export const useGetApiArmorsSuspense = <TData = NonNullable<Common.GetApiArmorsDefaultResponse>, TError = unknown, TQueryKey extends Array<unknown> = unknown[]>(clientOptions: Options<GetApiArmorsData, true> = {}, queryKey?: TQueryKey, options?: Omit<UseSuspenseQueryOptions<TData, TError>, "queryKey" | "queryFn">) => useSuspenseQuery<TData, TError>({ queryKey: Common.UseGetApiArmorsKeyFn(clientOptions, queryKey), queryFn: ({ signal }) => getApiArmors({ ...clientOptions, signal, throwOnError: true }).then(response => response.data as TData) as TData, ...options });
/**
 * List all weapon definitions
 */
export const useGetApiWeaponsSuspense = <TData = NonNullable<Common.GetApiWeaponsDefaultResponse>, TError = unknown, TQueryKey extends Array<unknown> = unknown[]>(clientOptions: Options<GetApiWeaponsData, true> = {}, queryKey?: TQueryKey, options?: Omit<UseSuspenseQueryOptions<TData, TError>, "queryKey" | "queryFn">) => useSuspenseQuery<TData, TError>({ queryKey: Common.UseGetApiWeaponsKeyFn(clientOptions, queryKey), queryFn: ({ signal }) => getApiWeapons({ ...clientOptions, signal, throwOnError: true }).then(response => response.data as TData) as TData, ...options });
/**
 * List all tool definitions
 */
export const useGetApiToolsSuspense = <TData = NonNullable<Common.GetApiToolsDefaultResponse>, TError = unknown, TQueryKey extends Array<unknown> = unknown[]>(clientOptions: Options<GetApiToolsData, true> = {}, queryKey?: TQueryKey, options?: Omit<UseSuspenseQueryOptions<TData, TError>, "queryKey" | "queryFn">) => useSuspenseQuery<TData, TError>({ queryKey: Common.UseGetApiToolsKeyFn(clientOptions, queryKey), queryFn: ({ signal }) => getApiTools({ ...clientOptions, signal, throwOnError: true }).then(response => response.data as TData) as TData, ...options });
/**
 * 3D game asset files discovered under resources/game-assets
 */
export const useGetApiGameAssetsSuspense = <TData = NonNullable<Common.GetApiGameAssetsDefaultResponse>, TError = unknown, TQueryKey extends Array<unknown> = unknown[]>(clientOptions: Options<GetApiGameAssetsData, true> = {}, queryKey?: TQueryKey, options?: Omit<UseSuspenseQueryOptions<TData, TError>, "queryKey" | "queryFn">) => useSuspenseQuery<TData, TError>({ queryKey: Common.UseGetApiGameAssetsKeyFn(clientOptions, queryKey), queryFn: ({ signal }) => getApiGameAssets({ ...clientOptions, signal, throwOnError: true }).then(response => response.data as TData) as TData, ...options });
/**
 * Raw asset file bytes (glb/gltf/fbx/textures)
 */
export const useGetApiGameAssetsFileBySuspense = <TData = NonNullable<Common.GetApiGameAssetsFileByDefaultResponse>, TError = unknown, TQueryKey extends Array<unknown> = unknown[]>(clientOptions: Options<GetApiGameAssetsFileByData, true>, queryKey?: TQueryKey, options?: Omit<UseSuspenseQueryOptions<TData, TError>, "queryKey" | "queryFn">) => useSuspenseQuery<TData, TError>({ queryKey: Common.UseGetApiGameAssetsFileByKeyFn(clientOptions, queryKey), queryFn: ({ signal }) => getApiGameAssetsFileBy({ ...clientOptions, signal, throwOnError: true }).then(response => response.data as TData) as TData, ...options });
/**
 * Reads a .blend file's collection/object tree via headless Blender (cached)
 */
export const useGetApiGameAssetsBlendSceneBySuspense = <TData = NonNullable<Common.GetApiGameAssetsBlendSceneByDefaultResponse>, TError = unknown, TQueryKey extends Array<unknown> = unknown[]>(clientOptions: Options<GetApiGameAssetsBlendSceneByData, true>, queryKey?: TQueryKey, options?: Omit<UseSuspenseQueryOptions<TData, TError>, "queryKey" | "queryFn">) => useSuspenseQuery<TData, TError>({ queryKey: Common.UseGetApiGameAssetsBlendSceneByKeyFn(clientOptions, queryKey), queryFn: ({ signal }) => getApiGameAssetsBlendSceneBy({ ...clientOptions, signal, throwOnError: true }).then(response => response.data as TData) as TData, ...options });
/**
 * Converts a .blend file to OBJ/MTL via headless Blender (cached) and returns the OBJ asset path
 */
export const useGetApiGameAssetsBlendPreviewBySuspense = <TData = NonNullable<Common.GetApiGameAssetsBlendPreviewByDefaultResponse>, TError = unknown, TQueryKey extends Array<unknown> = unknown[]>(clientOptions: Options<GetApiGameAssetsBlendPreviewByData, true>, queryKey?: TQueryKey, options?: Omit<UseSuspenseQueryOptions<TData, TError>, "queryKey" | "queryFn">) => useSuspenseQuery<TData, TError>({ queryKey: Common.UseGetApiGameAssetsBlendPreviewByKeyFn(clientOptions, queryKey), queryFn: ({ signal }) => getApiGameAssetsBlendPreviewBy({ ...clientOptions, signal, throwOnError: true }).then(response => response.data as TData) as TData, ...options });
/**
 * Converts an OBJ/MTL mesh into a Blockbench-compatible mesh .bbmodel (cached). The generated mesh elements are not rendered by the admin viewer — open the result in Blockbench to edit it.
 */
export const useGetApiGameAssetsBbmodelExportBySuspense = <TData = NonNullable<Common.GetApiGameAssetsBbmodelExportByDefaultResponse>, TError = unknown, TQueryKey extends Array<unknown> = unknown[]>(clientOptions: Options<GetApiGameAssetsBbmodelExportByData, true>, queryKey?: TQueryKey, options?: Omit<UseSuspenseQueryOptions<TData, TError>, "queryKey" | "queryFn">) => useSuspenseQuery<TData, TError>({ queryKey: Common.UseGetApiGameAssetsBbmodelExportByKeyFn(clientOptions, queryKey), queryFn: ({ signal }) => getApiGameAssetsBbmodelExportBy({ ...clientOptions, signal, throwOnError: true }).then(response => response.data as TData) as TData, ...options });
/**
 * All quest definitions
 */
export const useGetApiQuestsSuspense = <TData = NonNullable<Common.GetApiQuestsDefaultResponse>, TError = unknown, TQueryKey extends Array<unknown> = unknown[]>(clientOptions: Options<GetApiQuestsData, true> = {}, queryKey?: TQueryKey, options?: Omit<UseSuspenseQueryOptions<TData, TError>, "queryKey" | "queryFn">) => useSuspenseQuery<TData, TError>({ queryKey: Common.UseGetApiQuestsKeyFn(clientOptions, queryKey), queryFn: ({ signal }) => getApiQuests({ ...clientOptions, signal, throwOnError: true }).then(response => response.data as TData) as TData, ...options });
/**
 * Names of all known players
 */
export const useGetApiPlayersNamesSuspense = <TData = NonNullable<Common.GetApiPlayersNamesDefaultResponse>, TError = unknown, TQueryKey extends Array<unknown> = unknown[]>(clientOptions: Options<GetApiPlayersNamesData, true> = {}, queryKey?: TQueryKey, options?: Omit<UseSuspenseQueryOptions<TData, TError>, "queryKey" | "queryFn">) => useSuspenseQuery<TData, TError>({ queryKey: Common.UseGetApiPlayersNamesKeyFn(clientOptions, queryKey), queryFn: ({ signal }) => getApiPlayersNames({ ...clientOptions, signal, throwOnError: true }).then(response => response.data as TData) as TData, ...options });
/**
 * Live players, NPCs and weather zones for the map overlay
 */
export const useGetApiMapStateSuspense = <TData = NonNullable<Common.GetApiMapStateDefaultResponse>, TError = unknown, TQueryKey extends Array<unknown> = unknown[]>(clientOptions: Options<GetApiMapStateData, true> = {}, queryKey?: TQueryKey, options?: Omit<UseSuspenseQueryOptions<TData, TError>, "queryKey" | "queryFn">) => useSuspenseQuery<TData, TError>({ queryKey: Common.UseGetApiMapStateKeyFn(clientOptions, queryKey), queryFn: ({ signal }) => getApiMapState({ ...clientOptions, signal, throwOnError: true }).then(response => response.data as TData) as TData, ...options });
/**
 * Cached terrain summary JSON for the map overlay
 */
export const useGetApiMapTerrainSuspense = <TData = NonNullable<Common.GetApiMapTerrainDefaultResponse>, TError = unknown, TQueryKey extends Array<unknown> = unknown[]>(clientOptions: Options<GetApiMapTerrainData, true> = {}, queryKey?: TQueryKey, options?: Omit<UseSuspenseQueryOptions<TData, TError>, "queryKey" | "queryFn">) => useSuspenseQuery<TData, TError>({ queryKey: Common.UseGetApiMapTerrainKeyFn(clientOptions, queryKey), queryFn: ({ signal }) => getApiMapTerrain({ ...clientOptions, signal, throwOnError: true }).then(response => response.data as TData) as TData, ...options });
/**
 * Voronoi biome cells around a point
 */
export const useGetApiMapVoronoiSuspense = <TData = NonNullable<Common.GetApiMapVoronoiDefaultResponse>, TError = unknown, TQueryKey extends Array<unknown> = unknown[]>(clientOptions: Options<GetApiMapVoronoiData, true> = {}, queryKey?: TQueryKey, options?: Omit<UseSuspenseQueryOptions<TData, TError>, "queryKey" | "queryFn">) => useSuspenseQuery<TData, TError>({ queryKey: Common.UseGetApiMapVoronoiKeyFn(clientOptions, queryKey), queryFn: ({ signal }) => getApiMapVoronoi({ ...clientOptions, signal, throwOnError: true }).then(response => response.data as TData) as TData, ...options });
/**
 * Generated houses within an area
 */
export const useGetApiMapHousesSuspense = <TData = NonNullable<Common.GetApiMapHousesDefaultResponse>, TError = unknown, TQueryKey extends Array<unknown> = unknown[]>(clientOptions: Options<GetApiMapHousesData, true> = {}, queryKey?: TQueryKey, options?: Omit<UseSuspenseQueryOptions<TData, TError>, "queryKey" | "queryFn">) => useSuspenseQuery<TData, TError>({ queryKey: Common.UseGetApiMapHousesKeyFn(clientOptions, queryKey), queryFn: ({ signal }) => getApiMapHouses({ ...clientOptions, signal, throwOnError: true }).then(response => response.data as TData) as TData, ...options });
/**
 * Named staircase points for the map overlay
 */
export const useGetApiMapStaircasesSuspense = <TData = NonNullable<Common.GetApiMapStaircasesDefaultResponse>, TError = unknown, TQueryKey extends Array<unknown> = unknown[]>(clientOptions: Options<GetApiMapStaircasesData, true> = {}, queryKey?: TQueryKey, options?: Omit<UseSuspenseQueryOptions<TData, TError>, "queryKey" | "queryFn">) => useSuspenseQuery<TData, TError>({ queryKey: Common.UseGetApiMapStaircasesKeyFn(clientOptions, queryKey), queryFn: ({ signal }) => getApiMapStaircases({ ...clientOptions, signal, throwOnError: true }).then(response => response.data as TData) as TData, ...options });
/**
 * Road vertex segments within an area
 */
export const useGetApiMapRoadsSuspense = <TData = NonNullable<Common.GetApiMapRoadsDefaultResponse>, TError = unknown, TQueryKey extends Array<unknown> = unknown[]>(clientOptions: Options<GetApiMapRoadsData, true> = {}, queryKey?: TQueryKey, options?: Omit<UseSuspenseQueryOptions<TData, TError>, "queryKey" | "queryFn">) => useSuspenseQuery<TData, TError>({ queryKey: Common.UseGetApiMapRoadsKeyFn(clientOptions, queryKey), queryFn: ({ signal }) => getApiMapRoads({ ...clientOptions, signal, throwOnError: true }).then(response => response.data as TData) as TData, ...options });
/**
 * Per-chunk road bitmask within an area
 */
export const useGetApiMapRoadRasterSuspense = <TData = NonNullable<Common.GetApiMapRoadRasterDefaultResponse>, TError = unknown, TQueryKey extends Array<unknown> = unknown[]>(clientOptions: Options<GetApiMapRoadRasterData, true> = {}, queryKey?: TQueryKey, options?: Omit<UseSuspenseQueryOptions<TData, TError>, "queryKey" | "queryFn">) => useSuspenseQuery<TData, TError>({ queryKey: Common.UseGetApiMapRoadRasterKeyFn(clientOptions, queryKey), queryFn: ({ signal }) => getApiMapRoadRaster({ ...clientOptions, signal, throwOnError: true }).then(response => response.data as TData) as TData, ...options });
/**
 * Rasterized road overlay as a PNG image
 */
export const useGetApiMapRoadRasterPngSuspense = <TData = NonNullable<Common.GetApiMapRoadRasterPngDefaultResponse>, TError = unknown, TQueryKey extends Array<unknown> = unknown[]>(clientOptions: Options<GetApiMapRoadRasterPngData, true> = {}, queryKey?: TQueryKey, options?: Omit<UseSuspenseQueryOptions<TData, TError>, "queryKey" | "queryFn">) => useSuspenseQuery<TData, TError>({ queryKey: Common.UseGetApiMapRoadRasterPngKeyFn(clientOptions, queryKey), queryFn: ({ signal }) => getApiMapRoadRasterPng({ ...clientOptions, signal, throwOnError: true }).then(response => response.data as TData) as TData, ...options });
/**
 * Rasterized terrain overlay as a PNG image
 */
export const useGetApiMapTerrainRasterPngSuspense = <TData = NonNullable<Common.GetApiMapTerrainRasterPngDefaultResponse>, TError = unknown, TQueryKey extends Array<unknown> = unknown[]>(clientOptions: Options<GetApiMapTerrainRasterPngData, true> = {}, queryKey?: TQueryKey, options?: Omit<UseSuspenseQueryOptions<TData, TError>, "queryKey" | "queryFn">) => useSuspenseQuery<TData, TError>({ queryKey: Common.UseGetApiMapTerrainRasterPngKeyFn(clientOptions, queryKey), queryFn: ({ signal }) => getApiMapTerrainRasterPng({ ...clientOptions, signal, throwOnError: true }).then(response => response.data as TData) as TData, ...options });
/**
 * Voronoi cell border segments within an area
 */
export const useGetApiMapVoronoiBordersSuspense = <TData = NonNullable<Common.GetApiMapVoronoiBordersDefaultResponse>, TError = unknown, TQueryKey extends Array<unknown> = unknown[]>(clientOptions: Options<GetApiMapVoronoiBordersData, true> = {}, queryKey?: TQueryKey, options?: Omit<UseSuspenseQueryOptions<TData, TError>, "queryKey" | "queryFn">) => useSuspenseQuery<TData, TError>({ queryKey: Common.UseGetApiMapVoronoiBordersKeyFn(clientOptions, queryKey), queryFn: ({ signal }) => getApiMapVoronoiBorders({ ...clientOptions, signal, throwOnError: true }).then(response => response.data as TData) as TData, ...options });
/**
 * Server status snapshot (TPS, players, chunks, heap, CPU)
 */
export const useGetApiAdminStatusSuspense = <TData = NonNullable<Common.GetApiAdminStatusDefaultResponse>, TError = unknown, TQueryKey extends Array<unknown> = unknown[]>(clientOptions: Options<GetApiAdminStatusData, true> = {}, queryKey?: TQueryKey, options?: Omit<UseSuspenseQueryOptions<TData, TError>, "queryKey" | "queryFn">) => useSuspenseQuery<TData, TError>({ queryKey: Common.UseGetApiAdminStatusKeyFn(clientOptions, queryKey), queryFn: ({ signal }) => getApiAdminStatus({ ...clientOptions, signal, throwOnError: true }).then(response => response.data as TData) as TData, ...options });
/**
 * All local/no-auth accounts
 */
export const useGetApiAdminUsersSuspense = <TData = NonNullable<Common.GetApiAdminUsersDefaultResponse>, TError = unknown, TQueryKey extends Array<unknown> = unknown[]>(clientOptions: Options<GetApiAdminUsersData, true> = {}, queryKey?: TQueryKey, options?: Omit<UseSuspenseQueryOptions<TData, TError>, "queryKey" | "queryFn">) => useSuspenseQuery<TData, TError>({ queryKey: Common.UseGetApiAdminUsersKeyFn(clientOptions, queryKey), queryFn: ({ signal }) => getApiAdminUsers({ ...clientOptions, signal, throwOnError: true }).then(response => response.data as TData) as TData, ...options });
/**
 * All player names
 */
export const useGetApiAdminPlayersSuspense = <TData = NonNullable<Common.GetApiAdminPlayersDefaultResponse>, TError = unknown, TQueryKey extends Array<unknown> = unknown[]>(clientOptions: Options<GetApiAdminPlayersData, true> = {}, queryKey?: TQueryKey, options?: Omit<UseSuspenseQueryOptions<TData, TError>, "queryKey" | "queryFn">) => useSuspenseQuery<TData, TError>({ queryKey: Common.UseGetApiAdminPlayersKeyFn(clientOptions, queryKey), queryFn: ({ signal }) => getApiAdminPlayers({ ...clientOptions, signal, throwOnError: true }).then(response => response.data as TData) as TData, ...options });
/**
 * Full player file (state, keybindings, RPG data)
 */
export const useGetApiAdminPlayersByNameSuspense = <TData = NonNullable<Common.GetApiAdminPlayersByNameDefaultResponse>, TError = unknown, TQueryKey extends Array<unknown> = unknown[]>(clientOptions: Options<GetApiAdminPlayersByNameData, true>, queryKey?: TQueryKey, options?: Omit<UseSuspenseQueryOptions<TData, TError>, "queryKey" | "queryFn">) => useSuspenseQuery<TData, TError>({ queryKey: Common.UseGetApiAdminPlayersByNameKeyFn(clientOptions, queryKey), queryFn: ({ signal }) => getApiAdminPlayersByName({ ...clientOptions, signal, throwOnError: true }).then(response => response.data as TData) as TData, ...options });
/**
 * All worlds on disk, with stats
 */
export const useGetApiAdminWorldsSuspense = <TData = NonNullable<Common.GetApiAdminWorldsDefaultResponse>, TError = unknown, TQueryKey extends Array<unknown> = unknown[]>(clientOptions: Options<GetApiAdminWorldsData, true> = {}, queryKey?: TQueryKey, options?: Omit<UseSuspenseQueryOptions<TData, TError>, "queryKey" | "queryFn">) => useSuspenseQuery<TData, TError>({ queryKey: Common.UseGetApiAdminWorldsKeyFn(clientOptions, queryKey), queryFn: ({ signal }) => getApiAdminWorlds({ ...clientOptions, signal, throwOnError: true }).then(response => response.data as TData) as TData, ...options });
/**
 * Names of all whitelisted editable YAML config files
 */
export const useGetApiAdminConfigsSuspense = <TData = NonNullable<Common.GetApiAdminConfigsDefaultResponse>, TError = unknown, TQueryKey extends Array<unknown> = unknown[]>(clientOptions: Options<GetApiAdminConfigsData, true> = {}, queryKey?: TQueryKey, options?: Omit<UseSuspenseQueryOptions<TData, TError>, "queryKey" | "queryFn">) => useSuspenseQuery<TData, TError>({ queryKey: Common.UseGetApiAdminConfigsKeyFn(clientOptions, queryKey), queryFn: ({ signal }) => getApiAdminConfigs({ ...clientOptions, signal, throwOnError: true }).then(response => response.data as TData) as TData, ...options });
/**
 * Raw YAML content of a whitelisted config file
 */
export const useGetApiAdminConfigsBySuspense = <TData = NonNullable<Common.GetApiAdminConfigsByDefaultResponse>, TError = unknown, TQueryKey extends Array<unknown> = unknown[]>(clientOptions: Options<GetApiAdminConfigsByData, true>, queryKey?: TQueryKey, options?: Omit<UseSuspenseQueryOptions<TData, TError>, "queryKey" | "queryFn">) => useSuspenseQuery<TData, TError>({ queryKey: Common.UseGetApiAdminConfigsByKeyFn(clientOptions, queryKey), queryFn: ({ signal }) => getApiAdminConfigsBy({ ...clientOptions, signal, throwOnError: true }).then(response => response.data as TData) as TData, ...options });
/**
 * RPG class definitions, keyed by class name
 */
export const useGetApiAdminClassesSuspense = <TData = NonNullable<Common.GetApiAdminClassesDefaultResponse>, TError = unknown, TQueryKey extends Array<unknown> = unknown[]>(clientOptions: Options<GetApiAdminClassesData, true> = {}, queryKey?: TQueryKey, options?: Omit<UseSuspenseQueryOptions<TData, TError>, "queryKey" | "queryFn">) => useSuspenseQuery<TData, TError>({ queryKey: Common.UseGetApiAdminClassesKeyFn(clientOptions, queryKey), queryFn: ({ signal }) => getApiAdminClasses({ ...clientOptions, signal, throwOnError: true }).then(response => response.data as TData) as TData, ...options });
/**
 * All attack and spell ids
 */
export const useGetApiAdminSkillsSuspense = <TData = NonNullable<Common.GetApiAdminSkillsDefaultResponse>, TError = unknown, TQueryKey extends Array<unknown> = unknown[]>(clientOptions: Options<GetApiAdminSkillsData, true> = {}, queryKey?: TQueryKey, options?: Omit<UseSuspenseQueryOptions<TData, TError>, "queryKey" | "queryFn">) => useSuspenseQuery<TData, TError>({ queryKey: Common.UseGetApiAdminSkillsKeyFn(clientOptions, queryKey), queryFn: ({ signal }) => getApiAdminSkills({ ...clientOptions, signal, throwOnError: true }).then(response => response.data as TData) as TData, ...options });
/**
 * Live NPC instances with full animal/combat state
 */
export const useGetApiAdminNpcsSuspense = <TData = NonNullable<Common.GetApiAdminNpcsDefaultResponse>, TError = unknown, TQueryKey extends Array<unknown> = unknown[]>(clientOptions: Options<GetApiAdminNpcsData, true> = {}, queryKey?: TQueryKey, options?: Omit<UseSuspenseQueryOptions<TData, TError>, "queryKey" | "queryFn">) => useSuspenseQuery<TData, TError>({ queryKey: Common.UseGetApiAdminNpcsKeyFn(clientOptions, queryKey), queryFn: ({ signal }) => getApiAdminNpcs({ ...clientOptions, signal, throwOnError: true }).then(response => response.data as TData) as TData, ...options });
/**
 * All registered block definitions
 */
export const useGetApiAdminBlocksSuspense = <TData = NonNullable<Common.GetApiAdminBlocksDefaultResponse>, TError = unknown, TQueryKey extends Array<unknown> = unknown[]>(clientOptions: Options<GetApiAdminBlocksData, true> = {}, queryKey?: TQueryKey, options?: Omit<UseSuspenseQueryOptions<TData, TError>, "queryKey" | "queryFn">) => useSuspenseQuery<TData, TError>({ queryKey: Common.UseGetApiAdminBlocksKeyFn(clientOptions, queryKey), queryFn: ({ signal }) => getApiAdminBlocks({ ...clientOptions, signal, throwOnError: true }).then(response => response.data as TData) as TData, ...options });
/**
 * All registered plain paint colors
 */
export const useGetApiAdminPlainColorsSuspense = <TData = NonNullable<Common.GetApiAdminPlainColorsDefaultResponse>, TError = unknown, TQueryKey extends Array<unknown> = unknown[]>(clientOptions: Options<GetApiAdminPlainColorsData, true> = {}, queryKey?: TQueryKey, options?: Omit<UseSuspenseQueryOptions<TData, TError>, "queryKey" | "queryFn">) => useSuspenseQuery<TData, TError>({ queryKey: Common.UseGetApiAdminPlainColorsKeyFn(clientOptions, queryKey), queryFn: ({ signal }) => getApiAdminPlainColors({ ...clientOptions, signal, throwOnError: true }).then(response => response.data as TData) as TData, ...options });
/**
 * All chunk coordinates generated so far (in-memory ∪ persisted)
 */
export const useGetApiAdminChunksDiscoveredSuspense = <TData = NonNullable<Common.GetApiAdminChunksDiscoveredDefaultResponse>, TError = unknown, TQueryKey extends Array<unknown> = unknown[]>(clientOptions: Options<GetApiAdminChunksDiscoveredData, true> = {}, queryKey?: TQueryKey, options?: Omit<UseSuspenseQueryOptions<TData, TError>, "queryKey" | "queryFn">) => useSuspenseQuery<TData, TError>({ queryKey: Common.UseGetApiAdminChunksDiscoveredKeyFn(clientOptions, queryKey), queryFn: ({ signal }) => getApiAdminChunksDiscovered({ ...clientOptions, signal, throwOnError: true }).then(response => response.data as TData) as TData, ...options });
/**
 * All instance zones
 */
export const useGetApiAdminInstancesSuspense = <TData = NonNullable<Common.GetApiAdminInstancesDefaultResponse>, TError = unknown, TQueryKey extends Array<unknown> = unknown[]>(clientOptions: Options<GetApiAdminInstancesData, true> = {}, queryKey?: TQueryKey, options?: Omit<UseSuspenseQueryOptions<TData, TError>, "queryKey" | "queryFn">) => useSuspenseQuery<TData, TError>({ queryKey: Common.UseGetApiAdminInstancesKeyFn(clientOptions, queryKey), queryFn: ({ signal }) => getApiAdminInstances({ ...clientOptions, signal, throwOnError: true }).then(response => response.data as TData) as TData, ...options });
/**
 * A single instance zone
 */
export const useGetApiAdminInstancesByIdSuspense = <TData = NonNullable<Common.GetApiAdminInstancesByIdDefaultResponse>, TError = unknown, TQueryKey extends Array<unknown> = unknown[]>(clientOptions: Options<GetApiAdminInstancesByIdData, true>, queryKey?: TQueryKey, options?: Omit<UseSuspenseQueryOptions<TData, TError>, "queryKey" | "queryFn">) => useSuspenseQuery<TData, TError>({ queryKey: Common.UseGetApiAdminInstancesByIdKeyFn(clientOptions, queryKey), queryFn: ({ signal }) => getApiAdminInstancesById({ ...clientOptions, signal, throwOnError: true }).then(response => response.data as TData) as TData, ...options });
/**
 * Non-air blocks in an instance zone, streamed as newline-delimited JSON (application/x-ndjson, one InstanceBlockDto per line), capped at 300000
 */
export const useGetApiAdminInstancesByIdBlocksSuspense = <TData = NonNullable<Common.GetApiAdminInstancesByIdBlocksDefaultResponse>, TError = unknown, TQueryKey extends Array<unknown> = unknown[]>(clientOptions: Options<GetApiAdminInstancesByIdBlocksData, true>, queryKey?: TQueryKey, options?: Omit<UseSuspenseQueryOptions<TData, TError>, "queryKey" | "queryFn">) => useSuspenseQuery<TData, TError>({ queryKey: Common.UseGetApiAdminInstancesByIdBlocksKeyFn(clientOptions, queryKey), queryFn: ({ signal }) => getApiAdminInstancesByIdBlocks({ ...clientOptions, signal, throwOnError: true }).then(response => response.data as TData) as TData, ...options });
/**
 * All scenes (bounded off-world block-structure buffers)
 */
export const useGetApiAdminScenesSuspense = <TData = NonNullable<Common.GetApiAdminScenesDefaultResponse>, TError = unknown, TQueryKey extends Array<unknown> = unknown[]>(clientOptions: Options<GetApiAdminScenesData, true> = {}, queryKey?: TQueryKey, options?: Omit<UseSuspenseQueryOptions<TData, TError>, "queryKey" | "queryFn">) => useSuspenseQuery<TData, TError>({ queryKey: Common.UseGetApiAdminScenesKeyFn(clientOptions, queryKey), queryFn: ({ signal }) => getApiAdminScenes({ ...clientOptions, signal, throwOnError: true }).then(response => response.data as TData) as TData, ...options });
/**
 * A single scene's metadata
 */
export const useGetApiAdminScenesByIdSuspense = <TData = NonNullable<Common.GetApiAdminScenesByIdDefaultResponse>, TError = unknown, TQueryKey extends Array<unknown> = unknown[]>(clientOptions: Options<GetApiAdminScenesByIdData, true>, queryKey?: TQueryKey, options?: Omit<UseSuspenseQueryOptions<TData, TError>, "queryKey" | "queryFn">) => useSuspenseQuery<TData, TError>({ queryKey: Common.UseGetApiAdminScenesByIdKeyFn(clientOptions, queryKey), queryFn: ({ signal }) => getApiAdminScenesById({ ...clientOptions, signal, throwOnError: true }).then(response => response.data as TData) as TData, ...options });
/**
 * Scene block/state/extraState buffers as a binary blob: 3×4-byte big-endian dimensions (width,height,depth) followed by the blocks byte array, then the states byte array, then the extraStates byte array (wire-index-per-byte, 0 = AIR)
 */
export const useGetApiAdminScenesByIdBlocksRawSuspense = <TData = NonNullable<Common.GetApiAdminScenesByIdBlocksRawDefaultResponse>, TError = unknown, TQueryKey extends Array<unknown> = unknown[]>(clientOptions: Options<GetApiAdminScenesByIdBlocksRawData, true>, queryKey?: TQueryKey, options?: Omit<UseSuspenseQueryOptions<TData, TError>, "queryKey" | "queryFn">) => useSuspenseQuery<TData, TError>({ queryKey: Common.UseGetApiAdminScenesByIdBlocksRawKeyFn(clientOptions, queryKey), queryFn: ({ signal }) => getApiAdminScenesByIdBlocksRaw({ ...clientOptions, signal, throwOnError: true }).then(response => response.data as TData) as TData, ...options });
/**
 * Fractional (lego/plate/arch) block entities placed in this scene — not carried by the blocks/raw binary blob, so the client loads them separately on scene open
 */
export const useGetApiAdminScenesByIdEntitiesSuspense = <TData = NonNullable<Common.GetApiAdminScenesByIdEntitiesDefaultResponse>, TError = unknown, TQueryKey extends Array<unknown> = unknown[]>(clientOptions: Options<GetApiAdminScenesByIdEntitiesData, true>, queryKey?: TQueryKey, options?: Omit<UseSuspenseQueryOptions<TData, TError>, "queryKey" | "queryFn">) => useSuspenseQuery<TData, TError>({ queryKey: Common.UseGetApiAdminScenesByIdEntitiesKeyFn(clientOptions, queryKey), queryFn: ({ signal }) => getApiAdminScenesByIdEntities({ ...clientOptions, signal, throwOnError: true }).then(response => response.data as TData) as TData, ...options });
/**
 * NPC type definitions (codex info), keyed by type id
 */
export const useGetApiAdminNpcTypesSuspense = <TData = NonNullable<Common.GetApiAdminNpcTypesDefaultResponse>, TError = unknown, TQueryKey extends Array<unknown> = unknown[]>(clientOptions: Options<GetApiAdminNpcTypesData, true> = {}, queryKey?: TQueryKey, options?: Omit<UseSuspenseQueryOptions<TData, TError>, "queryKey" | "queryFn">) => useSuspenseQuery<TData, TError>({ queryKey: Common.UseGetApiAdminNpcTypesKeyFn(clientOptions, queryKey), queryFn: ({ signal }) => getApiAdminNpcTypes({ ...clientOptions, signal, throwOnError: true }).then(response => response.data as TData) as TData, ...options });
/**
 * Item definitions, keyed by item type id
 */
export const useGetApiAdminItemsSuspense = <TData = NonNullable<Common.GetApiAdminItemsDefaultResponse>, TError = unknown, TQueryKey extends Array<unknown> = unknown[]>(clientOptions: Options<GetApiAdminItemsData, true> = {}, queryKey?: TQueryKey, options?: Omit<UseSuspenseQueryOptions<TData, TError>, "queryKey" | "queryFn">) => useSuspenseQuery<TData, TError>({ queryKey: Common.UseGetApiAdminItemsKeyFn(clientOptions, queryKey), queryFn: ({ signal }) => getApiAdminItems({ ...clientOptions, signal, throwOnError: true }).then(response => response.data as TData) as TData, ...options });
/**
 * A JSON Schema file (data/config/schemas*.schema.json) for the config editor
 */
export const useGetApiAdminSchemasByFilenameSuspense = <TData = NonNullable<Common.GetApiAdminSchemasByFilenameDefaultResponse>, TError = unknown, TQueryKey extends Array<unknown> = unknown[]>(clientOptions: Options<GetApiAdminSchemasByFilenameData, true>, queryKey?: TQueryKey, options?: Omit<UseSuspenseQueryOptions<TData, TError>, "queryKey" | "queryFn">) => useSuspenseQuery<TData, TError>({ queryKey: Common.UseGetApiAdminSchemasByFilenameKeyFn(clientOptions, queryKey), queryFn: ({ signal }) => getApiAdminSchemasByFilename({ ...clientOptions, signal, throwOnError: true }).then(response => response.data as TData) as TData, ...options });
/**
 * Defaults the world simulator admin UI prefills its editors with
 */
export const useGetApiAdminSimulationDefaultsSuspense = <TData = NonNullable<Common.GetApiAdminSimulationDefaultsDefaultResponse>, TError = unknown, TQueryKey extends Array<unknown> = unknown[]>(clientOptions: Options<GetApiAdminSimulationDefaultsData, true> = {}, queryKey?: TQueryKey, options?: Omit<UseSuspenseQueryOptions<TData, TError>, "queryKey" | "queryFn">) => useSuspenseQuery<TData, TError>({ queryKey: Common.UseGetApiAdminSimulationDefaultsKeyFn(clientOptions, queryKey), queryFn: ({ signal }) => getApiAdminSimulationDefaults({ ...clientOptions, signal, throwOnError: true }).then(response => response.data as TData) as TData, ...options });
/**
 * Binary-encoded chunk data (protocol.ServerMessage.ChunkData wire format). Not a JSON API — used by the game client, not by TanStack Query hooks.
 */
export const useGetApiChunksByCxByCzSuspense = <TData = NonNullable<Common.GetApiChunksByCxByCzDefaultResponse>, TError = unknown, TQueryKey extends Array<unknown> = unknown[]>(clientOptions: Options<GetApiChunksByCxByCzData, true>, queryKey?: TQueryKey, options?: Omit<UseSuspenseQueryOptions<TData, TError>, "queryKey" | "queryFn">) => useSuspenseQuery<TData, TError>({ queryKey: Common.UseGetApiChunksByCxByCzKeyFn(clientOptions, queryKey), queryFn: ({ signal }) => getApiChunksByCxByCz({ ...clientOptions, signal, throwOnError: true }).then(response => response.data as TData) as TData, ...options });
/**
 * Player characters (name + id) linked to an account email
 */
export const useGetApiPlayersByEmailByEmailSuspense = <TData = NonNullable<Common.GetApiPlayersByEmailByEmailDefaultResponse>, TError = unknown, TQueryKey extends Array<unknown> = unknown[]>(clientOptions: Options<GetApiPlayersByEmailByEmailData, true>, queryKey?: TQueryKey, options?: Omit<UseSuspenseQueryOptions<TData, TError>, "queryKey" | "queryFn">) => useSuspenseQuery<TData, TError>({ queryKey: Common.UseGetApiPlayersByEmailByEmailKeyFn(clientOptions, queryKey), queryFn: ({ signal }) => getApiPlayersByEmailByEmail({ ...clientOptions, signal, throwOnError: true }).then(response => response.data as TData) as TData, ...options });
