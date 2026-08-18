import { useEffect, useState } from "react";
import { getApiAdminBlocks, getApiAdminPlainColors } from "../../../../generated/api/requests";
import type { BlockInfoDto, PlainColorDto } from "../../../apiTypes";

// Loads the block/plain-color registries once and pushes them into both the game-bundle globals
// (window.mc) and the WASM admin preview module — shared by the Instance and Scene editors, which
// both render via the same block registry regardless of which volume (chunk vs scene buffer) is
// being meshed.
export function useBlockRegistry() {
  const [blockDefs, setBlockDefs] = useState<BlockInfoDto[]>([]);
  const [ordinalByName, setOrdinalByName] = useState<Map<string, number>>(new Map());
  // Ordinal-indexed block type names — NOT the same as window.mc.getBlockDef(ordinal)?.name, which
  // is the first bbmodel cube's own (often copy-pasted, unrelated) name, an authoring artifact.
  const [nameByOrdinal, setNameByOrdinal] = useState<string[]>([]);
  const [plainColors, setPlainColors] = useState<PlainColorDto[]>([]);

  useEffect(() => {
    Promise.all([
      getApiAdminBlocks({ throwOnError: true }).then((r) => r.data),
      getApiAdminPlainColors({ throwOnError: true }).then((r) => r.data),
    ])
      .then(([defs, colors]) => {
        const withoutAir = defs.filter((b) => b.name !== "AIR");
        setBlockDefs(withoutAir);
        setOrdinalByName(new Map(defs.map((b, i) => [b.name, i])));
        setNameByOrdinal(defs.map((b) => b.name));
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

  return { blockDefs, ordinalByName, nameByOrdinal, plainColors, getOrdinal };
}
