import type { CSSProperties } from "react";
import { NpcProximityEntry } from "../types";

interface Props {
  npcProximity?: NpcProximityEntry[];
  layoutStyle?: CSSProperties;
}

export function AggroIndicators({ npcProximity = [], layoutStyle }: Props) {
  return (
    <div
      className="flex flex-col gap-1 bg-black/55 rounded-md px-3 py-2 pointer-events-none z-[998]"
      style={{ ...layoutStyle, userSelect: "none" }}
    >
      {npcProximity.length > 0 && (
        <div className="flex flex-row gap-2 mt-0.5">
          {npcProximity.map((npc) => {
            const angleDeg = (npc.relAngle * 180) / Math.PI;
            return (
              <div key={npc.id} className="flex flex-col gap-0 min-w-0">
                <div className="flex items-center gap-1">
                  <svg
                    width="12"
                    height="12"
                    viewBox="0 0 12 12"
                    style={{ transform: `rotate(${angleDeg}deg)`, flexShrink: 0 }}
                  >
                    <polygon points="6,1 10,11 6,8 2,11" fill={npc.aggro ? "#e74c3c" : "rgba(255,255,255,0.45)"} />
                  </svg>
                  <span
                    className="font-mono text-[9px]"
                    style={{ color: npc.aggro ? "#e74c3c" : "rgba(255,255,255,0.55)" }}
                  >
                    {Math.round(npc.dist)}m
                  </span>
                </div>
                <span
                  className="font-mono text-[9px] truncate max-w-[96px]"
                  style={{ color: npc.aggro ? "#e74c3c" : "rgba(255,255,255,0.4)" }}
                  title={npc.name}
                >
                  {npc.name || npc.id}
                </span>
              </div>
            );
          })}
        </div>
      )}
    </div>
  );
}
