// generated with @7nohe/openapi-react-query-codegen@3.0.2 

import { type UseQueryResult } from "@tanstack/react-query";
import { deleteApiAdminInstancesById, deleteApiAdminScenesById, deleteApiAdminUsersByEmail, deleteApiGameAssetsBlendCacheBy, getApiAdminBlocks, getApiAdminChunksDiscovered, getApiAdminClasses, getApiAdminConfigs, getApiAdminConfigsBy, getApiAdminInstances, getApiAdminInstancesById, getApiAdminInstancesByIdBlocks, getApiAdminItems, getApiAdminNpcTypes, getApiAdminNpcs, getApiAdminPlainColors, getApiAdminPlayers, getApiAdminPlayersByName, getApiAdminScenes, getApiAdminScenesById, getApiAdminScenesByIdBlocksRaw, getApiAdminScenesByIdEntities, getApiAdminSchemasByFilename, getApiAdminSimulationDefaults, getApiAdminSkills, getApiAdminStatus, getApiAdminUsers, getApiAdminWorlds, getApiAdminWsInstancesById, getApiAdminWsNpcs, getApiAdminWsScenesById, getApiAdminWsSimulation, getApiArmors, getApiAssetsManifest, getApiAttacks, getApiAuthConfig, getApiAutocompleteByCommandIdByArgIndex, getApiBiomes, getApiChunksByCxByCz, getApiClasses, getApiGameAssets, getApiGameAssetsBbmodelExportBy, getApiGameAssetsBlendPreviewBy, getApiGameAssetsBlendSceneBy, getApiGameAssetsFileBy, getApiI18nByLocale, getApiItemsMeta, getApiKeybindings, getApiLayoutRegistry, getApiMacrosContext, getApiMapHouses, getApiMapRoadRaster, getApiMapRoadRasterPng, getApiMapRoads, getApiMapStaircases, getApiMapState, getApiMapTerrain, getApiMapTerrainRasterPng, getApiMapVoronoi, getApiMapVoronoiBorders, getApiPlayerByIdArmors, getApiPlayerByIdHands, getApiPlayerByIdOwned, getApiPlayerByIdRpg, getApiPlayerByIdSkin, getApiPlayersByEmailByEmail, getApiPlayersNames, getApiQuests, getApiServerInfo, getApiSiegeWeapons, getApiSkins, getApiSkinsByNameConfig, getApiSpells, getApiTools, getApiVehiclesByNameConfig, getApiWeapons, postApiAdminInstances, postApiAdminPlayersByNameGive, postApiAdminPlayersByNameRename, postApiAdminReload, postApiAdminRestart, postApiAdminScenes, postApiAdminScenesByIdDuplicate, postApiAdminUsers, postApiAdminWorlds, postApiAssetsReload, postApiCharacterCreate, postApiCharacterRpgcreate, postApiPlayerByIdScreenshots, postAuthNoauthLogin, putApiAdminConfigsBy, putApiAdminGametime, putApiAdminInstancesById, putApiAdminInstancesByIdBounds, putApiAdminInstancesByIdChunks, putApiAdminInstancesByIdEnabled, putApiAdminInstancesByIdLayout, putApiAdminPlayersByNameEquipment, putApiAdminPlayersByNameKeybindings, putApiAdminPlayersByNamePreferences, putApiAdminPlayersByNameRpg, putApiAdminScenesById, putApiAdminScenesByIdDimensions, putApiAdminScenesByIdLayout, putApiAdminUsersByEmail, putApiPlayerByIdSkin, type Options } from "../requests/sdk.gen";
import type { GetApiAdminBlocksData, GetApiAdminChunksDiscoveredData, GetApiAdminClassesData, GetApiAdminConfigsByData, GetApiAdminConfigsData, GetApiAdminInstancesByIdBlocksData, GetApiAdminInstancesByIdData, GetApiAdminInstancesData, GetApiAdminItemsData, GetApiAdminNpcTypesData, GetApiAdminNpcsData, GetApiAdminPlainColorsData, GetApiAdminPlayersByNameData, GetApiAdminPlayersData, GetApiAdminScenesByIdBlocksRawData, GetApiAdminScenesByIdData, GetApiAdminScenesByIdEntitiesData, GetApiAdminScenesData, GetApiAdminSchemasByFilenameData, GetApiAdminSimulationDefaultsData, GetApiAdminSkillsData, GetApiAdminStatusData, GetApiAdminUsersData, GetApiAdminWorldsData, GetApiAdminWsInstancesByIdData, GetApiAdminWsNpcsData, GetApiAdminWsScenesByIdData, GetApiAdminWsSimulationData, GetApiArmorsData, GetApiAssetsManifestData, GetApiAttacksData, GetApiAuthConfigData, GetApiAutocompleteByCommandIdByArgIndexData, GetApiBiomesData, GetApiChunksByCxByCzData, GetApiClassesData, GetApiGameAssetsBbmodelExportByData, GetApiGameAssetsBlendPreviewByData, GetApiGameAssetsBlendSceneByData, GetApiGameAssetsData, GetApiGameAssetsFileByData, GetApiI18nByLocaleData, GetApiItemsMetaData, GetApiKeybindingsData, GetApiLayoutRegistryData, GetApiMacrosContextData, GetApiMapHousesData, GetApiMapRoadRasterData, GetApiMapRoadRasterPngData, GetApiMapRoadsData, GetApiMapStaircasesData, GetApiMapStateData, GetApiMapTerrainData, GetApiMapTerrainRasterPngData, GetApiMapVoronoiBordersData, GetApiMapVoronoiData, GetApiPlayerByIdArmorsData, GetApiPlayerByIdHandsData, GetApiPlayerByIdOwnedData, GetApiPlayerByIdRpgData, GetApiPlayerByIdSkinData, GetApiPlayersByEmailByEmailData, GetApiPlayersNamesData, GetApiQuestsData, GetApiServerInfoData, GetApiSiegeWeaponsData, GetApiSkinsByNameConfigData, GetApiSkinsData, GetApiSpellsData, GetApiToolsData, GetApiVehiclesByNameConfigData, GetApiWeaponsData } from "../requests/types.gen";

export type GetApiAuthConfigDefaultResponse = Awaited<ReturnType<typeof getApiAuthConfig>>["data"];
export type GetApiAuthConfigQueryResult<TData = GetApiAuthConfigDefaultResponse, TError = unknown> = UseQueryResult<TData, TError>;

export const useGetApiAuthConfigKey = "GetApiAuthConfig";
export const UseGetApiAuthConfigKeyFn = (clientOptions: Options<GetApiAuthConfigData, true> = {}, queryKey?: Array<unknown>) => [useGetApiAuthConfigKey, ...(queryKey ?? [clientOptions])];

export type GetApiAssetsManifestDefaultResponse = Awaited<ReturnType<typeof getApiAssetsManifest>>["data"];
export type GetApiAssetsManifestQueryResult<TData = GetApiAssetsManifestDefaultResponse, TError = unknown> = UseQueryResult<TData, TError>;

export const useGetApiAssetsManifestKey = "GetApiAssetsManifest";
export const UseGetApiAssetsManifestKeyFn = (clientOptions: Options<GetApiAssetsManifestData, true> = {}, queryKey?: Array<unknown>) => [useGetApiAssetsManifestKey, ...(queryKey ?? [clientOptions])];

export type GetApiAdminWsNpcsDefaultResponse = Awaited<ReturnType<typeof getApiAdminWsNpcs>>["data"];
export type GetApiAdminWsNpcsQueryResult<TData = GetApiAdminWsNpcsDefaultResponse, TError = unknown> = UseQueryResult<TData, TError>;

export const useGetApiAdminWsNpcsKey = "GetApiAdminWsNpcs";
export const UseGetApiAdminWsNpcsKeyFn = (clientOptions: Options<GetApiAdminWsNpcsData, true> = {}, queryKey?: Array<unknown>) => [useGetApiAdminWsNpcsKey, ...(queryKey ?? [clientOptions])];

export type GetApiAdminWsScenesByIdDefaultResponse = Awaited<ReturnType<typeof getApiAdminWsScenesById>>["data"];
export type GetApiAdminWsScenesByIdQueryResult<TData = GetApiAdminWsScenesByIdDefaultResponse, TError = unknown> = UseQueryResult<TData, TError>;

