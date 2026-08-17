import { forwardRef } from "react";
import type { VehicleEntry } from "./CodexModal";

export const VehicleCard = forwardRef<
  HTMLDivElement,
  { vehicle: VehicleEntry; selected: boolean; onClick: () => void }
>(function VehicleCard({ vehicle, selected, onClick }, ref) {
  const cardStyle: React.CSSProperties = {
    display: "flex",
    flexDirection: "column",
    alignItems: "center",
    padding: "8px 6px",
    cursor: "pointer",
    borderRadius: 6,
    border: `2px solid ${selected ? "#7aac7a" : "transparent"}`,
    background: selected ? "rgba(122,172,122,0.12)" : "transparent",
    gap: 4,
    width: 90,
  };

  return (
    <div ref={ref} style={cardStyle} onClick={onClick} title={vehicle.type}>
      <div
        style={{
          width: 52,
          height: 52,
          background: "#2a2a2a",
          borderRadius: 8,
          display: "flex",
          alignItems: "center",
          justifyContent: "center",
          fontSize: 28,
          border: "1px solid #444",
        }}
      >
        🛒
      </div>
      <span style={{ fontSize: 11, color: "#ccc", textAlign: "center", lineHeight: 1.2 }}>
        {vehicle.type.replace(/_/g, " ")}
      </span>
    </div>
  );
});
