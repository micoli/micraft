import { useState } from "react";
import { Block3DPreview } from "../shared/Block3DPreview";
import { PLAIN_COLORABLE_PREVIEW_HEX } from "../shared/blockPreviewCache";
import type { BlockEntry } from "./CodexModal";

export function BlockDetail({
  block,
  defsReady,
  giveItemName,
}: {
  block: BlockEntry;
  defsReady: boolean;
  giveItemName: string | null;
}) {
  const [qty, setQty] = useState(1);

  const row = (label: string, value: string | number | boolean) => (
    <div
      key={label}
      style={{ display: "flex", justifyContent: "space-between", padding: "4px 0", borderBottom: "1px solid #2a2a2a" }}
    >
      <span style={{ color: "#888", fontSize: 12 }}>{label}</span>
      <span style={{ color: "#ddd", fontSize: 12 }}>{String(value)}</span>
    </div>
  );

  return (
    <div style={{ display: "flex", flexDirection: "column", gap: 12, padding: 8 }}>
      <div style={{ display: "flex", justifyContent: "center" }}>
        {defsReady ? (
          <Block3DPreview
            ordinal={block.ordinal}
            colorHex={block.plainColorable ? PLAIN_COLORABLE_PREVIEW_HEX : undefined}
          />
        ) : (
          <div style={{ width: 160, height: 160, background: "#1a1a1a", borderRadius: 6 }} />
        )}
      </div>
      <div style={{ fontSize: 15, fontWeight: "bold", color: "#eee", textAlign: "center" }}>
        {block.name.replace(/_/g, " ")}
      </div>
      <div>
        {row("Dureté", block.hardness === -1 ? "∞" : block.hardness)}
        {row("Solide", block.solid ? "oui" : "non")}
        {row("Transparent", block.transparent ? "oui" : "non")}
        {row("Liquide", block.liquid ? "oui" : "non")}
      </div>
      <div style={{ display: "flex", gap: 6, alignItems: "center", paddingTop: 4 }}>
        <input
          type="number"
          min={1}
          max={128}
          value={qty}
          disabled={!giveItemName}
          onChange={(e) => setQty(Math.max(1, Math.min(128, parseInt(e.target.value) || 1)))}
          style={{
            width: 56,
            background: "#1e1e1e",
            border: "1px solid #3a3a3a",
            borderRadius: 4,
            color: giveItemName ? "#ddd" : "#555",
            fontFamily: "monospace",
            fontSize: 12,
            padding: "4px 6px",
            outline: "none",
          }}
        />
        <button
          disabled={!giveItemName}
          onClick={() => giveItemName && window.mcState.events.push(`cmd:/give ${giveItemName} ${qty}`)}
          style={{
            flex: 1,
            background: giveItemName ? "#2a3d2a" : "#1e1e1e",
            border: `1px solid ${giveItemName ? "#4a7a4a" : "#2a2a2a"}`,
            borderRadius: 4,
            color: giveItemName ? "#7aac7a" : "#444",
            fontFamily: "monospace",
            fontSize: 12,
            cursor: giveItemName ? "pointer" : "default",
            padding: "4px 8px",
          }}
          title={giveItemName ? undefined : "Aucun item disponible pour ce bloc"}
        >
          Donner
        </button>
      </div>
    </div>
  );
}