export const useGetApiAdminWsScenesByIdKey = "GetApiAdminWsScenesById";
export const UseGetApiAdminWsScenesByIdKeyFn = (clientOptions: Options<GetApiAdminWsScenesByIdData, true> = {}, queryKey?: Array<unknown>) => [useGetApiAdminWsScenesByIdKey, ...(queryKey ?? [clientOptions])];

export type GetApiAdminWsInstancesByIdDefaultResponse = Awaited<ReturnType<typeof getApiAdminWsInstancesById>>["data"];
export type GetApiAdminWsInstancesByIdQueryResult<TData = GetApiAdminWsInstancesByIdDefaultResponse, TError = unknown> = UseQueryResult<TData, TError>;

export const useGetApiAdminWsInstancesByIdKey = "GetApiAdminWsInstancesById";
export const UseGetApiAdminWsInstancesByIdKeyFn = (clientOptions: Options<GetApiAdminWsInstancesByIdData, true> = {}, queryKey?: Array<unknown>) => [useGetApiAdminWsInstancesByIdKey, ...(queryKey ?? [clientOptions])];

export type GetApiAdminWsSimulationDefaultResponse = Awaited<ReturnType<typeof getApiAdminWsSimulation>>["data"];
export type GetApiAdminWsSimulationQueryResult<TData = GetApiAdminWsSimulationDefaultResponse, TError = unknown> = UseQueryResult<TData, TError>;

export const useGetApiAdminWsSimulationKey = "GetApiAdminWsSimulation";
export const UseGetApiAdminWsSimulationKeyFn = (clientOptions: Options<GetApiAdminWsSimulationData, true> = {}, queryKey?: Array<unknown>) => [useGetApiAdminWsSimulationKey, ...(queryKey ?? [clientOptions])];

export type GetApiKeybindingsDefaultResponse = Awaited<ReturnType<typeof getApiKeybindings>>["data"];
export type GetApiKeybindingsQueryResult<TData = GetApiKeybindingsDefaultResponse, TError = unknown> = UseQueryResult<TData, TError>;

export const useGetApiKeybindingsKey = "GetApiKeybindings";
export const UseGetApiKeybindingsKeyFn = (clientOptions: Options<GetApiKeybindingsData, true> = {}, queryKey?: Array<unknown>) => [useGetApiKeybindingsKey, ...(queryKey ?? [clientOptions])];

export type GetApiAutocompleteByCommandIdByArgIndexDefaultResponse = Awaited<ReturnType<typeof getApiAutocompleteByCommandIdByArgIndex>>["data"];
export type GetApiAutocompleteByCommandIdByArgIndexQueryResult<TData = GetApiAutocompleteByCommandIdByArgIndexDefaultResponse, TError = unknown> = UseQueryResult<TData, TError>;

export const useGetApiAutocompleteByCommandIdByArgIndexKey = "GetApiAutocompleteByCommandIdByArgIndex";
export const UseGetApiAutocompleteByCommandIdByArgIndexKeyFn = (clientOptions: Options<GetApiAutocompleteByCommandIdByArgIndexData, true>, queryKey?: Array<unknown>) => [useGetApiAutocompleteByCommandIdByArgIndexKey, ...(queryKey ?? [clientOptions])];

export type GetApiI18nByLocaleDefaultResponse = Awaited<ReturnType<typeof getApiI18nByLocale>>["data"];
export type GetApiI18nByLocaleQueryResult<TData = GetApiI18nByLocaleDefaultResponse, TError = unknown> = UseQueryResult<TData, TError>;

export const useGetApiI18nByLocaleKey = "GetApiI18nByLocale";
export const UseGetApiI18nByLocaleKeyFn = (clientOptions: Options<GetApiI18nByLocaleData, true>, queryKey?: Array<unknown>) => [useGetApiI18nByLocaleKey, ...(queryKey ?? [clientOptions])];

export type GetApiLayoutRegistryDefaultResponse = Awaited<ReturnType<typeof getApiLayoutRegistry>>["data"];
export type GetApiLayoutRegistryQueryResult<TData = GetApiLayoutRegistryDefaultResponse, TError = unknown> = UseQueryResult<TData, TError>;

export const useGetApiLayoutRegistryKey = "GetApiLayoutRegistry";
export const UseGetApiLayoutRegistryKeyFn = (clientOptions: Options<GetApiLayoutRegistryData, true> = {}, queryKey?: Array<unknown>) => [useGetApiLayoutRegistryKey, ...(queryKey ?? [clientOptions])];

export type GetApiItemsMetaDefaultResponse = Awaited<ReturnType<typeof getApiItemsMeta>>["data"];
export type GetApiItemsMetaQueryResult<TData = GetApiItemsMetaDefaultResponse, TError = unknown> = UseQueryResult<TData, TError>;

export const useGetApiItemsMetaKey = "GetApiItemsMeta";
export const UseGetApiItemsMetaKeyFn = (clientOptions: Options<GetApiItemsMetaData, true> = {}, queryKey?: Array<unknown>) => [useGetApiItemsMetaKey, ...(queryKey ?? [clientOptions])];

export type GetApiServerInfoDefaultResponse = Awaited<ReturnType<typeof getApiServerInfo>>["data"];
export type GetApiServerInfoQueryResult<TData = GetApiServerInfoDefaultResponse, TError = unknown> = UseQueryResult<TData, TError>;

export const useGetApiServerInfoKey = "GetApiServerInfo";
export const UseGetApiServerInfoKeyFn = (clientOptions: Options<GetApiServerInfoData, true> = {}, queryKey?: Array<unknown>) => [useGetApiServerInfoKey, ...(queryKey ?? [clientOptions])];

export type GetApiAttacksDefaultResponse = Awaited<ReturnType<typeof getApiAttacks>>["data"];
export type GetApiAttacksQueryResult<TData = GetApiAttacksDefaultResponse, TError = unknown> = UseQueryResult<TData, TError>;

export const useGetApiAttacksKey = "GetApiAttacks";
export const UseGetApiAttacksKeyFn = (clientOptions: Options<GetApiAttacksData, true> = {}, queryKey?: Array<unknown>) => [useGetApiAttacksKey, ...(queryKey ?? [clientOptions])];

export type GetApiClassesDefaultResponse = Awaited<ReturnType<typeof getApiClasses>>["data"];
export type GetApiClassesQueryResult<TData = GetApiClassesDefaultResponse, TError = unknown> = UseQueryResult<TData, TError>;

export const useGetApiClassesKey = "GetApiClasses";
export const UseGetApiClassesKeyFn = (clientOptions: Options<GetApiClassesData, true> = {}, queryKey?: Array<unknown>) => [useGetApiClassesKey, ...(queryKey ?? [clientOptions])];

export type GetApiSpellsDefaultResponse = Awaited<ReturnType<typeof getApiSpells>>["data"];
export type GetApiSpellsQueryResult<TData = GetApiSpellsDefaultResponse, TError = unknown> = UseQueryResult<TData, TError>;

export const useGetApiSpellsKey = "GetApiSpells";
export const UseGetApiSpellsKeyFn = (clientOptions: Options<GetApiSpellsData, true> = {}, queryKey?: Array<unknown>) => [useGetApiSpellsKey, ...(queryKey ?? [clientOptions])];

export type GetApiMacrosContextDefaultResponse = Awaited<ReturnType<typeof getApiMacrosContext>>["data"];
export type GetApiMacrosContextQueryResult<TData = GetApiMacrosContextDefaultResponse, TError = unknown> = UseQueryResult<TData, TError>;

export const useGetApiMacrosContextKey = "GetApiMacrosContext";
export const UseGetApiMacrosContextKeyFn = (clientOptions: Options<GetApiMacrosContextData, true> = {}, queryKey?: Array<unknown>) => [useGetApiMacrosContextKey, ...(queryKey ?? [clientOptions])];

export type GetApiBiomesDefaultResponse = Awaited<ReturnType<typeof getApiBiomes>>["data"];
export type GetApiBiomesQueryResult<TData = GetApiBiomesDefaultResponse, TError = unknown> = UseQueryResult<TData, TError>;

export const useGetApiBiomesKey = "GetApiBiomes";
export const UseGetApiBiomesKeyFn = (clientOptions: Options<GetApiBiomesData, true> = {}, queryKey?: Array<unknown>) => [useGetApiBiomesKey, ...(queryKey ?? [clientOptions])];

