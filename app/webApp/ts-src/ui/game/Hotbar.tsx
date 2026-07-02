const ITEM_META: Record<string, { label: string; bg: string }> = {
  COBBLESTONE: { label: "COB", bg: "#7A7A7A" },
  DIRT: { label: "DRT", bg: "#8B5A2B" },
  SAND: { label: "SND", bg: "#D5C89A" },
  GRAVEL: { label: "GRV", bg: "#9A9A9A" },
  SANDSTONE: { label: "SST", bg: "#C8B46C" },
  SNOWBALL: { label: "SNW", bg: "#DCE8F5" },
  FLINT: { label: "FLT", bg: "#4A4A52" },
  SEED: { label: "SED", bg: "#C8A050" },
};

interface Props {
  inventory: Record<string, number>;
  visible: boolean;
}

export function Hotbar({ inventory, visible }: Props) {
  if (!visible) return null;

  const items = Object.keys(ITEM_META)
    .filter((type) => (inventory[type] ?? 0) > 0)
    .map((type) => ({ type, count: inventory[type], meta: ITEM_META[type] }));

  return (
    <div className="fixed bottom-5 left-1/2 -translate-x-1/2 flex gap-1 pointer-events-none z-[998] items-center bg-black/60 border border-white/20 rounded-md py-1.5 px-2.5 min-w-[120px] min-h-[68px]">
      {items.length === 0 ? (
        <div className="text-white/35 font-mono text-xs text-center w-full px-4 py-2">
          Inventaire vide
        </div>
      ) : (
        items.map(({ type, count, meta }) => (
          <div
            key={type}
            className="w-[52px] h-[52px] bg-black/72 border-2 border-white/45 rounded flex flex-col items-center justify-center relative"
          >
            <div
              className="w-[26px] h-[26px] rounded-sm"
              style={{
                background: meta.bg,
                boxShadow:
                  "inset -3px -3px 0 rgba(0,0,0,0.3),inset 3px 3px 0 rgba(255,255,255,0.15)",
              }}
            />
            <div className="text-white/70 font-mono text-[8px] mt-0.5 tracking-[0.5px]">
              {meta.label}
            </div>
            <div className="absolute bottom-0.5 right-1 text-white font-mono font-bold text-[10px] [text-shadow:1px_1px_0_#000]">
              {count}
            </div>
          </div>
        ))
      )}
    </div>
  );
}
