// generated with @7nohe/openapi-react-query-codegen@3.0.2 

import { type FetchQueryOptions, type QueryClient } from "@tanstack/react-query";
import { getApiAdminBlocks, getApiAdminChunksDiscovered, getApiAdminClasses, getApiAdminConfigs, getApiAdminConfigsBy, getApiAdminInstances, getApiAdminInstancesById, getApiAdminInstancesByIdBlocks, getApiAdminItems, getApiAdminNpcTypes, getApiAdminNpcs, getApiAdminPlainColors, getApiAdminPlayers, getApiAdminPlayersByName, getApiAdminScenes, getApiAdminScenesById, getApiAdminScenesByIdBlocksRaw, getApiAdminSchemasByFilename, getApiAdminSimulationDefaults, getApiAdminSkills, getApiAdminStatus, getApiAdminUsers, getApiAdminWorlds, getApiAdminWsInstancesById, getApiAdminWsNpcs, getApiAdminWsScenesById, getApiAdminWsSimulation, getApiArmors, getApiAssetsManifest, getApiAttacks, getApiAuthConfig, getApiAutocompleteByCommandIdByArgIndex, getApiBiomes, getApiChunksByCxByCz, getApiClasses, getApiGameAssets, getApiGameAssetsFileBy, getApiI18nByLocale, getApiItemsMeta, getApiKeybindings, getApiLayoutRegistry, getApiMacrosContext, getApiMapHouses, getApiMapRoadRaster, getApiMapRoadRasterPng, getApiMapRoads, getApiMapStaircases, getApiMapState, getApiMapTerrain, getApiMapTerrainRasterPng, getApiMapVoronoi, getApiMapVoronoiBorders, getApiPlayerByIdArmors, getApiPlayerByIdRpg, getApiPlayerByIdSkin, getApiPlayersByEmailByEmail, getApiPlayersNames, getApiQuests, getApiServerInfo, getApiSkins, getApiSkinsByNameConfig, getApiSpells, type Options } from "../requests/sdk.gen";
import type { GetApiAdminBlocksData, GetApiAdminChunksDiscoveredData, GetApiAdminClassesData, GetApiAdminConfigsByData, GetApiAdminConfigsData, GetApiAdminInstancesByIdBlocksData, GetApiAdminInstancesByIdData, GetApiAdminInstancesData, GetApiAdminItemsData, GetApiAdminNpcTypesData, GetApiAdminNpcsData, GetApiAdminPlainColorsData, GetApiAdminPlayersByNameData, GetApiAdminPlayersData, GetApiAdminScenesByIdBlocksRawData, GetApiAdminScenesByIdData, GetApiAdminScenesData, GetApiAdminSchemasByFilenameData, GetApiAdminSimulationDefaultsData, GetApiAdminSkillsData, GetApiAdminStatusData, GetApiAdminUsersData, GetApiAdminWorldsData, GetApiAdminWsInstancesByIdData, GetApiAdminWsNpcsData, GetApiAdminWsScenesByIdData, GetApiAdminWsSimulationData, GetApiArmorsData, GetApiAssetsManifestData, GetApiAttacksData, GetApiAuthConfigData, GetApiAutocompleteByCommandIdByArgIndexData, GetApiBiomesData, GetApiChunksByCxByCzData, GetApiClassesData, GetApiGameAssetsData, GetApiGameAssetsFileByData, GetApiI18nByLocaleData, GetApiItemsMetaData, GetApiKeybindingsData, GetApiLayoutRegistryData, GetApiMacrosContextData, GetApiMapHousesData, GetApiMapRoadRasterData, GetApiMapRoadRasterPngData, GetApiMapRoadsData, GetApiMapStaircasesData, GetApiMapStateData, GetApiMapTerrainData, GetApiMapTerrainRasterPngData, GetApiMapVoronoiBordersData, GetApiMapVoronoiData, GetApiPlayerByIdArmorsData, GetApiPlayerByIdRpgData, GetApiPlayerByIdSkinData, GetApiPlayersByEmailByEmailData, GetApiPlayersNamesData, GetApiQuestsData, GetApiServerInfoData, GetApiSkinsByNameConfigData, GetApiSkinsData, GetApiSpellsData } from "../requests/types.gen";
import * as Common from "./common";

/**
 * Active auth provider, used by the client to pick the right login UI
 */
