import { Vehicle3DPreview } from "./Vehicle3DPreview";
import type { VehicleEntry } from "./CodexModal";

export function VehicleDetail({ vehicle }: { vehicle: VehicleEntry }) {
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
        <Vehicle3DPreview vehicle={vehicle} />
      </div>
      <div style={{ fontSize: 15, fontWeight: "bold", color: "#eee", textAlign: "center" }}>
        {vehicle.type.replace(/_/g, " ")}
      </div>
      <div>
        {row("Taille", `${vehicle.width.toFixed(1)} × ${vehicle.height.toFixed(1)}`)}
        {row("Vitesse", `${vehicle.speed.toFixed(1)} blocs/s`)}
      </div>
    </div>
  );
}