export type GetApiPlayerByIdSkinDefaultResponse = Awaited<ReturnType<typeof getApiPlayerByIdSkin>>["data"];
export type GetApiPlayerByIdSkinQueryResult<TData = GetApiPlayerByIdSkinDefaultResponse, TError = unknown> = UseQueryResult<TData, TError>;

export const useGetApiPlayerByIdSkinKey = "GetApiPlayerByIdSkin";
export const UseGetApiPlayerByIdSkinKeyFn = (clientOptions: Options<GetApiPlayerByIdSkinData, true>, queryKey?: Array<unknown>) => [useGetApiPlayerByIdSkinKey, ...(queryKey ?? [clientOptions])];

export type GetApiPlayerByIdArmorsDefaultResponse = Awaited<ReturnType<typeof getApiPlayerByIdArmors>>["data"];
export type GetApiPlayerByIdArmorsQueryResult<TData = GetApiPlayerByIdArmorsDefaultResponse, TError = unknown> = UseQueryResult<TData, TError>;

export const useGetApiPlayerByIdArmorsKey = "GetApiPlayerByIdArmors";
export const UseGetApiPlayerByIdArmorsKeyFn = (clientOptions: Options<GetApiPlayerByIdArmorsData, true>, queryKey?: Array<unknown>) => [useGetApiPlayerByIdArmorsKey, ...(queryKey ?? [clientOptions])];

export type GetApiPlayerByIdHandsDefaultResponse = Awaited<ReturnType<typeof getApiPlayerByIdHands>>["data"];
export type GetApiPlayerByIdHandsQueryResult<TData = GetApiPlayerByIdHandsDefaultResponse, TError = unknown> = UseQueryResult<TData, TError>;

export const useGetApiPlayerByIdHandsKey = "GetApiPlayerByIdHands";
export const UseGetApiPlayerByIdHandsKeyFn = (clientOptions: Options<GetApiPlayerByIdHandsData, true>, queryKey?: Array<unknown>) => [useGetApiPlayerByIdHandsKey, ...(queryKey ?? [clientOptions])];

export type GetApiPlayerByIdOwnedDefaultResponse = Awaited<ReturnType<typeof getApiPlayerByIdOwned>>["data"];
export type GetApiPlayerByIdOwnedQueryResult<TData = GetApiPlayerByIdOwnedDefaultResponse, TError = unknown> = UseQueryResult<TData, TError>;

export const useGetApiPlayerByIdOwnedKey = "GetApiPlayerByIdOwned";
export const UseGetApiPlayerByIdOwnedKeyFn = (clientOptions: Options<GetApiPlayerByIdOwnedData, true>, queryKey?: Array<unknown>) => [useGetApiPlayerByIdOwnedKey, ...(queryKey ?? [clientOptions])];

export type GetApiPlayerByIdRpgDefaultResponse = Awaited<ReturnType<typeof getApiPlayerByIdRpg>>["data"];
export type GetApiPlayerByIdRpgQueryResult<TData = GetApiPlayerByIdRpgDefaultResponse, TError = unknown> = UseQueryResult<TData, TError>;

export const useGetApiPlayerByIdRpgKey = "GetApiPlayerByIdRpg";
export const UseGetApiPlayerByIdRpgKeyFn = (clientOptions: Options<GetApiPlayerByIdRpgData, true>, queryKey?: Array<unknown>) => [useGetApiPlayerByIdRpgKey, ...(queryKey ?? [clientOptions])];

export type GetApiSkinsDefaultResponse = Awaited<ReturnType<typeof getApiSkins>>["data"];
export type GetApiSkinsQueryResult<TData = GetApiSkinsDefaultResponse, TError = unknown> = UseQueryResult<TData, TError>;

export const useGetApiSkinsKey = "GetApiSkins";
export const UseGetApiSkinsKeyFn = (clientOptions: Options<GetApiSkinsData, true> = {}, queryKey?: Array<unknown>) => [useGetApiSkinsKey, ...(queryKey ?? [clientOptions])];

export type GetApiSkinsByNameConfigDefaultResponse = Awaited<ReturnType<typeof getApiSkinsByNameConfig>>["data"];
export type GetApiSkinsByNameConfigQueryResult<TData = GetApiSkinsByNameConfigDefaultResponse, TError = unknown> = UseQueryResult<TData, TError>;

export const useGetApiSkinsByNameConfigKey = "GetApiSkinsByNameConfig";
export const UseGetApiSkinsByNameConfigKeyFn = (clientOptions: Options<GetApiSkinsByNameConfigData, true>, queryKey?: Array<unknown>) => [useGetApiSkinsByNameConfigKey, ...(queryKey ?? [clientOptions])];

export type GetApiVehiclesByNameConfigDefaultResponse = Awaited<ReturnType<typeof getApiVehiclesByNameConfig>>["data"];
export type GetApiVehiclesByNameConfigQueryResult<TData = GetApiVehiclesByNameConfigDefaultResponse, TError = unknown> = UseQueryResult<TData, TError>;

export const useGetApiVehiclesByNameConfigKey = "GetApiVehiclesByNameConfig";
export const UseGetApiVehiclesByNameConfigKeyFn = (clientOptions: Options<GetApiVehiclesByNameConfigData, true>, queryKey?: Array<unknown>) => [useGetApiVehiclesByNameConfigKey, ...(queryKey ?? [clientOptions])];

export type GetApiArmorsDefaultResponse = Awaited<ReturnType<typeof getApiArmors>>["data"];
export type GetApiArmorsQueryResult<TData = GetApiArmorsDefaultResponse, TError = unknown> = UseQueryResult<TData, TError>;

export const useGetApiArmorsKey = "GetApiArmors";
export const UseGetApiArmorsKeyFn = (clientOptions: Options<GetApiArmorsData, true> = {}, queryKey?: Array<unknown>) => [useGetApiArmorsKey, ...(queryKey ?? [clientOptions])];

export type GetApiWeaponsDefaultResponse = Awaited<ReturnType<typeof getApiWeapons>>["data"];
export type GetApiWeaponsQueryResult<TData = GetApiWeaponsDefaultResponse, TError = unknown> = UseQueryResult<TData, TError>;

export const useGetApiWeaponsKey = "GetApiWeapons";
export const UseGetApiWeaponsKeyFn = (clientOptions: Options<GetApiWeaponsData, true> = {}, queryKey?: Array<unknown>) => [useGetApiWeaponsKey, ...(queryKey ?? [clientOptions])];

export type GetApiToolsDefaultResponse = Awaited<ReturnType<typeof getApiTools>>["data"];
export type GetApiToolsQueryResult<TData = GetApiToolsDefaultResponse, TError = unknown> = UseQueryResult<TData, TError>;

export const useGetApiToolsKey = "GetApiTools";
export const UseGetApiToolsKeyFn = (clientOptions: Options<GetApiToolsData, true> = {}, queryKey?: Array<unknown>) => [useGetApiToolsKey, ...(queryKey ?? [clientOptions])];

export type GetApiSiegeWeaponsDefaultResponse = Awaited<ReturnType<typeof getApiSiegeWeapons>>["data"];
export type GetApiSiegeWeaponsQueryResult<TData = GetApiSiegeWeaponsDefaultResponse, TError = unknown> = UseQueryResult<TData, TError>;

export const useGetApiSiegeWeaponsKey = "GetApiSiegeWeapons";
export const UseGetApiSiegeWeaponsKeyFn = (clientOptions: Options<GetApiSiegeWeaponsData, true> = {}, queryKey?: Array<unknown>) => [useGetApiSiegeWeaponsKey, ...(queryKey ?? [clientOptions])];

export type GetApiGameAssetsDefaultResponse = Awaited<ReturnType<typeof getApiGameAssets>>["data"];
export type GetApiGameAssetsQueryResult<TData = GetApiGameAssetsDefaultResponse, TError = unknown> = UseQueryResult<TData, TError>;

export const useGetApiGameAssetsKey = "GetApiGameAssets";
export const UseGetApiGameAssetsKeyFn = (clientOptions: Options<GetApiGameAssetsData, true> = {}, queryKey?: Array<unknown>) => [useGetApiGameAssetsKey, ...(queryKey ?? [clientOptions])];