export const prefetchUseGetApiAuthConfig = (queryClient: QueryClient, clientOptions: Options<GetApiAuthConfigData, true> = {}, options?: Omit<FetchQueryOptions<Common.GetApiAuthConfigDefaultResponse>, "queryKey" | "queryFn">) => queryClient.prefetchQuery({ queryKey: Common.UseGetApiAuthConfigKeyFn(clientOptions), queryFn: ({ signal }) => getApiAuthConfig({ ...clientOptions, signal, throwOnError: true }).then(response => response.data), ...options });
export const prefetchUseGetApiAssetsManifest = (queryClient: QueryClient, clientOptions: Options<GetApiAssetsManifestData, true> = {}, options?: Omit<FetchQueryOptions<Common.GetApiAssetsManifestDefaultResponse>, "queryKey" | "queryFn">) => queryClient.prefetchQuery({ queryKey: Common.UseGetApiAssetsManifestKeyFn(clientOptions), queryFn: ({ signal }) => getApiAssetsManifest({ ...clientOptions, signal, throwOnError: true }).then(response => response.data), ...options });
export const prefetchUseGetApiAdminWsNpcs = (queryClient: QueryClient, clientOptions: Options<GetApiAdminWsNpcsData, true> = {}, options?: Omit<FetchQueryOptions<Common.GetApiAdminWsNpcsDefaultResponse>, "queryKey" | "queryFn">) => queryClient.prefetchQuery({ queryKey: Common.UseGetApiAdminWsNpcsKeyFn(clientOptions), queryFn: ({ signal }) => getApiAdminWsNpcs({ ...clientOptions, signal, throwOnError: true }).then(response => response.data), ...options });
export const prefetchUseGetApiAdminWsScenesById = (queryClient: QueryClient, clientOptions: Options<GetApiAdminWsScenesByIdData, true> = {}, options?: Omit<FetchQueryOptions<Common.GetApiAdminWsScenesByIdDefaultResponse>, "queryKey" | "queryFn">) => queryClient.prefetchQuery({ queryKey: Common.UseGetApiAdminWsScenesByIdKeyFn(clientOptions), queryFn: ({ signal }) => getApiAdminWsScenesById({ ...clientOptions, signal, throwOnError: true }).then(response => response.data), ...options });
export const prefetchUseGetApiAdminWsInstancesById = (queryClient: QueryClient, clientOptions: Options<GetApiAdminWsInstancesByIdData, true> = {}, options?: Omit<FetchQueryOptions<Common.GetApiAdminWsInstancesByIdDefaultResponse>, "queryKey" | "queryFn">) => queryClient.prefetchQuery({ queryKey: Common.UseGetApiAdminWsInstancesByIdKeyFn(clientOptions), queryFn: ({ signal }) => getApiAdminWsInstancesById({ ...clientOptions, signal, throwOnError: true }).then(response => response.data), ...options });
export const prefetchUseGetApiAdminWsSimulation = (queryClient: QueryClient, clientOptions: Options<GetApiAdminWsSimulationData, true> = {}, options?: Omit<FetchQueryOptions<Common.GetApiAdminWsSimulationDefaultResponse>, "queryKey" | "queryFn">) => queryClient.prefetchQuery({ queryKey: Common.UseGetApiAdminWsSimulationKeyFn(clientOptions), queryFn: ({ signal }) => getApiAdminWsSimulation({ ...clientOptions, signal, throwOnError: true }).then(response => response.data), ...options });
/**
 * Key bindings — a player's saved bindings if ?player= is given and persistence is available, otherwise the default config
 */
export const prefetchUseGetApiKeybindings = (queryClient: QueryClient, clientOptions: Options<GetApiKeybindingsData, true> = {}, options?: Omit<FetchQueryOptions<Common.GetApiKeybindingsDefaultResponse>, "queryKey" | "queryFn">) => queryClient.prefetchQuery({ queryKey: Common.UseGetApiKeybindingsKeyFn(clientOptions), queryFn: ({ signal }) => getApiKeybindings({ ...clientOptions, signal, throwOnError: true }).then(response => response.data), ...options });
/**
 * Autocomplete suggestions for a slash command argument
 */
export const prefetchUseGetApiAutocompleteByCommandIdByArgIndex = (queryClient: QueryClient, clientOptions: Options<GetApiAutocompleteByCommandIdByArgIndexData, true>, options?: Omit<FetchQueryOptions<Common.GetApiAutocompleteByCommandIdByArgIndexDefaultResponse>, "queryKey" | "queryFn">) => queryClient.prefetchQuery({ queryKey: Common.UseGetApiAutocompleteByCommandIdByArgIndexKeyFn(clientOptions), queryFn: ({ signal }) => getApiAutocompleteByCommandIdByArgIndex({ ...clientOptions, signal, throwOnError: true }).then(response => response.data), ...options });
/**
 * Client-facing translation keys for a locale
 */
export const prefetchUseGetApiI18nByLocale = (queryClient: QueryClient, clientOptions: Options<GetApiI18nByLocaleData, true>, options?: Omit<FetchQueryOptions<Common.GetApiI18nByLocaleDefaultResponse>, "queryKey" | "queryFn">) => queryClient.prefetchQuery({ queryKey: Common.UseGetApiI18nByLocaleKeyFn(clientOptions), queryFn: ({ signal }) => getApiI18nByLocale({ ...clientOptions, signal, throwOnError: true }).then(response => response.data), ...options });
/**
 * All widgets registered for the UI layout editor
 */
export const prefetchUseGetApiLayoutRegistry = (queryClient: QueryClient, clientOptions: Options<GetApiLayoutRegistryData, true> = {}, options?: Omit<FetchQueryOptions<Common.GetApiLayoutRegistryDefaultResponse>, "queryKey" | "queryFn">) => queryClient.prefetchQuery({ queryKey: Common.UseGetApiLayoutRegistryKeyFn(clientOptions), queryFn: ({ signal }) => getApiLayoutRegistry({ ...clientOptions, signal, throwOnError: true }).then(response => response.data), ...options });
/**
 * Item metadata (label, background color, consumable flags) by item type id
 */
