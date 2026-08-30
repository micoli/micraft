import React, { useEffect, useState } from "react";
import { PetRosterData } from "../../types";

interface Props {
  roster: PetRosterData;
  layoutStyle: React.CSSProperties;
  onCommand: (cmd: string) => void;
}

const t = (key: string): string => window.mc?.t?.(key) ?? key;

export function PetHud({ roster, layoutStyle, onCommand }: Props) {
  const [now, setNow] = useState(0);

  useEffect(() => {
    setNow(Date.now());
    const id = window.setInterval(() => setNow(Date.now()), 1000);
    return () => window.clearInterval(id);
  }, []);

  if (roster.pets.length === 0) return null;

  const active = roster.pets.find((p) => p.id === roster.activePetId) ?? null;
  const deadOnCooldown = roster.pets.filter((p) => p.dead);

  return (
    <div
      style={{ ...layoutStyle, zIndex: 200, overflowY: "auto", pointerEvents: "none" }}
      className="font-mono text-white"
    >
      {active && (
        <div className="rounded bg-black/70 border border-emerald-500/30 px-2 py-1.5 backdrop-blur-sm mb-1">
          <div className="flex justify-between text-xs font-semibold text-emerald-400 leading-tight">
            <span className="truncate mr-1">{active.name}</span>
            <span className="shrink-0">
              {t("pet:client:level")} {active.level}
            </span>
          </div>
          <div className="h-1.5 bg-white/10 rounded-full mt-1 overflow-hidden">
            <div
              className="h-full bg-red-500 rounded-full"
              style={{ width: `${Math.max(0, Math.min(100, (active.currentHp / active.maxHp) * 100))}%` }}
            />
          </div>
          <div className="text-[10px] text-white/50 mt-0.5">
            {active.currentHp}/{active.maxHp} HP
          </div>
        </div>
      )}
      {deadOnCooldown.map((p) => {
        const remainingS = Math.max(0, Math.ceil((p.resurrectReadyAtMs - now) / 1000));
        return (
          <div key={p.id} className="rounded bg-black/70 border border-red-500/30 px-2 py-1.5 backdrop-blur-sm mb-1">
            <div className="text-xs text-red-400 truncate">{p.name}</div>
            {remainingS > 0 ? (
              <div className="text-[10px] text-white/50">
                {t("pet:client:cooldown")} {remainingS}s
              </div>
            ) : (
              <button
                type="button"
                style={{ pointerEvents: "auto" }}
                className="mt-1 text-[11px] px-2 py-0.5 rounded bg-emerald-600 hover:bg-emerald-500"
                onClick={() => onCommand(`/pet resurrect ${p.name}`)}
              >
                {t("pet:client:resurrect")}
              </button>
            )}
          </div>
        );
      })}
    </div>
  );
}
