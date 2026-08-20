import type { BlockEditSocket } from "./editSocket";
import type { RailJunction } from "./railSwitchMarkers";

// Shape common to InstanceBlockDto/SceneBlockDto — structural, no import of either concrete DTO
// needed here.
export interface VoxelBlockLike {
  x: number;
  y: number;
  z: number;
  type: string;
  state: number;
  xOffset: number;
  zOffset: number;
}

// Bridges the parts of InstanceEditorViewport/SceneEditorViewport that genuinely differ (chunk vs
// whole-buffer volume, mcAdmin* vs mcScene* wasm exports, chunk-reload vs entity-reload refresh)
// so voxelEditorSceneController.ts's handlers can stay identical between the two editors. Built
// inside each component's scene-setup effect, closing over its local `scene`/`wasmExports`/
// `editSocket`/reload functions.
export interface VoxelVolumeAdapter<TBlockDto extends VoxelBlockLike> {
  // True once the wasm module powering this volume is ready to answer block queries — mirrors each
  // editor's own `!!wasmExports` check.
  isReady(): boolean;
  inBounds(x: number, y: number, z: number): boolean;
  getBlockOrdinalAt(x: number, y: number, z: number): number;
  getBlockStateAt(x: number, y: number, z: number): number;
  getUsedXZOffsetAt(x: number, y: number, z: number): [number, number] | null;
  // Instance: always non-null (opened synchronously alongside chunk streaming). Scene: null until
  // its initial bulk load resolves.
  getEditSocket(): BlockEditSocket<TBlockDto> | null;
  // Instant local feedback via the wasm mesher. Instance: no-op (reloadChunk re-fetches from the
  // server instead). Scene: mcSceneSetBlock + re-enable mesh picking.
  applyLocal(edit: TBlockDto): void;
  // Called once after a send/sendBatch + applyLocal round, with every (x,z) touched. Instance:
  // dedupes into chunk keys and calls reloadChunk per chunk. Scene: ignores the list, calls
  // reloadEntities().
  afterEdit(touched: { x: number; z: number }[]): void;
  // Applied after a rail-switch click's sendRaw. Instance: deferred, conditional refresh (handled
  // inside its own reloadChunk). Scene: applies the toggle locally via mcSceneSetExtraState and
  // refreshes synchronously. Each side owns its own timing — do not reorder relative to sendRaw.
  afterRailSwitchToggle(junction: RailJunction): void;
  // Scene additionally bounds-checks a rail-test pick before starting the cart; Instance never did
  // (picks only ever land on real meshes, already inside its zone). Defaults to true if omitted.
  railPickInBounds?(x: number, y: number, z: number): boolean;
}