export const prefetchUseGetApiItemsMeta = (queryClient: QueryClient, clientOptions: Options<GetApiItemsMetaData, true> = {}, options?: Omit<FetchQueryOptions<Common.GetApiItemsMetaDefaultResponse>, "queryKey" | "queryFn">) => queryClient.prefetchQuery({ queryKey: Common.UseGetApiItemsMetaKeyFn(clientOptions), queryFn: ({ signal }) => getApiItemsMeta({ ...clientOptions, signal, throwOnError: true }).then(response => response.data), ...options });
/**
 * Server build timestamp
 */
export const prefetchUseGetApiServerInfo = (queryClient: QueryClient, clientOptions: Options<GetApiServerInfoData, true> = {}, options?: Omit<FetchQueryOptions<Common.GetApiServerInfoDefaultResponse>, "queryKey" | "queryFn">) => queryClient.prefetchQuery({ queryKey: Common.UseGetApiServerInfoKeyFn(clientOptions), queryFn: ({ signal }) => getApiServerInfo({ ...clientOptions, signal, throwOnError: true }).then(response => response.data), ...options });
/**
 * Attack definitions, flattened by "attackId:level" key
 */
export const prefetchUseGetApiAttacks = (queryClient: QueryClient, clientOptions: Options<GetApiAttacksData, true> = {}, options?: Omit<FetchQueryOptions<Common.GetApiAttacksDefaultResponse>, "queryKey" | "queryFn">) => queryClient.prefetchQuery({ queryKey: Common.UseGetApiAttacksKeyFn(clientOptions), queryFn: ({ signal }) => getApiAttacks({ ...clientOptions, signal, throwOnError: true }).then(response => response.data), ...options });
/**
 * Attack ids accessible per RPG class, keyed by level
 */
export const prefetchUseGetApiClasses = (queryClient: QueryClient, clientOptions: Options<GetApiClassesData, true> = {}, options?: Omit<FetchQueryOptions<Common.GetApiClassesDefaultResponse>, "queryKey" | "queryFn">) => queryClient.prefetchQuery({ queryKey: Common.UseGetApiClassesKeyFn(clientOptions), queryFn: ({ signal }) => getApiClasses({ ...clientOptions, signal, throwOnError: true }).then(response => response.data), ...options });
/**
 * Spell definitions, keyed by spell id
 */
export const prefetchUseGetApiSpells = (queryClient: QueryClient, clientOptions: Options<GetApiSpellsData, true> = {}, options?: Omit<FetchQueryOptions<Common.GetApiSpellsDefaultResponse>, "queryKey" | "queryFn">) => queryClient.prefetchQuery({ queryKey: Common.UseGetApiSpellsKeyFn(clientOptions), queryFn: ({ signal }) => getApiSpells({ ...clientOptions, signal, throwOnError: true }).then(response => response.data), ...options });
/**
 * Variables available to the macro JEXL evaluation context
 */
export const prefetchUseGetApiMacrosContext = (queryClient: QueryClient, clientOptions: Options<GetApiMacrosContextData, true> = {}, options?: Omit<FetchQueryOptions<Common.GetApiMacrosContextDefaultResponse>, "queryKey" | "queryFn">) => queryClient.prefetchQuery({ queryKey: Common.UseGetApiMacrosContextKeyFn(clientOptions), queryFn: ({ signal }) => getApiMacrosContext({ ...clientOptions, signal, throwOnError: true }).then(response => response.data), ...options });
/**
 * Grass color per biome id, as [r, g, b] in 0..1
 */
export const prefetchUseGetApiBiomes = (queryClient: QueryClient, clientOptions: Options<GetApiBiomesData, true> = {}, options?: Omit<FetchQueryOptions<Common.GetApiBiomesDefaultResponse>, "queryKey" | "queryFn">) => queryClient.prefetchQuery({ queryKey: Common.UseGetApiBiomesKeyFn(clientOptions), queryFn: ({ signal }) => getApiBiomes({ ...clientOptions, signal, throwOnError: true }).then(response => response.data), ...options });
/**
 * A player's current skin
 */
export const prefetchUseGetApiPlayerByIdSkin = (queryClient: QueryClient, clientOptions: Options<GetApiPlayerByIdSkinData, true>, options?: Omit<FetchQueryOptions<Common.GetApiPlayerByIdSkinDefaultResponse>, "queryKey" | "queryFn">) => queryClient.prefetchQuery({ queryKey: Common.UseGetApiPlayerByIdSkinKeyFn(clientOptions), queryFn: ({ signal }) => getApiPlayerByIdSkin({ ...clientOptions, signal, throwOnError: true }).then(response => response.data), ...options });
/**
 * Armor names currently equipped by a player
 */
export const prefetchUseGetApiPlayerByIdArmors = (queryClient: QueryClient, clientOptions: Options<GetApiPlayerByIdArmorsData, true>, options?: Omit<FetchQueryOptions<Common.GetApiPlayerByIdArmorsDefaultResponse>, "queryKey" | "queryFn">) => queryClient.prefetchQuery({ queryKey: Common.UseGetApiPlayerByIdArmorsKeyFn(clientOptions), queryFn: ({ signal }) => getApiPlayerByIdArmors({ ...clientOptions, signal, throwOnError: true }).then(response => response.data), ...options });
/**
 * A player's RPG character class
 */
