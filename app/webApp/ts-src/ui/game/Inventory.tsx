interface Props {
  inventory: Record<string, number>;
  itemMeta: Record<string, { label: string; bg: string }>;
  visible: boolean;
  layoutStyle?: React.CSSProperties;
}

export function Inventory({ inventory, itemMeta, visible, layoutStyle }: Props) {
  if (!visible) return null;

  const items = Object.keys(inventory)
    .filter((type) => (inventory[type] ?? 0) > 0 && itemMeta[type] !== undefined)
    .map((type) => ({ type, count: inventory[type], meta: itemMeta[type] }));

  const containerStyle: React.CSSProperties = layoutStyle
    ? {
        display: "flex",
        gap: 4,
        pointerEvents: "all",
        zIndex: 998,
        alignItems: "center",
        background: "rgba(0,0,0,0.75)",
        border: "1px solid rgba(255,255,255,0.3)",
        borderRadius: 6,
        padding: "8px 12px",
        minWidth: 120,
        minHeight: 68,
        flexWrap: "wrap",
        ...layoutStyle,
      }
    : {
        position: "fixed",
        bottom: 100,
        left: "50%",
        transform: "translateX(-50%)",
        display: "flex",
        gap: 4,
        pointerEvents: "all",
        zIndex: 998,
        alignItems: "center",
        background: "rgba(0,0,0,0.75)",
        border: "1px solid rgba(255,255,255,0.3)",
        borderRadius: 6,
        padding: "8px 12px",
        minWidth: 120,
        minHeight: 68,
      };

  return (
    <div style={containerStyle}>
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
          Inventory empty
        </div>
      ) : (
        items.map(({ type, count, meta }) => (
          <div
            key={type}
            draggable
            onDragStart={(e) => {
              e.dataTransfer.setData("text/plain", type);
              e.dataTransfer.effectAllowed = "copy";
            }}
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
              cursor: "grab",
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
