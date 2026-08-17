import type { ArmorStatBonus } from "./Character";

function formatBonus(v: number): string {
  return v === 0 ? "" : v > 0 ? `+${v}` : `${v}`;
}

export function ArmorBonusLine({ bonus }: { bonus: ArmorStatBonus | undefined }) {
  if (!bonus) return null;
  const parts = (["str", "dex", "intel", "wis", "con", "cha"] as const)
    .map((k) => ({ k, v: bonus[k] ?? 0 }))
    .filter(({ v }) => v !== 0)
    .map(({ k, v }) => `${k.toUpperCase()} ${formatBonus(v)}`);
  if (parts.length === 0) return null;
  return <span className="text-green-400 text-xs ml-1">{parts.join("  ")}</span>;
}
