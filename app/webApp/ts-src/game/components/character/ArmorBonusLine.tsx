import type { ArmorSlots, ArmorStatBonus } from "./Character";
import { ArmorSlotsDiagram } from "./ArmorSlotsDiagram";

function formatBonus(v: number): string {
  return v === 0 ? "" : v > 0 ? `+${v}` : `${v}`;
}

export function ArmorBonusLine({
  bonus,
  wearable,
}: {
  bonus: ArmorStatBonus | undefined;
  wearable: ArmorSlots | undefined;
}) {
  const parts = bonus
    ? (["str", "dex", "intel", "wis", "con", "cha"] as const)
        .map((k) => ({ k, v: bonus[k] ?? 0 }))
        .filter(({ v }) => v !== 0)
        .map(({ k, v }) => `${k.toUpperCase()} ${formatBonus(v)}`)
    : [];

  return (
    <div className="flex items-center gap-2">
      <ArmorSlotsDiagram wearable={wearable} />
      {parts.length > 0 && <span className="text-green-400 text-xs">{parts.join("  ")}</span>}
    </div>
  );
}