export const prefetchUseGetApiPlayerByIdRpg = (queryClient: QueryClient, clientOptions: Options<GetApiPlayerByIdRpgData, true>, options?: Omit<FetchQueryOptions<Common.GetApiPlayerByIdRpgDefaultResponse>, "queryKey" | "queryFn">) => queryClient.prefetchQuery({ queryKey: Common.UseGetApiPlayerByIdRpgKeyFn(clientOptions), queryFn: ({ signal }) => getApiPlayerByIdRpg({ ...clientOptions, signal, throwOnError: true }).then(response => response.data), ...options });
/**
 * Names of all available player skins
 */
export const prefetchUseGetApiSkins = (queryClient: QueryClient, clientOptions: Options<GetApiSkinsData, true> = {}, options?: Omit<FetchQueryOptions<Common.GetApiSkinsDefaultResponse>, "queryKey" | "queryFn">) => queryClient.prefetchQuery({ queryKey: Common.UseGetApiSkinsKeyFn(clientOptions), queryFn: ({ signal }) => getApiSkins({ ...clientOptions, signal, throwOnError: true }).then(response => response.data), ...options });
/**
 * Skin config (eye offset, hidden bones) for a named skin
 */
export const prefetchUseGetApiSkinsByNameConfig = (queryClient: QueryClient, clientOptions: Options<GetApiSkinsByNameConfigData, true>, options?: Omit<FetchQueryOptions<Common.GetApiSkinsByNameConfigDefaultResponse>, "queryKey" | "queryFn">) => queryClient.prefetchQuery({ queryKey: Common.UseGetApiSkinsByNameConfigKeyFn(clientOptions), queryFn: ({ signal }) => getApiSkinsByNameConfig({ ...clientOptions, signal, throwOnError: true }).then(response => response.data), ...options });
/**
 * List all armor definitions
 */
export const prefetchUseGetApiArmors = (queryClient: QueryClient, clientOptions: Options<GetApiArmorsData, true> = {}, options?: Omit<FetchQueryOptions<Common.GetApiArmorsDefaultResponse>, "queryKey" | "queryFn">) => queryClient.prefetchQuery({ queryKey: Common.UseGetApiArmorsKeyFn(clientOptions), queryFn: ({ signal }) => getApiArmors({ ...clientOptions, signal, throwOnError: true }).then(response => response.data), ...options });
/**
 * 3D game asset files discovered under resources/game-assets
 */
export const prefetchUseGetApiGameAssets = (queryClient: QueryClient, clientOptions: Options<GetApiGameAssetsData, true> = {}, options?: Omit<FetchQueryOptions<Common.GetApiGameAssetsDefaultResponse>, "queryKey" | "queryFn">) => queryClient.prefetchQuery({ queryKey: Common.UseGetApiGameAssetsKeyFn(clientOptions), queryFn: ({ signal }) => getApiGameAssets({ ...clientOptions, signal, throwOnError: true }).then(response => response.data), ...options });
/**
 * Raw asset file bytes (glb/gltf/fbx/textures)
 */
export const prefetchUseGetApiGameAssetsFileBy = (queryClient: QueryClient, clientOptions: Options<GetApiGameAssetsFileByData, true>, options?: Omit<FetchQueryOptions<Common.GetApiGameAssetsFileByDefaultResponse>, "queryKey" | "queryFn">) => queryClient.prefetchQuery({ queryKey: Common.UseGetApiGameAssetsFileByKeyFn(clientOptions), queryFn: ({ signal }) => getApiGameAssetsFileBy({ ...clientOptions, signal, throwOnError: true }).then(response => response.data), ...options });
/**
 * All quest definitions
 */
export const prefetchUseGetApiQuests = (queryClient: QueryClient, clientOptions: Options<GetApiQuestsData, true> = {}, options?: Omit<FetchQueryOptions<Common.GetApiQuestsDefaultResponse>, "queryKey" | "queryFn">) => queryClient.prefetchQuery({ queryKey: Common.UseGetApiQuestsKeyFn(clientOptions), queryFn: ({ signal }) => getApiQuests({ ...clientOptions, signal, throwOnError: true }).then(response => response.data), ...options });
/**
 * Names of all known players
 */
export const prefetchUseGetApiPlayersNames = (queryClient: QueryClient, clientOptions: Options<GetApiPlayersNamesData, true> = {}, options?: Omit<FetchQueryOptions<Common.GetApiPlayersNamesDefaultResponse>, "queryKey" | "queryFn">) => queryClient.prefetchQuery({ queryKey: Common.UseGetApiPlayersNamesKeyFn(clientOptions), queryFn: ({ signal }) => getApiPlayersNames({ ...clientOptions, signal, throwOnError: true }).then(response => response.data), ...options });
/**
 * Live players, NPCs and weather zones for the map overlay
 */
export const prefetchUseGetApiMapState = (queryClient: QueryClient, clientOptions: Options<GetApiMapStateData, true> = {}, options?: Omit<FetchQueryOptions<Common.GetApiMapStateDefaultResponse>, "queryKey" | "queryFn">) => queryClient.prefetchQuery({ queryKey: Common.UseGetApiMapStateKeyFn(clientOptions), queryFn: ({ signal }) => getApiMapState({ ...clientOptions, signal, throwOnError: true }).then(response => response.data), ...options });
/**
 * Cached terrain summary JSON for the map overlay
 */