export type GetApiGameAssetsFileByDefaultResponse = Awaited<ReturnType<typeof getApiGameAssetsFileBy>>["data"];
export type GetApiGameAssetsFileByQueryResult<TData = GetApiGameAssetsFileByDefaultResponse, TError = unknown> = UseQueryResult<TData, TError>;

export const useGetApiGameAssetsFileByKey = "GetApiGameAssetsFileBy";
export const UseGetApiGameAssetsFileByKeyFn = (clientOptions: Options<GetApiGameAssetsFileByData, true>, queryKey?: Array<unknown>) => [useGetApiGameAssetsFileByKey, ...(queryKey ?? [clientOptions])];

export type GetApiGameAssetsBlendSceneByDefaultResponse = Awaited<ReturnType<typeof getApiGameAssetsBlendSceneBy>>["data"];
export type GetApiGameAssetsBlendSceneByQueryResult<TData = GetApiGameAssetsBlendSceneByDefaultResponse, TError = unknown> = UseQueryResult<TData, TError>;

export const useGetApiGameAssetsBlendSceneByKey = "GetApiGameAssetsBlendSceneBy";
export const UseGetApiGameAssetsBlendSceneByKeyFn = (clientOptions: Options<GetApiGameAssetsBlendSceneByData, true>, queryKey?: Array<unknown>) => [useGetApiGameAssetsBlendSceneByKey, ...(queryKey ?? [clientOptions])];

export type GetApiGameAssetsBlendPreviewByDefaultResponse = Awaited<ReturnType<typeof getApiGameAssetsBlendPreviewBy>>["data"];
export type GetApiGameAssetsBlendPreviewByQueryResult<TData = GetApiGameAssetsBlendPreviewByDefaultResponse, TError = unknown> = UseQueryResult<TData, TError>;

export const useGetApiGameAssetsBlendPreviewByKey = "GetApiGameAssetsBlendPreviewBy";
export const UseGetApiGameAssetsBlendPreviewByKeyFn = (clientOptions: Options<GetApiGameAssetsBlendPreviewByData, true>, queryKey?: Array<unknown>) => [useGetApiGameAssetsBlendPreviewByKey, ...(queryKey ?? [clientOptions])];

export type GetApiGameAssetsBbmodelExportByDefaultResponse = Awaited<ReturnType<typeof getApiGameAssetsBbmodelExportBy>>["data"];
export type GetApiGameAssetsBbmodelExportByQueryResult<TData = GetApiGameAssetsBbmodelExportByDefaultResponse, TError = unknown> = UseQueryResult<TData, TError>;

export const useGetApiGameAssetsBbmodelExportByKey = "GetApiGameAssetsBbmodelExportBy";
export const UseGetApiGameAssetsBbmodelExportByKeyFn = (clientOptions: Options<GetApiGameAssetsBbmodelExportByData, true>, queryKey?: Array<unknown>) => [useGetApiGameAssetsBbmodelExportByKey, ...(queryKey ?? [clientOptions])];

export type GetApiQuestsDefaultResponse = Awaited<ReturnType<typeof getApiQuests>>["data"];
export type GetApiQuestsQueryResult<TData = GetApiQuestsDefaultResponse, TError = unknown> = UseQueryResult<TData, TError>;

export const useGetApiQuestsKey = "GetApiQuests";
export const UseGetApiQuestsKeyFn = (clientOptions: Options<GetApiQuestsData, true> = {}, queryKey?: Array<unknown>) => [useGetApiQuestsKey, ...(queryKey ?? [clientOptions])];

export type GetApiPlayersNamesDefaultResponse = Awaited<ReturnType<typeof getApiPlayersNames>>["data"];
export type GetApiPlayersNamesQueryResult<TData = GetApiPlayersNamesDefaultResponse, TError = unknown> = UseQueryResult<TData, TError>;

export const useGetApiPlayersNamesKey = "GetApiPlayersNames";
export const UseGetApiPlayersNamesKeyFn = (clientOptions: Options<GetApiPlayersNamesData, true> = {}, queryKey?: Array<unknown>) => [useGetApiPlayersNamesKey, ...(queryKey ?? [clientOptions])];

export type GetApiMapStateDefaultResponse = Awaited<ReturnType<typeof getApiMapState>>["data"];
export type GetApiMapStateQueryResult<TData = GetApiMapStateDefaultResponse, TError = unknown> = UseQueryResult<TData, TError>;

export const useGetApiMapStateKey = "GetApiMapState";
export const UseGetApiMapStateKeyFn = (clientOptions: Options<GetApiMapStateData, true> = {}, queryKey?: Array<unknown>) => [useGetApiMapStateKey, ...(queryKey ?? [clientOptions])];

export type GetApiMapTerrainDefaultResponse = Awaited<ReturnType<typeof getApiMapTerrain>>["data"];
export type GetApiMapTerrainQueryResult<TData = GetApiMapTerrainDefaultResponse, TError = unknown> = UseQueryResult<TData, TError>;

export const useGetApiMapTerrainKey = "GetApiMapTerrain";
export const UseGetApiMapTerrainKeyFn = (clientOptions: Options<GetApiMapTerrainData, true> = {}, queryKey?: Array<unknown>) => [useGetApiMapTerrainKey, ...(queryKey ?? [clientOptions])];

export type GetApiMapVoronoiDefaultResponse = Awaited<ReturnType<typeof getApiMapVoronoi>>["data"];
export type GetApiMapVoronoiQueryResult<TData = GetApiMapVoronoiDefaultResponse, TError = unknown> = UseQueryResult<TData, TError>;

export const useGetApiMapVoronoiKey = "GetApiMapVoronoi";
export const UseGetApiMapVoronoiKeyFn = (clientOptions: Options<GetApiMapVoronoiData, true> = {}, queryKey?: Array<unknown>) => [useGetApiMapVoronoiKey, ...(queryKey ?? [clientOptions])];

export type GetApiMapHousesDefaultResponse = Awaited<ReturnType<typeof getApiMapHouses>>["data"];
export type GetApiMapHousesQueryResult<TData = GetApiMapHousesDefaultResponse, TError = unknown> = UseQueryResult<TData, TError>;

export const useGetApiMapHousesKey = "GetApiMapHouses";
export const UseGetApiMapHousesKeyFn = (clientOptions: Options<GetApiMapHousesData, true> = {}, queryKey?: Array<unknown>) => [useGetApiMapHousesKey, ...(queryKey ?? [clientOptions])];

export type GetApiMapStaircasesDefaultResponse = Awaited<ReturnType<typeof getApiMapStaircases>>["data"];
export type GetApiMapStaircasesQueryResult<TData = GetApiMapStaircasesDefaultResponse, TError = unknown> = UseQueryResult<TData, TError>;

export const useGetApiMapStaircasesKey = "GetApiMapStaircases";
export const UseGetApiMapStaircasesKeyFn = (clientOptions: Options<GetApiMapStaircasesData, true> = {}, queryKey?: Array<unknown>) => [useGetApiMapStaircasesKey, ...(queryKey ?? [clientOptions])];

export type GetApiMapRoadsDefaultResponse = Awaited<ReturnType<typeof getApiMapRoads>>["data"];
export type GetApiMapRoadsQueryResult<TData = GetApiMapRoadsDefaultResponse, TError = unknown> = UseQueryResult<TData, TError>;

export const useGetApiMapRoadsKey = "GetApiMapRoads";
export const UseGetApiMapRoadsKeyFn = (clientOptions: Options<GetApiMapRoadsData, true> = {}, queryKey?: Array<unknown>) => [useGetApiMapRoadsKey, ...(queryKey ?? [clientOptions])];

export type GetApiMapRoadRasterDefaultResponse = Awaited<ReturnType<typeof getApiMapRoadRaster>>["data"];
export type GetApiMapRoadRasterQueryResult<TData = GetApiMapRoadRasterDefaultResponse, TError = unknown> = UseQueryResult<TData, TError>;

export const useGetApiMapRoadRasterKey = "GetApiMapRoadRaster";
export const UseGetApiMapRoadRasterKeyFn = (clientOptions: Options<GetApiMapRoadRasterData, true> = {}, queryKey?: Array<unknown>) => [useGetApiMapRoadRasterKey, ...(queryKey ?? [clientOptions])];

