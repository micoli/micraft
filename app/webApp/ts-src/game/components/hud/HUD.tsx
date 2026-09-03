import { ActionBlockHudData, HudData } from "../../types";
import { cn } from "../../../primitives/cn";
import { HudRow } from "./HudRow";

export function HUD({
  data,
  actionBlock,
  layoutStyle,
}: {
  data: HudData | null;
  actionBlock?: ActionBlockHudData | null;
  layoutStyle?: React.CSSProperties;
}) {
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
        {actionBlock && (
          <>
            <HudRow label={"ActionBlock"} value={actionBlock.name} />
            <div className="text-amber-300/80 text-[11px] whitespace-pre-wrap break-all max-w-[240px]">
              {JSON.stringify(actionBlock.values)}
            </div>
          </>
        )}
      </div>
    </div>
  );
}
