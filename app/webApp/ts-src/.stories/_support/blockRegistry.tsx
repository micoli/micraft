import * as BABYLON from "babylonjs";
import type { Decorator } from "@storybook/react";
import { registerBlockDefs, setRegistryBlocks, setRegistryItems, setPlainColors } from "../../game/lib/blockDefs";

// Real 3D block/item previews in Storybook. The rendering pipeline
// (game/shared/blockPreviewCache.ts + BlockPreview.tsx) already does everything once
// window.BABYLON + the window.mc block-def surface + a loaded registry exist and the
// .bbmodel / texture files are reachable at /api/models/blocks/<NAME>/... (served via
// staticDirs in .storybook/main.ts). This decorator wires those up.

type BlockFixture = { name: string; modelElement: string; minimapColor: [number, number, number] };
type ItemFixture = { buildable: boolean; placesBlock: string | null };

export const DEFAULT_BLOCKS: BlockFixture[] = [
  { name: "STONE", modelElement: "STONE", minimapColor: [128, 128, 128] },
  { name: "COBBLESTONE", modelElement: "COBBLESTONE", minimapColor: [122, 122, 122] },
  { name: "DIRT", modelElement: "DIRT", minimapColor: [134, 96, 67] },
  { name: "SAND", modelElement: "SAND", minimapColor: [219, 207, 163] },
  { name: "SANDSTONE", modelElement: "SANDSTONE", minimapColor: [216, 203, 155] },
  { name: "GRAVEL", modelElement: "GRAVEL", minimapColor: [136, 126, 120] },
  { name: "OAK_LOG", modelElement: "OAK_LOG", minimapColor: [102, 81, 48] },
];

export const DEFAULT_ITEMS: Record<string, ItemFixture> = {
  COBBLESTONE: { buildable: true, placesBlock: "COBBLESTONE" },
  DIRT: { buildable: true, placesBlock: "DIRT" },
  SAND: { buildable: true, placesBlock: "SAND" },
  SANDSTONE: { buildable: true, placesBlock: "SANDSTONE" },
  GRAVEL: { buildable: true, placesBlock: "GRAVEL" },
  OAK_LOG: { buildable: true, placesBlock: "OAK_LOG" },
  // Not placeable blocks — ItemIcon keeps the colored-square fallback for these.
  SNOWBALL: { buildable: false, placesBlock: null },
  FLINT: { buildable: false, placesBlock: null },
};

interface Opts {
  blocks?: BlockFixture[];
  items?: Record<string, ItemFixture>;
  plainColors?: { name: string; hex: string }[];
}

type StoryWindow = typeof window & {
  mc?: Record<string, unknown>;
  mcState?: Record<string, unknown>;
  mcT?: (k: string) => string;
  __sbBlockRegistryKey?: string;
};

export function withBlockRegistry(opts: Opts = {}): Decorator {
  return function WithBlockRegistry(Story) {
    const w = window as StoryWindow;

    w.BABYLON = w.BABYLON ?? (BABYLON as unknown as typeof w.BABYLON);
    w.mc = { ...(w.mc ?? {}), ...registerBlockDefs() };
    w.mcT = w.mcT ?? ((k: string) => k);
    w.mcState = {
      events: [],
      playerName: "alice",
      playerId: "player-1",
      scenes: [],
      ...w.mcState,
    };

    const blocks = opts.blocks ?? DEFAULT_BLOCKS;
    const items = { ...DEFAULT_ITEMS, ...opts.items };

    // Re-applied every render so decorator ordering vs stubMcState stops mattering.
    w.mcState.codexBlocks = blocks;
    w.mcState.codexItems = items;
    setPlainColors(
      (opts.plainColors ?? []).map(({ name, hex }) => {
        const n = parseInt(hex, 16);
        return { name, hex, r: (n >> 16) & 0xff, g: (n >> 8) & 0xff, b: n & 0xff };
      }),
    );
    setRegistryItems(items);

    // initBlockDefs() fires an internal fetch sweep — run it once per distinct block set.
    const key = JSON.stringify(blocks.map((b) => b.name));
    if (w.__sbBlockRegistryKey !== key) {
      w.__sbBlockRegistryKey = key;
      setRegistryBlocks(blocks as unknown as Parameters<typeof setRegistryBlocks>[0]);
      (w.mc.initBlockDefs as () => void)();
    }

    return <Story />;
  };
}