export type GetApiMapRoadRasterPngDefaultResponse = Awaited<ReturnType<typeof getApiMapRoadRasterPng>>["data"];
export type GetApiMapRoadRasterPngQueryResult<TData = GetApiMapRoadRasterPngDefaultResponse, TError = unknown> = UseQueryResult<TData, TError>;

export const useGetApiMapRoadRasterPngKey = "GetApiMapRoadRasterPng";
export const UseGetApiMapRoadRasterPngKeyFn = (clientOptions: Options<GetApiMapRoadRasterPngData, true> = {}, queryKey?: Array<unknown>) => [useGetApiMapRoadRasterPngKey, ...(queryKey ?? [clientOptions])];

export type GetApiMapTerrainRasterPngDefaultResponse = Awaited<ReturnType<typeof getApiMapTerrainRasterPng>>["data"];
export type GetApiMapTerrainRasterPngQueryResult<TData = GetApiMapTerrainRasterPngDefaultResponse, TError = unknown> = UseQueryResult<TData, TError>;

export const useGetApiMapTerrainRasterPngKey = "GetApiMapTerrainRasterPng";
export const UseGetApiMapTerrainRasterPngKeyFn = (clientOptions: Options<GetApiMapTerrainRasterPngData, true> = {}, queryKey?: Array<unknown>) => [useGetApiMapTerrainRasterPngKey, ...(queryKey ?? [clientOptions])];

export type GetApiMapVoronoiBordersDefaultResponse = Awaited<ReturnType<typeof getApiMapVoronoiBorders>>["data"];
export type GetApiMapVoronoiBordersQueryResult<TData = GetApiMapVoronoiBordersDefaultResponse, TError = unknown> = UseQueryResult<TData, TError>;

export const useGetApiMapVoronoiBordersKey = "GetApiMapVoronoiBorders";
export const UseGetApiMapVoronoiBordersKeyFn = (clientOptions: Options<GetApiMapVoronoiBordersData, true> = {}, queryKey?: Array<unknown>) => [useGetApiMapVoronoiBordersKey, ...(queryKey ?? [clientOptions])];

export type GetApiAdminStatusDefaultResponse = Awaited<ReturnType<typeof getApiAdminStatus>>["data"];
export type GetApiAdminStatusQueryResult<TData = GetApiAdminStatusDefaultResponse, TError = unknown> = UseQueryResult<TData, TError>;

export const useGetApiAdminStatusKey = "GetApiAdminStatus";
export const UseGetApiAdminStatusKeyFn = (clientOptions: Options<GetApiAdminStatusData, true> = {}, queryKey?: Array<unknown>) => [useGetApiAdminStatusKey, ...(queryKey ?? [clientOptions])];

export type GetApiAdminUsersDefaultResponse = Awaited<ReturnType<typeof getApiAdminUsers>>["data"];
export type GetApiAdminUsersQueryResult<TData = GetApiAdminUsersDefaultResponse, TError = unknown> = UseQueryResult<TData, TError>;

export const useGetApiAdminUsersKey = "GetApiAdminUsers";
export const UseGetApiAdminUsersKeyFn = (clientOptions: Options<GetApiAdminUsersData, true> = {}, queryKey?: Array<unknown>) => [useGetApiAdminUsersKey, ...(queryKey ?? [clientOptions])];

export type GetApiAdminPlayersDefaultResponse = Awaited<ReturnType<typeof getApiAdminPlayers>>["data"];
export type GetApiAdminPlayersQueryResult<TData = GetApiAdminPlayersDefaultResponse, TError = unknown> = UseQueryResult<TData, TError>;

export const useGetApiAdminPlayersKey = "GetApiAdminPlayers";
export const UseGetApiAdminPlayersKeyFn = (clientOptions: Options<GetApiAdminPlayersData, true> = {}, queryKey?: Array<unknown>) => [useGetApiAdminPlayersKey, ...(queryKey ?? [clientOptions])];

export type GetApiAdminPlayersByNameDefaultResponse = Awaited<ReturnType<typeof getApiAdminPlayersByName>>["data"];
export type GetApiAdminPlayersByNameQueryResult<TData = GetApiAdminPlayersByNameDefaultResponse, TError = unknown> = UseQueryResult<TData, TError>;

export const useGetApiAdminPlayersByNameKey = "GetApiAdminPlayersByName";
export const UseGetApiAdminPlayersByNameKeyFn = (clientOptions: Options<GetApiAdminPlayersByNameData, true>, queryKey?: Array<unknown>) => [useGetApiAdminPlayersByNameKey, ...(queryKey ?? [clientOptions])];

export type GetApiAdminWorldsDefaultResponse = Awaited<ReturnType<typeof getApiAdminWorlds>>["data"];
export type GetApiAdminWorldsQueryResult<TData = GetApiAdminWorldsDefaultResponse, TError = unknown> = UseQueryResult<TData, TError>;

export const useGetApiAdminWorldsKey = "GetApiAdminWorlds";
export const UseGetApiAdminWorldsKeyFn = (clientOptions: Options<GetApiAdminWorldsData, true> = {}, queryKey?: Array<unknown>) => [useGetApiAdminWorldsKey, ...(queryKey ?? [clientOptions])];

export type GetApiAdminConfigsDefaultResponse = Awaited<ReturnType<typeof getApiAdminConfigs>>["data"];
export type GetApiAdminConfigsQueryResult<TData = GetApiAdminConfigsDefaultResponse, TError = unknown> = UseQueryResult<TData, TError>;

export const useGetApiAdminConfigsKey = "GetApiAdminConfigs";
export const UseGetApiAdminConfigsKeyFn = (clientOptions: Options<GetApiAdminConfigsData, true> = {}, queryKey?: Array<unknown>) => [useGetApiAdminConfigsKey, ...(queryKey ?? [clientOptions])];

export type GetApiAdminConfigsByDefaultResponse = Awaited<ReturnType<typeof getApiAdminConfigsBy>>["data"];
export type GetApiAdminConfigsByQueryResult<TData = GetApiAdminConfigsByDefaultResponse, TError = unknown> = UseQueryResult<TData, TError>;

export const useGetApiAdminConfigsByKey = "GetApiAdminConfigsBy";
export const UseGetApiAdminConfigsByKeyFn = (clientOptions: Options<GetApiAdminConfigsByData, true>, queryKey?: Array<unknown>) => [useGetApiAdminConfigsByKey, ...(queryKey ?? [clientOptions])];

export type GetApiAdminClassesDefaultResponse = Awaited<ReturnType<typeof getApiAdminClasses>>["data"];
export type GetApiAdminClassesQueryResult<TData = GetApiAdminClassesDefaultResponse, TError = unknown> = UseQueryResult<TData, TError>;

export const useGetApiAdminClassesKey = "GetApiAdminClasses";
export const UseGetApiAdminClassesKeyFn = (clientOptions: Options<GetApiAdminClassesData, true> = {}, queryKey?: Array<unknown>) => [useGetApiAdminClassesKey, ...(queryKey ?? [clientOptions])];

export type GetApiAdminSkillsDefaultResponse = Awaited<ReturnType<typeof getApiAdminSkills>>["data"];
export type GetApiAdminSkillsQueryResult<TData = GetApiAdminSkillsDefaultResponse, TError = unknown> = UseQueryResult<TData, TError>;

export const useGetApiAdminSkillsKey = "GetApiAdminSkills";
export const UseGetApiAdminSkillsKeyFn = (clientOptions: Options<GetApiAdminSkillsData, true> = {}, queryKey?: Array<unknown>) => [useGetApiAdminSkillsKey, ...(queryKey ?? [clientOptions])];

export type GetApiAdminNpcsDefaultResponse = Awaited<ReturnType<typeof getApiAdminNpcs>>["data"];
export type GetApiAdminNpcsQueryResult<TData = GetApiAdminNpcsDefaultResponse, TError = unknown> = UseQueryResult<TData, TError>;

export const useGetApiAdminNpcsKey = "GetApiAdminNpcs";
export const UseGetApiAdminNpcsKeyFn = (clientOptions: Options<GetApiAdminNpcsData, true> = {}, queryKey?: Array<unknown>) => [useGetApiAdminNpcsKey, ...(queryKey ?? [clientOptions])];

export type GetApiAdminBlocksDefaultResponse = Awaited<ReturnType<typeof getApiAdminBlocks>>["data"];
export type GetApiAdminBlocksQueryResult<TData = GetApiAdminBlocksDefaultResponse, TError = unknown> = UseQueryResult<TData, TError>;

