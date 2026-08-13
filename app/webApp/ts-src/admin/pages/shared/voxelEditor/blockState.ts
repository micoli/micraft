// Mirrors BlockState.pack() (BlockState.kt): bits 0-1 rotation, bits 2-7 plain color index
// (0 = untinted, keeps the block's own texture). Shared by both the Instance and Scene editors.
export function packState(rotation: number, colorIndex: number): number {
  return ((colorIndex & 0x3f) << 2) | (rotation & 0x03);
}
