export interface PlayerMapInfo {
  id: string;
  name: string;
  x: number;
  y: number;
  z: number;
  yaw: number;
}

export interface NpcMapInfo {
  id: string;
  name: string;
  type: string;
  x: number;
  y: number;
  z: number;
  yaw: number;
}

export interface WeatherZoneInfo {
  cx: number;
  cz: number;
  radius: number;
  type: string;
}

export interface MapApiState {
  gameTicks: number;
  players: PlayerMapInfo[];
  npcs: NpcMapInfo[];
  weatherZones: WeatherZoneInfo[];
}

export interface ChunkTerrainInfo {
  cx: number;
  cz: number;
  colors: (string | null)[];
  avgHeight?: number;
}

export interface VoronoiCellInfo {
  x: number;
  z: number;
  biome: string;
  color: string;
  name: string;
}

export interface StaircaseMapInfo {
  name: string;
  x: number;
  z: number;
}

export interface HouseMapInfo {
  x: number;
  z: number;
  type: string;
  width: number;
  depth: number;
}

export interface Camera {
  x: number;
  z: number;
  pxPerBlock: number;
}

export const LAYER_KEYS = [
  "voronoi",
  "contours",
  "vegetation",
  "houses",
  "players",
  "npcs",
  "precise-roads",
  "chunks",
  "weather",
  "staircases",
] as const;

export type LayerKey = (typeof LAYER_KEYS)[number];
export type Layers = Record<LayerKey, boolean>;
export type FollowTarget = { type: "player" | "npc"; id: string } | null;
