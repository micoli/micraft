import { useEffect, useState } from "react";
import { ActiveEffect } from "../../UIStateRegistry";

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

// Every Class's Protection shares the wire name "Protected" (StatusEffect.Protected) — the
// per-Protection icon and color are keyed by `protectionId` instead (`ActiveEffect.protectionId`).
const PROTECTION_LABELS: Record<string, string> = {
  iron_skin: "🛡️",
  shadowstep: "👤",
  arcane_ward: "🔮",
  natures_veil: "🍃",
  fortitude: "✨",
};

const PROTECTION_COLORS: Record<string, string> = {
  iron_skin: "#a0a0a0",
  shadowstep: "#606060",
  arcane_ward: "#8040c0",
  natures_veil: "#40a060",
  fortitude: "#e0c040",
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

  const label = effect.protectionId
    ? `${PROTECTION_LABELS[effect.protectionId] ?? "🛡️"}${effect.rank ?? ""}`
    : (BUFF_LABELS[effect.name] ?? effect.name);
  const color = effect.protectionId
    ? (PROTECTION_COLORS[effect.protectionId] ?? "#888")
    : (BUFF_COLORS[effect.name] ?? "#888");

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
