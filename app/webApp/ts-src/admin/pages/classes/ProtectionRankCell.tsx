import { ProtectionRankDto } from "../../apiTypes";
import { useT } from "../../i18n";

/** Compact per-Rank summary: only the bonuses this Rank actually sets, plus cost/Cooldown. */
function bonusSummary(rank: ProtectionRankDto): string[] {
  const parts: string[] = [];
  if (rank.acBonus) parts.push(`AC+${rank.acBonus}`);
  if (rank.dodgeBonusPct) parts.push(`Dodge+${rank.dodgeBonusPct}%`);
  if (rank.magicResistBonusPct) parts.push(`MRes+${rank.magicResistBonusPct}%`);
  if (rank.maxHpBonus) parts.push(`HP+${rank.maxHpBonus}`);
  if (rank.hpRegenMultBonus) parts.push(`Regen×${(1 + rank.hpRegenMultBonus).toFixed(1)}`);
  return parts;
}

function costLabel(rank: ProtectionRankDto): string | null {
  if (rank.manaCost > 0) return `${rank.manaCost} mana`;
  if (rank.rageCost > 0) return `${rank.rageCost} rage`;
  return null;
}

export function ProtectionRankCell({ rank }: { rank: ProtectionRankDto | undefined }) {
  const t = useT();
  if (!rank) {
    return <td className="border border-[#2E3A4E] px-3 py-2 text-center text-[#2E3A4E]">—</td>;
  }
  const cost = costLabel(rank);
  return (
    <td className="border border-[#2E3A4E] px-3 py-2">
      <div className="flex flex-col gap-0.5">
        <span className="text-[11px] font-mono text-emerald-400">{bonusSummary(rank).join(" ")}</span>
        <span className="text-[9px] text-[#8A99AF]">
          {rank.durationSec}s · {t("classes.protectionCooldown")} {rank.cooldownMs / 1000}s{cost ? ` · ${cost}` : ""}
        </span>
      </div>
    </td>
  );
}
