import type { RefObject } from "react";
import { CodexCard } from "./CodexCard";
import { EmojiThumbnail } from "./EmojiThumbnail";
import { Vehicle3DPreview } from "./Vehicle3DPreview";
import { CodexDetailLayout } from "./CodexDetailLayout";
import { CodexDetailRow } from "./CodexDetailRow";
import type { VehicleEntry } from "./CodexModal";
import { filterInputStyle, filterWrapperStyle, gridStyle } from "./codexListStyles";

interface Props {
  vehicles: VehicleEntry[];
  filter: string;
  onFilterChange: (filter: string) => void;
  gridRef: RefObject<HTMLDivElement | null>;
  selectedKey: string | number | undefined;
  onSelect: (key: string) => void;
  registerRef: (key: string, el: HTMLDivElement | null) => void;
}

export function filterVehicles(vehicles: VehicleEntry[], filter: string): VehicleEntry[] {
  return vehicles.filter((v) => v.type.toLowerCase().includes(filter.toLowerCase()));
}

export function VehicleList({ vehicles, filter, onFilterChange, gridRef, selectedKey, onSelect, registerRef }: Props) {
  return (
    <>
      <div style={filterWrapperStyle}>
        <input
          type="text"
          placeholder="Filtrer…"
          value={filter}
          onChange={(e) => onFilterChange(e.target.value)}
          style={filterInputStyle}
        />
      </div>
      <div ref={gridRef} style={gridStyle}>
        {filterVehicles(vehicles, filter).map((vehicle) => (
          <CodexCard
            key={vehicle.type}
            ref={(el) => registerRef(vehicle.type, el)}
            selected={selectedKey === vehicle.type}
            onClick={() => onSelect(vehicle.type)}
            title={vehicle.type}
            label={vehicle.type.replace(/_/g, " ")}
            thumbnail={<EmojiThumbnail emoji="🛒" />}
          />
        ))}
      </div>
    </>
  );
}

// eslint-disable-next-line react/no-multi-comp -- co-located with VehicleList per codex list/detail pairing convention
export function VehicleDetail({ vehicle }: { vehicle: VehicleEntry }) {
  return (
    <CodexDetailLayout preview={<Vehicle3DPreview vehicle={vehicle} />} title={vehicle.type.replace(/_/g, " ")}>
      <div>
        <CodexDetailRow label="Taille" value={`${vehicle.width.toFixed(1)} × ${vehicle.height.toFixed(1)}`} />
        <CodexDetailRow label="Vitesse" value={`${vehicle.speed.toFixed(1)} blocs/s`} />
      </div>
    </CodexDetailLayout>
  );
}