export const useGetApiAdminBlocksKey = "GetApiAdminBlocks";
export const UseGetApiAdminBlocksKeyFn = (clientOptions: Options<GetApiAdminBlocksData, true> = {}, queryKey?: Array<unknown>) => [useGetApiAdminBlocksKey, ...(queryKey ?? [clientOptions])];

export type GetApiAdminPlainColorsDefaultResponse = Awaited<ReturnType<typeof getApiAdminPlainColors>>["data"];
export type GetApiAdminPlainColorsQueryResult<TData = GetApiAdminPlainColorsDefaultResponse, TError = unknown> = UseQueryResult<TData, TError>;

export const useGetApiAdminPlainColorsKey = "GetApiAdminPlainColors";
export const UseGetApiAdminPlainColorsKeyFn = (clientOptions: Options<GetApiAdminPlainColorsData, true> = {}, queryKey?: Array<unknown>) => [useGetApiAdminPlainColorsKey, ...(queryKey ?? [clientOptions])];

export type GetApiAdminChunksDiscoveredDefaultResponse = Awaited<ReturnType<typeof getApiAdminChunksDiscovered>>["data"];
export type GetApiAdminChunksDiscoveredQueryResult<TData = GetApiAdminChunksDiscoveredDefaultResponse, TError = unknown> = UseQueryResult<TData, TError>;

export const useGetApiAdminChunksDiscoveredKey = "GetApiAdminChunksDiscovered";
export const UseGetApiAdminChunksDiscoveredKeyFn = (clientOptions: Options<GetApiAdminChunksDiscoveredData, true> = {}, queryKey?: Array<unknown>) => [useGetApiAdminChunksDiscoveredKey, ...(queryKey ?? [clientOptions])];

export type GetApiAdminInstancesDefaultResponse = Awaited<ReturnType<typeof getApiAdminInstances>>["data"];
export type GetApiAdminInstancesQueryResult<TData = GetApiAdminInstancesDefaultResponse, TError = unknown> = UseQueryResult<TData, TError>;

export const useGetApiAdminInstancesKey = "GetApiAdminInstances";
export const UseGetApiAdminInstancesKeyFn = (clientOptions: Options<GetApiAdminInstancesData, true> = {}, queryKey?: Array<unknown>) => [useGetApiAdminInstancesKey, ...(queryKey ?? [clientOptions])];

export type GetApiAdminInstancesByIdDefaultResponse = Awaited<ReturnType<typeof getApiAdminInstancesById>>["data"];
export type GetApiAdminInstancesByIdQueryResult<TData = GetApiAdminInstancesByIdDefaultResponse, TError = unknown> = UseQueryResult<TData, TError>;

export const useGetApiAdminInstancesByIdKey = "GetApiAdminInstancesById";
export const UseGetApiAdminInstancesByIdKeyFn = (clientOptions: Options<GetApiAdminInstancesByIdData, true>, queryKey?: Array<unknown>) => [useGetApiAdminInstancesByIdKey, ...(queryKey ?? [clientOptions])];

export type GetApiAdminInstancesByIdBlocksDefaultResponse = Awaited<ReturnType<typeof getApiAdminInstancesByIdBlocks>>["data"];
export type GetApiAdminInstancesByIdBlocksQueryResult<TData = GetApiAdminInstancesByIdBlocksDefaultResponse, TError = unknown> = UseQueryResult<TData, TError>;

export const useGetApiAdminInstancesByIdBlocksKey = "GetApiAdminInstancesByIdBlocks";
export const UseGetApiAdminInstancesByIdBlocksKeyFn = (clientOptions: Options<GetApiAdminInstancesByIdBlocksData, true>, queryKey?: Array<unknown>) => [useGetApiAdminInstancesByIdBlocksKey, ...(queryKey ?? [clientOptions])];

export type GetApiAdminScenesDefaultResponse = Awaited<ReturnType<typeof getApiAdminScenes>>["data"];
export type GetApiAdminScenesQueryResult<TData = GetApiAdminScenesDefaultResponse, TError = unknown> = UseQueryResult<TData, TError>;

export const useGetApiAdminScenesKey = "GetApiAdminScenes";
export const UseGetApiAdminScenesKeyFn = (clientOptions: Options<GetApiAdminScenesData, true> = {}, queryKey?: Array<unknown>) => [useGetApiAdminScenesKey, ...(queryKey ?? [clientOptions])];

export type GetApiAdminScenesByIdDefaultResponse = Awaited<ReturnType<typeof getApiAdminScenesById>>["data"];
export type GetApiAdminScenesByIdQueryResult<TData = GetApiAdminScenesByIdDefaultResponse, TError = unknown> = UseQueryResult<TData, TError>;

export const useGetApiAdminScenesByIdKey = "GetApiAdminScenesById";
export const UseGetApiAdminScenesByIdKeyFn = (clientOptions: Options<GetApiAdminScenesByIdData, true>, queryKey?: Array<unknown>) => [useGetApiAdminScenesByIdKey, ...(queryKey ?? [clientOptions])];

export type GetApiAdminScenesByIdBlocksRawDefaultResponse = Awaited<ReturnType<typeof getApiAdminScenesByIdBlocksRaw>>["data"];
export type GetApiAdminScenesByIdBlocksRawQueryResult<TData = GetApiAdminScenesByIdBlocksRawDefaultResponse, TError = unknown> = UseQueryResult<TData, TError>;

export const useGetApiAdminScenesByIdBlocksRawKey = "GetApiAdminScenesByIdBlocksRaw";
export const UseGetApiAdminScenesByIdBlocksRawKeyFn = (clientOptions: Options<GetApiAdminScenesByIdBlocksRawData, true>, queryKey?: Array<unknown>) => [useGetApiAdminScenesByIdBlocksRawKey, ...(queryKey ?? [clientOptions])];

export type GetApiAdminScenesByIdEntitiesDefaultResponse = Awaited<ReturnType<typeof getApiAdminScenesByIdEntities>>["data"];
export type GetApiAdminScenesByIdEntitiesQueryResult<TData = GetApiAdminScenesByIdEntitiesDefaultResponse, TError = unknown> = UseQueryResult<TData, TError>;

export const useGetApiAdminScenesByIdEntitiesKey = "GetApiAdminScenesByIdEntities";
export const UseGetApiAdminScenesByIdEntitiesKeyFn = (clientOptions: Options<GetApiAdminScenesByIdEntitiesData, true>, queryKey?: Array<unknown>) => [useGetApiAdminScenesByIdEntitiesKey, ...(queryKey ?? [clientOptions])];

export type GetApiAdminNpcTypesDefaultResponse = Awaited<ReturnType<typeof getApiAdminNpcTypes>>["data"];
export type GetApiAdminNpcTypesQueryResult<TData = GetApiAdminNpcTypesDefaultResponse, TError = unknown> = UseQueryResult<TData, TError>;

export const useGetApiAdminNpcTypesKey = "GetApiAdminNpcTypes";
export const UseGetApiAdminNpcTypesKeyFn = (clientOptions: Options<GetApiAdminNpcTypesData, true> = {}, queryKey?: Array<unknown>) => [useGetApiAdminNpcTypesKey, ...(queryKey ?? [clientOptions])];

export type GetApiAdminItemsDefaultResponse = Awaited<ReturnType<typeof getApiAdminItems>>["data"];
export type GetApiAdminItemsQueryResult<TData = GetApiAdminItemsDefaultResponse, TError = unknown> = UseQueryResult<TData, TError>;

export const useGetApiAdminItemsKey = "GetApiAdminItems";
export const UseGetApiAdminItemsKeyFn = (clientOptions: Options<GetApiAdminItemsData, true> = {}, queryKey?: Array<unknown>) => [useGetApiAdminItemsKey, ...(queryKey ?? [clientOptions])];

export type GetApiAdminSchemasByFilenameDefaultResponse = Awaited<ReturnType<typeof getApiAdminSchemasByFilename>>["data"];
export type GetApiAdminSchemasByFilenameQueryResult<TData = GetApiAdminSchemasByFilenameDefaultResponse, TError = unknown> = UseQueryResult<TData, TError>;

