import { NpcProximityEntry } from "../../types";
import { UiState } from "../../UIReducer";
import { Bar } from "./Bar";
import { GcdBar } from "./GcdBar";

interface Props {
  status: NonNullable<UiState["playerStatus"]>;
  godMode?: boolean;
  npcProximity?: NpcProximityEntry[];
  layoutStyle?: React.CSSProperties;
}

export function PlayerStatusBar({ status, godMode, layoutStyle }: Props) {
  const playerName = window.mcState?.playerName ?? "";
  return (
    <div
      className="flex flex-col gap-1 bg-black/55 rounded-md px-3 py-2 pointer-events-none z-[998]"
      style={{ ...layoutStyle, userSelect: "none" }}
    >
      {playerName && (
        <div className="flex items-center gap-2 w-full">
          <span className="text-white/80 text-[11px] font-semibold shrink-0 pb-0.5">
            {godMode && <span className="text-yellow-400 mr-0.5">☢︎</span>}
            {playerName}
          </span>
          <div className="flex-1 min-w-0">
            <GcdBar remainingMs={status.globalCooldownRemainingMs} />
          </div>
          {status.maxTokens > 0 && (
            <div className="flex items-center gap-1 shrink-0">
              <span className="text-orange-300/70 font-mono text-[10px]">TK</span>
              <div className="flex gap-0.5">
                {Array.from({ length: status.maxTokens }).map((_, i) => (
                  <div
                    key={i}
                    className="w-3 h-3 rounded-sm border border-orange-400/60"
                    style={{ background: i < (status.currentTokens ?? 0) ? "#e67e22" : "rgba(0,0,0,0.4)" }}
                  />
                ))}
              </div>
            </div>
          )}
        </div>
      )}
      <Bar value={status.currentHp} max={status.maxHp} color="#c0392b" label="HP" />
      {status.maxMana > 0 && <Bar value={status.currentMana} max={status.maxMana} color="#2980b9" label="MP" />}
      {status.maxRage > 0 && <Bar value={status.currentRage} max={status.maxRage} color="#e67e22" label="RP" />}
    </div>
  );
}
