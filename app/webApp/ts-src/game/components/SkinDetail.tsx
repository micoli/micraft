import { useState } from "react";
import { SkinModelPreview } from "./SkinModelPreview";

export function SkinDetail({ name }: { name: string }) {
  const [walking, setWalking] = useState(true);

  const btnStyle = (active: boolean): React.CSSProperties => ({
    flex: 1,
    background: active ? "#2a3d2a" : "#1e1e1e",
    border: `1px solid ${active ? "#4a7a4a" : "#333"}`,
    borderRadius: 4,
    color: active ? "#7aac7a" : "#666",
    fontFamily: "monospace",
    fontSize: 11,
    cursor: "pointer",
    padding: "4px 0",
  });

  return (
    <div style={{ display: "flex", flexDirection: "column", gap: 12, padding: 8 }}>
      <div style={{ display: "flex", justifyContent: "center" }}>
        <SkinModelPreview skin={name} walking={walking} />
      </div>
      <div style={{ fontSize: 15, fontWeight: "bold", color: "#eee", textAlign: "center" }}>
        {name.replace(/_/g, " ")}
      </div>
      <div style={{ display: "flex", gap: 4 }}>
        <button style={btnStyle(!walking)} onClick={() => setWalking(false)}>
          Statique
        </button>
        <button style={btnStyle(walking)} onClick={() => setWalking(true)}>
          Marche
        </button>
      </div>
      <div style={{ display: "flex", justifyContent: "center" }}>
        <button
          onClick={() => window.mcState.events.push(`cmd:/skin ${name}`)}
          style={{
            background: "#2a3d2a",
            border: "1px solid #4a7a4a",
            borderRadius: 4,
            color: "#7aac7a",
            fontFamily: "monospace",
            fontSize: 12,
            cursor: "pointer",
            padding: "5px 16px",
          }}
        >
          Équiper
        </button>
      </div>
    </div>
  );
}