export const prefetchUseGetApiMapTerrain = (queryClient: QueryClient, clientOptions: Options<GetApiMapTerrainData, true> = {}, options?: Omit<FetchQueryOptions<Common.GetApiMapTerrainDefaultResponse>, "queryKey" | "queryFn">) => queryClient.prefetchQuery({ queryKey: Common.UseGetApiMapTerrainKeyFn(clientOptions), queryFn: ({ signal }) => getApiMapTerrain({ ...clientOptions, signal, throwOnError: true }).then(response => response.data), ...options });
/**
 * Voronoi biome cells around a point
 */
export const prefetchUseGetApiMapVoronoi = (queryClient: QueryClient, clientOptions: Options<GetApiMapVoronoiData, true> = {}, options?: Omit<FetchQueryOptions<Common.GetApiMapVoronoiDefaultResponse>, "queryKey" | "queryFn">) => queryClient.prefetchQuery({ queryKey: Common.UseGetApiMapVoronoiKeyFn(clientOptions), queryFn: ({ signal }) => getApiMapVoronoi({ ...clientOptions, signal, throwOnError: true }).then(response => response.data), ...options });
/**
 * Generated houses within an area
 */
export const prefetchUseGetApiMapHouses = (queryClient: QueryClient, clientOptions: Options<GetApiMapHousesData, true> = {}, options?: Omit<FetchQueryOptions<Common.GetApiMapHousesDefaultResponse>, "queryKey" | "queryFn">) => queryClient.prefetchQuery({ queryKey: Common.UseGetApiMapHousesKeyFn(clientOptions), queryFn: ({ signal }) => getApiMapHouses({ ...clientOptions, signal, throwOnError: true }).then(response => response.data), ...options });
/**
 * Named staircase points for the map overlay
 */
export const prefetchUseGetApiMapStaircases = (queryClient: QueryClient, clientOptions: Options<GetApiMapStaircasesData, true> = {}, options?: Omit<FetchQueryOptions<Common.GetApiMapStaircasesDefaultResponse>, "queryKey" | "queryFn">) => queryClient.prefetchQuery({ queryKey: Common.UseGetApiMapStaircasesKeyFn(clientOptions), queryFn: ({ signal }) => getApiMapStaircases({ ...clientOptions, signal, throwOnError: true }).then(response => response.data), ...options });
/**
 * Road vertex segments within an area
 */
export const prefetchUseGetApiMapRoads = (queryClient: QueryClient, clientOptions: Options<GetApiMapRoadsData, true> = {}, options?: Omit<FetchQueryOptions<Common.GetApiMapRoadsDefaultResponse>, "queryKey" | "queryFn">) => queryClient.prefetchQuery({ queryKey: Common.UseGetApiMapRoadsKeyFn(clientOptions), queryFn: ({ signal }) => getApiMapRoads({ ...clientOptions, signal, throwOnError: true }).then(response => response.data), ...options });
/**
 * Per-chunk road bitmask within an area
 */
export const prefetchUseGetApiMapRoadRaster = (queryClient: QueryClient, clientOptions: Options<GetApiMapRoadRasterData, true> = {}, options?: Omit<FetchQueryOptions<Common.GetApiMapRoadRasterDefaultResponse>, "queryKey" | "queryFn">) => queryClient.prefetchQuery({ queryKey: Common.UseGetApiMapRoadRasterKeyFn(clientOptions), queryFn: ({ signal }) => getApiMapRoadRaster({ ...clientOptions, signal, throwOnError: true }).then(response => response.data), ...options });
/**
 * Rasterized road overlay as a PNG image
 */
export const prefetchUseGetApiMapRoadRasterPng = (queryClient: QueryClient, clientOptions: Options<GetApiMapRoadRasterPngData, true> = {}, options?: Omit<FetchQueryOptions<Common.GetApiMapRoadRasterPngDefaultResponse>, "queryKey" | "queryFn">) => queryClient.prefetchQuery({ queryKey: Common.UseGetApiMapRoadRasterPngKeyFn(clientOptions), queryFn: ({ signal }) => getApiMapRoadRasterPng({ ...clientOptions, signal, throwOnError: true }).then(response => response.data), ...options });
/**
 * Rasterized terrain overlay as a PNG image
 */
export const prefetchUseGetApiMapTerrainRasterPng = (queryClient: QueryClient, clientOptions: Options<GetApiMapTerrainRasterPngData, true> = {}, options?: Omit<FetchQueryOptions<Common.GetApiMapTerrainRasterPngDefaultResponse>, "queryKey" | "queryFn">) => queryClient.prefetchQuery({ queryKey: Common.UseGetApiMapTerrainRasterPngKeyFn(clientOptions), queryFn: ({ signal }) => getApiMapTerrainRasterPng({ ...clientOptions, signal, throwOnError: true }).then(response => response.data), ...options });
/**
 * Voronoi cell border segments within an area
 */
export const prefetchUseGetApiMapVoronoiBorders = (queryClient: QueryClient, clientOptions: Options<GetApiMapVoronoiBordersData, true> = {}, options?: Omit<FetchQueryOptions<Common.GetApiMapVoronoiBordersDefaultResponse>, "queryKey" | "queryFn">) => queryClient.prefetchQuery({ queryKey: Common.UseGetApiMapVoronoiBordersKeyFn(clientOptions), queryFn: ({ signal }) => getApiMapVoronoiBorders({ ...clientOptions, signal, throwOnError: true }).then(response => response.data), ...options });
/**
 * Server status snapshot (TPS, players, chunks, heap, CPU)
 */
