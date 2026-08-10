import { useT } from "../i18n";
import type { SimNpc } from "./types";

export interface HoverTarget {
  npc: SimNpc;
  sx: number;
  sy: number;
}

/** Light hover card over the arena. */
export function NpcTooltip({ hover }: { hover: HoverTarget }) {
  const t = useT();
  const { npc, sx, sy } = hover;
  return (
    <div
      className="pointer-events-none absolute z-10 rounded-md border border-[#2E3A4E] bg-[#1A222C] px-2.5 py-1.5 text-[11px] text-white shadow-lg"
      style={{ left: sx + 14, top: sy + 10 }}
    >
      <div className="font-semibold">{npc.name}</div>
      <div className="text-[#8A99AF]">
        {npc.type} · {t("sim.tooltip.level", npc.level)}
      </div>
      <div>
        {npc.currentHp}/{npc.maxHp} {t("sim.tooltip.hp")}
        {npc.gender ? ` · ${npc.gender === "FEMALE" ? "♀" : "♂"}` : ""}
      </div>
      {npc.hunger != null && (
        <div className="text-[#FACC15]">{t("sim.tooltip.satiety", Math.round((1 - npc.hunger) * 100))}</div>
      )}
      {npc.gestationRemainingDays != null && (
        <div className="text-[#E879F9]">{t("sim.tooltip.gestation", npc.gestationRemainingDays.toFixed(2))}</div>
      )}
      {npc.isDead && <div className="text-[#EF4444]">{t("sim.tooltip.dead")}</div>}
    </div>
  );
}
