import { Npc3DPreview } from "./Npc3DPreview";
import type { NpcEntry } from "./CodexModal";

export function NpcDetail({ npc }: { npc: NpcEntry }) {
  const behaviorLabel: Record<string, string> = {
    interactionable: "PNJ interactif",
    random_movable: "Vagabond",
    static: "Statique",
  };

  const row = (label: string, value: string) => (
    <div
      key={label}
      style={{ display: "flex", justifyContent: "space-between", padding: "4px 0", borderBottom: "1px solid #2a2a2a" }}
    >
      <span style={{ color: "#888", fontSize: 12 }}>{label}</span>
      <span style={{ color: "#ddd", fontSize: 12 }}>{value}</span>
    </div>
  );

  return (
    <div style={{ display: "flex", flexDirection: "column", gap: 12, padding: 8 }}>
      <div style={{ display: "flex", justifyContent: "center" }}>
        <Npc3DPreview npc={npc} />
      </div>
      <div style={{ fontSize: 15, fontWeight: "bold", color: "#eee", textAlign: "center" }}>
        {npc.type.replace(/_/g, " ")}
      </div>
      <div>
        {row("Comportement", behaviorLabel[npc.behaviorKey] ?? npc.behaviorKey)}
        {row("Taille", `${npc.width.toFixed(1)} × ${npc.height.toFixed(1)}`)}
        {row("Spawn auto", npc.autoSpawn ? "oui" : "non")}
        {npc.wanderSpeed > 0 ? row("Vitesse", npc.wanderSpeed.toFixed(1)) : null}
      </div>
    </div>
  );
}