export const prefetchUseGetApiAdminStatus = (queryClient: QueryClient, clientOptions: Options<GetApiAdminStatusData, true> = {}, options?: Omit<FetchQueryOptions<Common.GetApiAdminStatusDefaultResponse>, "queryKey" | "queryFn">) => queryClient.prefetchQuery({ queryKey: Common.UseGetApiAdminStatusKeyFn(clientOptions), queryFn: ({ signal }) => getApiAdminStatus({ ...clientOptions, signal, throwOnError: true }).then(response => response.data), ...options });
/**
 * All local/no-auth accounts
 */
export const prefetchUseGetApiAdminUsers = (queryClient: QueryClient, clientOptions: Options<GetApiAdminUsersData, true> = {}, options?: Omit<FetchQueryOptions<Common.GetApiAdminUsersDefaultResponse>, "queryKey" | "queryFn">) => queryClient.prefetchQuery({ queryKey: Common.UseGetApiAdminUsersKeyFn(clientOptions), queryFn: ({ signal }) => getApiAdminUsers({ ...clientOptions, signal, throwOnError: true }).then(response => response.data), ...options });
/**
 * All player names
 */
export const prefetchUseGetApiAdminPlayers = (queryClient: QueryClient, clientOptions: Options<GetApiAdminPlayersData, true> = {}, options?: Omit<FetchQueryOptions<Common.GetApiAdminPlayersDefaultResponse>, "queryKey" | "queryFn">) => queryClient.prefetchQuery({ queryKey: Common.UseGetApiAdminPlayersKeyFn(clientOptions), queryFn: ({ signal }) => getApiAdminPlayers({ ...clientOptions, signal, throwOnError: true }).then(response => response.data), ...options });
/**
 * Full player file (state, keybindings, RPG data)
 */
export const prefetchUseGetApiAdminPlayersByName = (queryClient: QueryClient, clientOptions: Options<GetApiAdminPlayersByNameData, true>, options?: Omit<FetchQueryOptions<Common.GetApiAdminPlayersByNameDefaultResponse>, "queryKey" | "queryFn">) => queryClient.prefetchQuery({ queryKey: Common.UseGetApiAdminPlayersByNameKeyFn(clientOptions), queryFn: ({ signal }) => getApiAdminPlayersByName({ ...clientOptions, signal, throwOnError: true }).then(response => response.data), ...options });
/**
 * All worlds on disk, with stats
 */
export const prefetchUseGetApiAdminWorlds = (queryClient: QueryClient, clientOptions: Options<GetApiAdminWorldsData, true> = {}, options?: Omit<FetchQueryOptions<Common.GetApiAdminWorldsDefaultResponse>, "queryKey" | "queryFn">) => queryClient.prefetchQuery({ queryKey: Common.UseGetApiAdminWorldsKeyFn(clientOptions), queryFn: ({ signal }) => getApiAdminWorlds({ ...clientOptions, signal, throwOnError: true }).then(response => response.data), ...options });
/**
 * Names of all whitelisted editable YAML config files
 */
export const prefetchUseGetApiAdminConfigs = (queryClient: QueryClient, clientOptions: Options<GetApiAdminConfigsData, true> = {}, options?: Omit<FetchQueryOptions<Common.GetApiAdminConfigsDefaultResponse>, "queryKey" | "queryFn">) => queryClient.prefetchQuery({ queryKey: Common.UseGetApiAdminConfigsKeyFn(clientOptions), queryFn: ({ signal }) => getApiAdminConfigs({ ...clientOptions, signal, throwOnError: true }).then(response => response.data), ...options });
/**
 * Raw YAML content of a whitelisted config file
 */
export const prefetchUseGetApiAdminConfigsBy = (queryClient: QueryClient, clientOptions: Options<GetApiAdminConfigsByData, true>, options?: Omit<FetchQueryOptions<Common.GetApiAdminConfigsByDefaultResponse>, "queryKey" | "queryFn">) => queryClient.prefetchQuery({ queryKey: Common.UseGetApiAdminConfigsByKeyFn(clientOptions), queryFn: ({ signal }) => getApiAdminConfigsBy({ ...clientOptions, signal, throwOnError: true }).then(response => response.data), ...options });
/**
 * RPG class definitions, keyed by class name
 */
export const prefetchUseGetApiAdminClasses = (queryClient: QueryClient, clientOptions: Options<GetApiAdminClassesData, true> = {}, options?: Omit<FetchQueryOptions<Common.GetApiAdminClassesDefaultResponse>, "queryKey" | "queryFn">) => queryClient.prefetchQuery({ queryKey: Common.UseGetApiAdminClassesKeyFn(clientOptions), queryFn: ({ signal }) => getApiAdminClasses({ ...clientOptions, signal, throwOnError: true }).then(response => response.data), ...options });
/**
 * All attack and spell ids
 */
export const prefetchUseGetApiAdminSkills = (queryClient: QueryClient, clientOptions: Options<GetApiAdminSkillsData, true> = {}, options?: Omit<FetchQueryOptions<Common.GetApiAdminSkillsDefaultResponse>, "queryKey" | "queryFn">) => queryClient.prefetchQuery({ queryKey: Common.UseGetApiAdminSkillsKeyFn(clientOptions), queryFn: ({ signal }) => getApiAdminSkills({ ...clientOptions, signal, throwOnError: true }).then(response => response.data), ...options });
/**
 * Live NPC instances with full animal/combat state
 */
