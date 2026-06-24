const ITEM_META: Record<string, { label: string; bg: string }> = {
  COBBLESTONE: { label: "COB", bg: "#7A7A7A" },
  DIRT: { label: "DRT", bg: "#8B5A2B" },
  SAND: { label: "SND", bg: "#D5C89A" },
  GRAVEL: { label: "GRV", bg: "#9A9A9A" },
  SANDSTONE: { label: "SST", bg: "#C8B46C" },
  SNOWBALL: { label: "SNW", bg: "#DCE8F5" },
  FLINT: { label: "FLT", bg: "#4A4A52" },
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
    <div
      style={{
        position: "fixed",
        bottom: 20,
        left: "50%",
        transform: "translateX(-50%)",
        display: "flex",
        gap: 4,
        pointerEvents: "none",
        zIndex: 998,
        alignItems: "center",
        background: "rgba(0,0,0,0.6)",
        border: "1px solid rgba(255,255,255,0.2)",
        borderRadius: 6,
        padding: "6px 10px",
        minWidth: 120,
        minHeight: 68,
      }}
    >
      {items.length === 0 ? (
        <div
          style={{
            color: "rgba(255,255,255,0.35)",
            font: "12px monospace",
            padding: "8px 16px",
            textAlign: "center",
            width: "100%",
          }}
        >
          Inventaire vide
        </div>
      ) : (
        items.map(({ type, count, meta }) => (
          <div
            key={type}
            style={{
              width: 52,
              height: 52,
              background: "rgba(0,0,0,0.72)",
              border: "2px solid rgba(255,255,255,0.45)",
              borderRadius: 4,
              display: "flex",
              flexDirection: "column",
              alignItems: "center",
              justifyContent: "center",
              position: "relative",
            }}
          >
            <div
              style={{
                width: 26,
                height: 26,
                borderRadius: 3,
                background: meta.bg,
                boxShadow: "inset -3px -3px 0 rgba(0,0,0,0.3),inset 3px 3px 0 rgba(255,255,255,0.15)",
              }}
            />
            <div
              style={{ color: "rgba(255,255,255,0.7)", font: "8px monospace", marginTop: 3, letterSpacing: "0.5px" }}
            >
              {meta.label}
            </div>
            <div
              style={{
                position: "absolute",
                bottom: 2,
                right: 4,
                color: "#fff",
                font: "bold 10px monospace",
                textShadow: "1px 1px 0 #000",
              }}
            >
              {count}
            </div>
          </div>
        ))
      )}
    </div>
  );
}
