import { HudData } from "../types";
import { cn } from "../../primitives/cn";

function HudRow({ label, value }: { label: string; value: string | number }) {
  return (
    <div key={label} className="flex items-center gap-1.5">
      <span className="text-[11px] text-white/60 w-14 shrink-0 truncate">{label}</span>
      <span className="text-white/90 truncate">{value}</span>
    </div>
  );
}

export function HUD({ data, layoutStyle }: { data: HudData | null; layoutStyle?: React.CSSProperties }) {
  if (!data) return null;
  const { stance, biome, weather, targetBlock, zoneLevel } = data;

  return (
    <div
      className={cn(
        "bg-black/55 text-white font-mono text-[13px] leading-relaxed px-3 py-2 rounded-md pointer-events-none z-[999]",
        !layoutStyle && "fixed top-3 right-3",
      )}
      style={layoutStyle}
    >
      <div className="flex flex-col gap-y-0.5">
        <HudRow label={"Biome"} value={biome ?? ""} />
        <HudRow label={"Weather"} value={weather ?? ""} />
        <HudRow label={"Stance"} value={stance} />
        <HudRow label={"Block"} value={targetBlock ?? ""} />
        <HudRow label={"Zone Lv"} value={zoneLevel ?? ""} />
      </div>
    </div>
  );
}