export const prefetchUseGetApiAdminNpcs = (queryClient: QueryClient, clientOptions: Options<GetApiAdminNpcsData, true> = {}, options?: Omit<FetchQueryOptions<Common.GetApiAdminNpcsDefaultResponse>, "queryKey" | "queryFn">) => queryClient.prefetchQuery({ queryKey: Common.UseGetApiAdminNpcsKeyFn(clientOptions), queryFn: ({ signal }) => getApiAdminNpcs({ ...clientOptions, signal, throwOnError: true }).then(response => response.data), ...options });
/**
 * All registered block definitions
 */
export const prefetchUseGetApiAdminBlocks = (queryClient: QueryClient, clientOptions: Options<GetApiAdminBlocksData, true> = {}, options?: Omit<FetchQueryOptions<Common.GetApiAdminBlocksDefaultResponse>, "queryKey" | "queryFn">) => queryClient.prefetchQuery({ queryKey: Common.UseGetApiAdminBlocksKeyFn(clientOptions), queryFn: ({ signal }) => getApiAdminBlocks({ ...clientOptions, signal, throwOnError: true }).then(response => response.data), ...options });
/**
 * All registered plain paint colors
 */
export const prefetchUseGetApiAdminPlainColors = (queryClient: QueryClient, clientOptions: Options<GetApiAdminPlainColorsData, true> = {}, options?: Omit<FetchQueryOptions<Common.GetApiAdminPlainColorsDefaultResponse>, "queryKey" | "queryFn">) => queryClient.prefetchQuery({ queryKey: Common.UseGetApiAdminPlainColorsKeyFn(clientOptions), queryFn: ({ signal }) => getApiAdminPlainColors({ ...clientOptions, signal, throwOnError: true }).then(response => response.data), ...options });
/**
 * All chunk coordinates generated so far (in-memory ∪ persisted)
 */
export const prefetchUseGetApiAdminChunksDiscovered = (queryClient: QueryClient, clientOptions: Options<GetApiAdminChunksDiscoveredData, true> = {}, options?: Omit<FetchQueryOptions<Common.GetApiAdminChunksDiscoveredDefaultResponse>, "queryKey" | "queryFn">) => queryClient.prefetchQuery({ queryKey: Common.UseGetApiAdminChunksDiscoveredKeyFn(clientOptions), queryFn: ({ signal }) => getApiAdminChunksDiscovered({ ...clientOptions, signal, throwOnError: true }).then(response => response.data), ...options });
/**
 * All instance zones
 */
export const prefetchUseGetApiAdminInstances = (queryClient: QueryClient, clientOptions: Options<GetApiAdminInstancesData, true> = {}, options?: Omit<FetchQueryOptions<Common.GetApiAdminInstancesDefaultResponse>, "queryKey" | "queryFn">) => queryClient.prefetchQuery({ queryKey: Common.UseGetApiAdminInstancesKeyFn(clientOptions), queryFn: ({ signal }) => getApiAdminInstances({ ...clientOptions, signal, throwOnError: true }).then(response => response.data), ...options });
/**
 * A single instance zone
 */
export const prefetchUseGetApiAdminInstancesById = (queryClient: QueryClient, clientOptions: Options<GetApiAdminInstancesByIdData, true>, options?: Omit<FetchQueryOptions<Common.GetApiAdminInstancesByIdDefaultResponse>, "queryKey" | "queryFn">) => queryClient.prefetchQuery({ queryKey: Common.UseGetApiAdminInstancesByIdKeyFn(clientOptions), queryFn: ({ signal }) => getApiAdminInstancesById({ ...clientOptions, signal, throwOnError: true }).then(response => response.data), ...options });
/**
 * Non-air blocks in an instance zone, streamed as newline-delimited JSON (application/x-ndjson, one InstanceBlockDto per line), capped at 300000
 */
export const prefetchUseGetApiAdminInstancesByIdBlocks = (queryClient: QueryClient, clientOptions: Options<GetApiAdminInstancesByIdBlocksData, true>, options?: Omit<FetchQueryOptions<Common.GetApiAdminInstancesByIdBlocksDefaultResponse>, "queryKey" | "queryFn">) => queryClient.prefetchQuery({ queryKey: Common.UseGetApiAdminInstancesByIdBlocksKeyFn(clientOptions), queryFn: ({ signal }) => getApiAdminInstancesByIdBlocks({ ...clientOptions, signal, throwOnError: true }).then(response => response.data), ...options });
/**
 * All scenes (bounded off-world block-structure buffers)
 */
export const prefetchUseGetApiAdminScenes = (queryClient: QueryClient, clientOptions: Options<GetApiAdminScenesData, true> = {}, options?: Omit<FetchQueryOptions<Common.GetApiAdminScenesDefaultResponse>, "queryKey" | "queryFn">) => queryClient.prefetchQuery({ queryKey: Common.UseGetApiAdminScenesKeyFn(clientOptions), queryFn: ({ signal }) => getApiAdminScenes({ ...clientOptions, signal, throwOnError: true }).then(response => response.data), ...options });
/**
 * A single scene's metadata
 */
