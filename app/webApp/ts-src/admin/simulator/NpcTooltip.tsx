import { useT } from "../i18n";
import type { SimNpc } from "./types";

export interface HoverTarget {
  npc: SimNpc;
  sx: number;
  sy: number;
}

/** Light hover card, identical in the SVG and canvas views. */
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

/** Bottom-left hint shared by both renderers. */
export function ArenaHint({
  halfSize,
  pxPerBlock,
  renderer,
}: {
  halfSize: number;
  pxPerBlock: number;
  renderer: string;
}) {
  const t = useT();
  return (
    <div className="absolute bottom-2 left-2 rounded bg-[#1A222C]/80 px-2 py-1 text-[10px] text-[#8A99AF]">
      {t("sim.hint", halfSize * 2, halfSize * 2, pxPerBlock.toFixed(1), renderer)}
    </div>
  );
}