export const useGetApiAdminSchemasByFilenameKey = "GetApiAdminSchemasByFilename";
export const UseGetApiAdminSchemasByFilenameKeyFn = (clientOptions: Options<GetApiAdminSchemasByFilenameData, true>, queryKey?: Array<unknown>) => [useGetApiAdminSchemasByFilenameKey, ...(queryKey ?? [clientOptions])];

export type GetApiAdminSimulationDefaultsDefaultResponse = Awaited<ReturnType<typeof getApiAdminSimulationDefaults>>["data"];
export type GetApiAdminSimulationDefaultsQueryResult<TData = GetApiAdminSimulationDefaultsDefaultResponse, TError = unknown> = UseQueryResult<TData, TError>;

export const useGetApiAdminSimulationDefaultsKey = "GetApiAdminSimulationDefaults";
export const UseGetApiAdminSimulationDefaultsKeyFn = (clientOptions: Options<GetApiAdminSimulationDefaultsData, true> = {}, queryKey?: Array<unknown>) => [useGetApiAdminSimulationDefaultsKey, ...(queryKey ?? [clientOptions])];

export type GetApiChunksByCxByCzDefaultResponse = Awaited<ReturnType<typeof getApiChunksByCxByCz>>["data"];
export type GetApiChunksByCxByCzQueryResult<TData = GetApiChunksByCxByCzDefaultResponse, TError = unknown> = UseQueryResult<TData, TError>;

export const useGetApiChunksByCxByCzKey = "GetApiChunksByCxByCz";
export const UseGetApiChunksByCxByCzKeyFn = (clientOptions: Options<GetApiChunksByCxByCzData, true>, queryKey?: Array<unknown>) => [useGetApiChunksByCxByCzKey, ...(queryKey ?? [clientOptions])];

export type GetApiPlayersByEmailByEmailDefaultResponse = Awaited<ReturnType<typeof getApiPlayersByEmailByEmail>>["data"];
export type GetApiPlayersByEmailByEmailQueryResult<TData = GetApiPlayersByEmailByEmailDefaultResponse, TError = unknown> = UseQueryResult<TData, TError>;

export const useGetApiPlayersByEmailByEmailKey = "GetApiPlayersByEmailByEmail";
export const UseGetApiPlayersByEmailByEmailKeyFn = (clientOptions: Options<GetApiPlayersByEmailByEmailData, true>, queryKey?: Array<unknown>) => [useGetApiPlayersByEmailByEmailKey, ...(queryKey ?? [clientOptions])];

export type PostAuthNoauthLoginMutationResult = Awaited<ReturnType<typeof postAuthNoauthLogin>>;

export const usePostAuthNoauthLoginKey = "PostAuthNoauthLogin";
export const UsePostAuthNoauthLoginKeyFn = (mutationKey?: Array<unknown>) => [usePostAuthNoauthLoginKey, ...(mutationKey ?? [])];

export type PostApiAssetsReloadMutationResult = Awaited<ReturnType<typeof postApiAssetsReload>>;

export const usePostApiAssetsReloadKey = "PostApiAssetsReload";
export const UsePostApiAssetsReloadKeyFn = (mutationKey?: Array<unknown>) => [usePostApiAssetsReloadKey, ...(mutationKey ?? [])];

export type PutApiPlayerByIdSkinMutationResult = Awaited<ReturnType<typeof putApiPlayerByIdSkin>>;

export const usePutApiPlayerByIdSkinKey = "PutApiPlayerByIdSkin";
export const UsePutApiPlayerByIdSkinKeyFn = (mutationKey?: Array<unknown>) => [usePutApiPlayerByIdSkinKey, ...(mutationKey ?? [])];

export type PostApiPlayerByIdScreenshotsMutationResult = Awaited<ReturnType<typeof postApiPlayerByIdScreenshots>>;

export const usePostApiPlayerByIdScreenshotsKey = "PostApiPlayerByIdScreenshots";
export const UsePostApiPlayerByIdScreenshotsKeyFn = (mutationKey?: Array<unknown>) => [usePostApiPlayerByIdScreenshotsKey, ...(mutationKey ?? [])];

export type PostApiCharacterCreateMutationResult = Awaited<ReturnType<typeof postApiCharacterCreate>>;

export const usePostApiCharacterCreateKey = "PostApiCharacterCreate";
export const UsePostApiCharacterCreateKeyFn = (mutationKey?: Array<unknown>) => [usePostApiCharacterCreateKey, ...(mutationKey ?? [])];

export type PostApiCharacterRpgcreateMutationResult = Awaited<ReturnType<typeof postApiCharacterRpgcreate>>;

export const usePostApiCharacterRpgcreateKey = "PostApiCharacterRpgcreate";
export const UsePostApiCharacterRpgcreateKeyFn = (mutationKey?: Array<unknown>) => [usePostApiCharacterRpgcreateKey, ...(mutationKey ?? [])];

export type DeleteApiGameAssetsBlendCacheByMutationResult = Awaited<ReturnType<typeof deleteApiGameAssetsBlendCacheBy>>;

export const useDeleteApiGameAssetsBlendCacheByKey = "DeleteApiGameAssetsBlendCacheBy";
export const UseDeleteApiGameAssetsBlendCacheByKeyFn = (mutationKey?: Array<unknown>) => [useDeleteApiGameAssetsBlendCacheByKey, ...(mutationKey ?? [])];

export type PostApiAdminRestartMutationResult = Awaited<ReturnType<typeof postApiAdminRestart>>;

export const usePostApiAdminRestartKey = "PostApiAdminRestart";
export const UsePostApiAdminRestartKeyFn = (mutationKey?: Array<unknown>) => [usePostApiAdminRestartKey, ...(mutationKey ?? [])];

export type PostApiAdminReloadMutationResult = Awaited<ReturnType<typeof postApiAdminReload>>;

export const usePostApiAdminReloadKey = "PostApiAdminReload";
export const UsePostApiAdminReloadKeyFn = (mutationKey?: Array<unknown>) => [usePostApiAdminReloadKey, ...(mutationKey ?? [])];

export type PutApiAdminGametimeMutationResult = Awaited<ReturnType<typeof putApiAdminGametime>>;

export const usePutApiAdminGametimeKey = "PutApiAdminGametime";
export const UsePutApiAdminGametimeKeyFn = (mutationKey?: Array<unknown>) => [usePutApiAdminGametimeKey, ...(mutationKey ?? [])];

export type PostApiAdminUsersMutationResult = Awaited<ReturnType<typeof postApiAdminUsers>>;

export const usePostApiAdminUsersKey = "PostApiAdminUsers";
export const UsePostApiAdminUsersKeyFn = (mutationKey?: Array<unknown>) => [usePostApiAdminUsersKey, ...(mutationKey ?? [])];

export type DeleteApiAdminUsersByEmailMutationResult = Awaited<ReturnType<typeof deleteApiAdminUsersByEmail>>;

export const useDeleteApiAdminUsersByEmailKey = "DeleteApiAdminUsersByEmail";
export const UseDeleteApiAdminUsersByEmailKeyFn = (mutationKey?: Array<unknown>) => [useDeleteApiAdminUsersByEmailKey, ...(mutationKey ?? [])];

export type PutApiAdminUsersByEmailMutationResult = Awaited<ReturnType<typeof putApiAdminUsersByEmail>>;

export const usePutApiAdminUsersByEmailKey = "PutApiAdminUsersByEmail";
export const UsePutApiAdminUsersByEmailKeyFn = (mutationKey?: Array<unknown>) => [usePutApiAdminUsersByEmailKey, ...(mutationKey ?? [])];

export type PutApiAdminPlayersByNameKeybindingsMutationResult = Awaited<ReturnType<typeof putApiAdminPlayersByNameKeybindings>>;

export const usePutApiAdminPlayersByNameKeybindingsKey = "PutApiAdminPlayersByNameKeybindings";
export const UsePutApiAdminPlayersByNameKeybindingsKeyFn = (mutationKey?: Array<unknown>) => [usePutApiAdminPlayersByNameKeybindingsKey, ...(mutationKey ?? [])];

export type PutApiAdminPlayersByNamePreferencesMutationResult = Awaited<ReturnType<typeof putApiAdminPlayersByNamePreferences>>;

export const usePutApiAdminPlayersByNamePreferencesKey = "PutApiAdminPlayersByNamePreferences";
export const UsePutApiAdminPlayersByNamePreferencesKeyFn = (mutationKey?: Array<unknown>) => [usePutApiAdminPlayersByNamePreferencesKey, ...(mutationKey ?? [])];