export const prefetchUseGetApiAdminScenesById = (queryClient: QueryClient, clientOptions: Options<GetApiAdminScenesByIdData, true>, options?: Omit<FetchQueryOptions<Common.GetApiAdminScenesByIdDefaultResponse>, "queryKey" | "queryFn">) => queryClient.prefetchQuery({ queryKey: Common.UseGetApiAdminScenesByIdKeyFn(clientOptions), queryFn: ({ signal }) => getApiAdminScenesById({ ...clientOptions, signal, throwOnError: true }).then(response => response.data), ...options });
/**
 * Scene block/state buffers as a binary blob: 3×4-byte big-endian dimensions (width,height,depth) followed by the blocks byte array then the states byte array (wire-index-per-byte, 0 = AIR)
 */
export const prefetchUseGetApiAdminScenesByIdBlocksRaw = (queryClient: QueryClient, clientOptions: Options<GetApiAdminScenesByIdBlocksRawData, true>, options?: Omit<FetchQueryOptions<Common.GetApiAdminScenesByIdBlocksRawDefaultResponse>, "queryKey" | "queryFn">) => queryClient.prefetchQuery({ queryKey: Common.UseGetApiAdminScenesByIdBlocksRawKeyFn(clientOptions), queryFn: ({ signal }) => getApiAdminScenesByIdBlocksRaw({ ...clientOptions, signal, throwOnError: true }).then(response => response.data), ...options });
/**
 * NPC type definitions (codex info), keyed by type id
 */
export const prefetchUseGetApiAdminNpcTypes = (queryClient: QueryClient, clientOptions: Options<GetApiAdminNpcTypesData, true> = {}, options?: Omit<FetchQueryOptions<Common.GetApiAdminNpcTypesDefaultResponse>, "queryKey" | "queryFn">) => queryClient.prefetchQuery({ queryKey: Common.UseGetApiAdminNpcTypesKeyFn(clientOptions), queryFn: ({ signal }) => getApiAdminNpcTypes({ ...clientOptions, signal, throwOnError: true }).then(response => response.data), ...options });
/**
 * Item definitions, keyed by item type id
 */
export const prefetchUseGetApiAdminItems = (queryClient: QueryClient, clientOptions: Options<GetApiAdminItemsData, true> = {}, options?: Omit<FetchQueryOptions<Common.GetApiAdminItemsDefaultResponse>, "queryKey" | "queryFn">) => queryClient.prefetchQuery({ queryKey: Common.UseGetApiAdminItemsKeyFn(clientOptions), queryFn: ({ signal }) => getApiAdminItems({ ...clientOptions, signal, throwOnError: true }).then(response => response.data), ...options });
/**
 * A JSON Schema file (data/config/schemas*.schema.json) for the config editor
 */
export const prefetchUseGetApiAdminSchemasByFilename = (queryClient: QueryClient, clientOptions: Options<GetApiAdminSchemasByFilenameData, true>, options?: Omit<FetchQueryOptions<Common.GetApiAdminSchemasByFilenameDefaultResponse>, "queryKey" | "queryFn">) => queryClient.prefetchQuery({ queryKey: Common.UseGetApiAdminSchemasByFilenameKeyFn(clientOptions), queryFn: ({ signal }) => getApiAdminSchemasByFilename({ ...clientOptions, signal, throwOnError: true }).then(response => response.data), ...options });
/**
 * Defaults the world simulator admin UI prefills its editors with
 */
export const prefetchUseGetApiAdminSimulationDefaults = (queryClient: QueryClient, clientOptions: Options<GetApiAdminSimulationDefaultsData, true> = {}, options?: Omit<FetchQueryOptions<Common.GetApiAdminSimulationDefaultsDefaultResponse>, "queryKey" | "queryFn">) => queryClient.prefetchQuery({ queryKey: Common.UseGetApiAdminSimulationDefaultsKeyFn(clientOptions), queryFn: ({ signal }) => getApiAdminSimulationDefaults({ ...clientOptions, signal, throwOnError: true }).then(response => response.data), ...options });
/**
 * Binary-encoded chunk data (protocol.ServerMessage.ChunkData wire format). Not a JSON API — used by the game client, not by TanStack Query hooks.
 */
export const prefetchUseGetApiChunksByCxByCz = (queryClient: QueryClient, clientOptions: Options<GetApiChunksByCxByCzData, true>, options?: Omit<FetchQueryOptions<Common.GetApiChunksByCxByCzDefaultResponse>, "queryKey" | "queryFn">) => queryClient.prefetchQuery({ queryKey: Common.UseGetApiChunksByCxByCzKeyFn(clientOptions), queryFn: ({ signal }) => getApiChunksByCxByCz({ ...clientOptions, signal, throwOnError: true }).then(response => response.data), ...options });
/**
 * Player characters (name + id) linked to an account email
 */
export const prefetchUseGetApiPlayersByEmailByEmail = (queryClient: QueryClient, clientOptions: Options<GetApiPlayersByEmailByEmailData, true>, options?: Omit<FetchQueryOptions<Common.GetApiPlayersByEmailByEmailDefaultResponse>, "queryKey" | "queryFn">) => queryClient.prefetchQuery({ queryKey: Common.UseGetApiPlayersByEmailByEmailKeyFn(clientOptions), queryFn: ({ signal }) => getApiPlayersByEmailByEmail({ ...clientOptions, signal, throwOnError: true }).then(response => response.data), ...options });
