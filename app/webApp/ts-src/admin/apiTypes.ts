// Re-exports generated OpenAPI types under the names admin/ pages used before the TanStack
// Query migration, so most call sites only need an import-path change.
export type {
  OrgMicoliMicraftProtocolBlockInfo as BlockInfoDto,
  OrgMicoliMicraftProtocolPlainColorInfo as PlainColorDto,
  OrgMicoliMicraftGameWorldChunkPos as ChunkPosDto,
  OrgMicoliMicraftGameWorldInstanceInstanceZone as InstanceZoneDto,
  OrgMicoliMicraftHttpInstanceBlockDto as InstanceBlockDto,
  OrgMicoliMicraftHttpSceneDto as SceneDto,
  OrgMicoliMicraftProtocolNpcCodexInfo as NpcTypeDto,
  OrgMicoliMicraftProtocolItemInfo as ItemDto,
  OrgMicoliMicraftHttpStatusSnapshot as StatusSnapshot,
  OrgMicoliMicraftGameClassesClassAttackAccess as ClassAttackAccess,
  OrgMicoliMicraftGameClassesClassLevelEntry as ClassLevelEntry,
  OrgMicoliMicraftGameClassesClassDefinitionEntry as ClassDefinitionEntry,
  OrgMicoliMicraftHttpNpcAdminDto as NpcAdminDto,
  OrgMicoliMicraftHttpWorldStatsDto as WorldStatsDto,
  OrgMicoliMicraftHttpUserDto as UserDto,
  OrgMicoliMicraftPlayerRpgBaseStats as BaseStats,
  OrgMicoliMicraftPlayerRpgCharacterData as CharacterData,
  OrgMicoliMicraftPlayerPlayerState as PlayerState,
  OrgMicoliMicraftGameWorldPlayerFile as PlayerFile,
  OrgMicoliMicraftProtocolBlockEntityProto as BlockEntityProtoDto,
} from "../generated/api/requests/types.gen";

// /api/map/terrain is documented as an opaque JSON string in the OpenAPI spec (it returns a
// pre-serialized cache), so it has no generated schema — kept as a local shape.
export interface ChunkTerrainInfoDto {
  cx: number;
  cz: number;
  colors: (string | null)[];
  avgHeight: number | null;
}

// Scene blocks are only ever read/written over the collaborative-edit websocket, never an HTTP
// body, so this has no OpenAPI schema either.
export interface SceneBlockDto {
  x: number;
  y: number;
  z: number;
  type: string;
  state: number;
  xOffset: number;
  zOffset: number;
}
