import { useEffect, useState } from "react";
import { api, type BlockInfoDto, type PlainColorDto } from "../../../api";

// Loads the block/plain-color registries once and pushes them into both the game-bundle globals
// (window.mc) and the WASM admin preview module — shared by the Instance and Scene editors, which
// both render via the same block registry regardless of which volume (chunk vs scene buffer) is
// being meshed.
export function useBlockRegistry() {
  const [blockDefs, setBlockDefs] = useState<BlockInfoDto[]>([]);
  const [ordinalByName, setOrdinalByName] = useState<Map<string, number>>(new Map());
  const [plainColors, setPlainColors] = useState<PlainColorDto[]>([]);

  useEffect(() => {
    Promise.all([api.blocks.list(), api.plainColors.list()])
      .then(([defs, colors]) => {
        const withoutAir = defs.filter((b) => b.name !== "AIR");
        setBlockDefs(withoutAir);
        setOrdinalByName(new Map(defs.map((b, i) => [b.name, i])));
        setPlainColors(colors);
        // Plain-color materials (chunkBuilder.ts's plainMatKey) are built from this palette —
        // must be loaded before the block registry so the first mesh resolves them.
        window.mc.setPlainColors?.(JSON.stringify(colors));
        window.mc.setBlockRegistry?.(JSON.stringify(defs));
        window.webApp?.then((exports) => exports.mcAdminSetBlockRegistry(JSON.stringify(defs)));
      })
      .catch(console.error);
  }, []);

  const getOrdinal = (name: string): number | null => ordinalByName.get(name) ?? null;

  return { blockDefs, ordinalByName, plainColors, getOrdinal };
}