export type PostApiAdminPlayersByNameRenameMutationResult = Awaited<ReturnType<typeof postApiAdminPlayersByNameRename>>;

export const usePostApiAdminPlayersByNameRenameKey = "PostApiAdminPlayersByNameRename";
export const UsePostApiAdminPlayersByNameRenameKeyFn = (mutationKey?: Array<unknown>) => [usePostApiAdminPlayersByNameRenameKey, ...(mutationKey ?? [])];

export type PutApiAdminPlayersByNameRpgMutationResult = Awaited<ReturnType<typeof putApiAdminPlayersByNameRpg>>;

export const usePutApiAdminPlayersByNameRpgKey = "PutApiAdminPlayersByNameRpg";
export const UsePutApiAdminPlayersByNameRpgKeyFn = (mutationKey?: Array<unknown>) => [usePutApiAdminPlayersByNameRpgKey, ...(mutationKey ?? [])];

export type PutApiAdminPlayersByNameEquipmentMutationResult = Awaited<ReturnType<typeof putApiAdminPlayersByNameEquipment>>;

export const usePutApiAdminPlayersByNameEquipmentKey = "PutApiAdminPlayersByNameEquipment";
export const UsePutApiAdminPlayersByNameEquipmentKeyFn = (mutationKey?: Array<unknown>) => [usePutApiAdminPlayersByNameEquipmentKey, ...(mutationKey ?? [])];

export type PostApiAdminPlayersByNameGiveMutationResult = Awaited<ReturnType<typeof postApiAdminPlayersByNameGive>>;

export const usePostApiAdminPlayersByNameGiveKey = "PostApiAdminPlayersByNameGive";
export const UsePostApiAdminPlayersByNameGiveKeyFn = (mutationKey?: Array<unknown>) => [usePostApiAdminPlayersByNameGiveKey, ...(mutationKey ?? [])];

export type PostApiAdminWorldsMutationResult = Awaited<ReturnType<typeof postApiAdminWorlds>>;

export const usePostApiAdminWorldsKey = "PostApiAdminWorlds";
export const UsePostApiAdminWorldsKeyFn = (mutationKey?: Array<unknown>) => [usePostApiAdminWorldsKey, ...(mutationKey ?? [])];

export type PutApiAdminConfigsByMutationResult = Awaited<ReturnType<typeof putApiAdminConfigsBy>>;

export const usePutApiAdminConfigsByKey = "PutApiAdminConfigsBy";
export const UsePutApiAdminConfigsByKeyFn = (mutationKey?: Array<unknown>) => [usePutApiAdminConfigsByKey, ...(mutationKey ?? [])];

export type PostApiAdminInstancesMutationResult = Awaited<ReturnType<typeof postApiAdminInstances>>;

export const usePostApiAdminInstancesKey = "PostApiAdminInstances";
export const UsePostApiAdminInstancesKeyFn = (mutationKey?: Array<unknown>) => [usePostApiAdminInstancesKey, ...(mutationKey ?? [])];

export type DeleteApiAdminInstancesByIdMutationResult = Awaited<ReturnType<typeof deleteApiAdminInstancesById>>;

export const useDeleteApiAdminInstancesByIdKey = "DeleteApiAdminInstancesById";
export const UseDeleteApiAdminInstancesByIdKeyFn = (mutationKey?: Array<unknown>) => [useDeleteApiAdminInstancesByIdKey, ...(mutationKey ?? [])];

export type PutApiAdminInstancesByIdMutationResult = Awaited<ReturnType<typeof putApiAdminInstancesById>>;

export const usePutApiAdminInstancesByIdKey = "PutApiAdminInstancesById";
export const UsePutApiAdminInstancesByIdKeyFn = (mutationKey?: Array<unknown>) => [usePutApiAdminInstancesByIdKey, ...(mutationKey ?? [])];

export type PutApiAdminInstancesByIdBoundsMutationResult = Awaited<ReturnType<typeof putApiAdminInstancesByIdBounds>>;

export const usePutApiAdminInstancesByIdBoundsKey = "PutApiAdminInstancesByIdBounds";
export const UsePutApiAdminInstancesByIdBoundsKeyFn = (mutationKey?: Array<unknown>) => [usePutApiAdminInstancesByIdBoundsKey, ...(mutationKey ?? [])];

export type PutApiAdminInstancesByIdEnabledMutationResult = Awaited<ReturnType<typeof putApiAdminInstancesByIdEnabled>>;

export const usePutApiAdminInstancesByIdEnabledKey = "PutApiAdminInstancesByIdEnabled";
export const UsePutApiAdminInstancesByIdEnabledKeyFn = (mutationKey?: Array<unknown>) => [usePutApiAdminInstancesByIdEnabledKey, ...(mutationKey ?? [])];

export type PutApiAdminInstancesByIdChunksMutationResult = Awaited<ReturnType<typeof putApiAdminInstancesByIdChunks>>;

export const usePutApiAdminInstancesByIdChunksKey = "PutApiAdminInstancesByIdChunks";
export const UsePutApiAdminInstancesByIdChunksKeyFn = (mutationKey?: Array<unknown>) => [usePutApiAdminInstancesByIdChunksKey, ...(mutationKey ?? [])];

export type PutApiAdminInstancesByIdLayoutMutationResult = Awaited<ReturnType<typeof putApiAdminInstancesByIdLayout>>;

export const usePutApiAdminInstancesByIdLayoutKey = "PutApiAdminInstancesByIdLayout";
export const UsePutApiAdminInstancesByIdLayoutKeyFn = (mutationKey?: Array<unknown>) => [usePutApiAdminInstancesByIdLayoutKey, ...(mutationKey ?? [])];

export type PostApiAdminScenesMutationResult = Awaited<ReturnType<typeof postApiAdminScenes>>;

export const usePostApiAdminScenesKey = "PostApiAdminScenes";
export const UsePostApiAdminScenesKeyFn = (mutationKey?: Array<unknown>) => [usePostApiAdminScenesKey, ...(mutationKey ?? [])];

export type DeleteApiAdminScenesByIdMutationResult = Awaited<ReturnType<typeof deleteApiAdminScenesById>>;

export const useDeleteApiAdminScenesByIdKey = "DeleteApiAdminScenesById";
export const UseDeleteApiAdminScenesByIdKeyFn = (mutationKey?: Array<unknown>) => [useDeleteApiAdminScenesByIdKey, ...(mutationKey ?? [])];

export type PutApiAdminScenesByIdMutationResult = Awaited<ReturnType<typeof putApiAdminScenesById>>;

export const usePutApiAdminScenesByIdKey = "PutApiAdminScenesById";
export const UsePutApiAdminScenesByIdKeyFn = (mutationKey?: Array<unknown>) => [usePutApiAdminScenesByIdKey, ...(mutationKey ?? [])];

export type PostApiAdminScenesByIdDuplicateMutationResult = Awaited<ReturnType<typeof postApiAdminScenesByIdDuplicate>>;

export const usePostApiAdminScenesByIdDuplicateKey = "PostApiAdminScenesByIdDuplicate";
export const UsePostApiAdminScenesByIdDuplicateKeyFn = (mutationKey?: Array<unknown>) => [usePostApiAdminScenesByIdDuplicateKey, ...(mutationKey ?? [])];

export type PutApiAdminScenesByIdDimensionsMutationResult = Awaited<ReturnType<typeof putApiAdminScenesByIdDimensions>>;

export const usePutApiAdminScenesByIdDimensionsKey = "PutApiAdminScenesByIdDimensions";
export const UsePutApiAdminScenesByIdDimensionsKeyFn = (mutationKey?: Array<unknown>) => [usePutApiAdminScenesByIdDimensionsKey, ...(mutationKey ?? [])];

export type PutApiAdminScenesByIdLayoutMutationResult = Awaited<ReturnType<typeof putApiAdminScenesByIdLayout>>;

export const usePutApiAdminScenesByIdLayoutKey = "PutApiAdminScenesByIdLayout";
export const UsePutApiAdminScenesByIdLayoutKeyFn = (mutationKey?: Array<unknown>) => [usePutApiAdminScenesByIdLayoutKey, ...(mutationKey ?? [])];
