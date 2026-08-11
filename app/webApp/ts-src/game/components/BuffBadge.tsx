import { useEffect, useState } from "react";
import { ActiveEffect } from "../UIStateRegistry";

const BUFF_LABELS: Record<string, string> = {
  HpBoost: "❤️+20",
  ManaBoost: "💧+20",
  HpRegenBoost: "❤️↑10%",
  ManaRegenBoost: "💧↑10%",
};

const BUFF_COLORS: Record<string, string> = {
  HpBoost: "#e05050",
  ManaBoost: "#4080e0",
  HpRegenBoost: "#a040a0",
  ManaRegenBoost: "#4090c0",
};

export function BuffBadge({ effect }: { effect: ActiveEffect }) {
  const [remaining, setRemaining] = useState(0);

  useEffect(() => {
    const update = () => {
      const secs = Math.max(0, Math.ceil((effect.expiresAtMs - Date.now()) / 1000));
      setRemaining(secs);
    };
    update();
    const id = setInterval(update, 500);
    return () => clearInterval(id);
  }, [effect.expiresAtMs]);

  const label = BUFF_LABELS[effect.name] ?? effect.name;
  const color = BUFF_COLORS[effect.name] ?? "#888";

  return (
    <div
      style={{ borderColor: color }}
      className="flex flex-col items-center justify-center w-14 h-14 rounded border-2 bg-black/70 text-white gap-0.5"
    >
      <span className="text-[13px] leading-none">{label}</span>
      <span className="text-[10px] text-white/60 font-mono leading-none">{remaining}s</span>
    </div>
  );
}
