import { useSimulation } from "./useSimulation";
import { Translate, useT } from "../../i18n";
import { npcColor } from "./types";

export function NpcDetailPanel({ sim }: { sim: ReturnType<typeof useSimulation> }) {
  const t: Translate = useT();
  const detail = sim.detail;
  if (!detail) {
    return <p className="text-[11px] text-[#4A5568]">{t("sim.detail.hint")}</p>;
  }
  const npc = detail.npc;
  const rows: [string, string][] = [
    [t("sim.detail.type"), npc.type],
    [t("sim.detail.level"), t("sim.detail.levelValue", npc.level, detail.xp)],
    [t("sim.detail.hitPoints"), `${npc.currentHp}/${npc.maxHp}`],
    [t("sim.detail.mana"), `${detail.currentMana}/${detail.maxMana}`],
    [t("sim.detail.behavior"), detail.behaviorKey],
    [t("sim.detail.aggro"), t("sim.detail.aggroValue", detail.aggroMode, detail.aggroRange)],
    [t("sim.detail.aggroTarget"), npc.aggroTargetId ?? "—"],
    [t("sim.detail.class"), detail.characterClass],
    [t("sim.detail.speedRadius"), `${detail.wanderSpeed} / ${detail.wanderRadius}`],
    [t("sim.detail.size"), `${detail.width} × ${detail.height}`],
    [t("sim.detail.wanderPhase"), detail.wanderPhase],
    [t("sim.detail.position"), `${npc.x.toFixed(2)}, ${npc.y.toFixed(2)}, ${npc.z.toFixed(2)}`],
    [t("sim.detail.spawnPoint"), `${detail.spawnX.toFixed(1)}, ${detail.spawnZ.toFixed(1)}`],
    [t("sim.detail.attacks"), detail.attacks.join(", ") || "—"],
    [t("sim.detail.spells"), detail.spells.join(", ") || "—"],
    [t("sim.detail.activeEffects"), detail.activeEffects.join(", ") || "—"],
    [t("sim.detail.diet"), detail.diet ?? "—"],
    [t("sim.detail.gender"), npc.gender ?? "—"],
    [t("sim.detail.ageDays"), npc.ageGameDays != null ? npc.ageGameDays.toFixed(2) : "—"],
    [t("sim.detail.hunger"), npc.hunger != null ? `${Math.round(npc.hunger * 100)}%` : "—"],
    [
      t("sim.detail.gestation"),
      npc.gestationRemainingDays != null ? t("sim.detail.gestationValue", npc.gestationRemainingDays.toFixed(2)) : "—",
    ],
    [t("sim.detail.preyTarget"), detail.preyTargetId ?? "—"],
    [t("sim.detail.mateTarget"), detail.mateTargetId ?? "—"],
    [
      t("sim.detail.pack"),
      npc.packId
        ? t(detail.packEngaged ? "sim.detail.packEngaged" : "sim.detail.packRallying", String(detail.packSize ?? 1))
        : "—",
    ],
    [t("sim.detail.npcTarget"), npc.npcTargetId?.slice(0, 8) ?? "—"],
    [t("sim.detail.parents"), detail.parentIds.length ? detail.parentIds.map((p) => p.slice(0, 8)).join(", ") : "—"],
  ];

  return (
    <div className="flex h-full flex-col">
      <div className="mb-2 flex items-center gap-2">
        <span className="inline-block h-3 w-3 rounded-full" style={{ background: npcColor(npc.type) }} />
        <span className="flex-1 truncate text-[12px] font-semibold text-white">{npc.name}</span>
        <button
          type="button"
          onClick={() => sim.inspect(npc.id)}
          className="rounded bg-[#2E3A4E] px-2 py-0.5 text-[10px] text-[#C7D2FE] hover:bg-[#3C50E0]/60"
        >
          {t("common.refresh")}
        </button>
      </div>
      <div className="flex-1 overflow-auto rounded border border-[#2E3A4E] bg-[#0E1726] p-2">
        {rows.map(([label, value]) => (
          <div key={label} className="flex gap-2 border-b border-[#1A222C] py-1 text-[11px] last:border-0">
            <span className="w-40 shrink-0 text-[#8A99AF]">{label}</span>
            <span className="break-all text-white">{value}</span>
          </div>
        ))}
        {detail.baseStats && (
          <div className="mt-2 text-[11px]">
            <p className="mb-1 text-[#8A99AF]">{t("sim.detail.baseStats")}</p>
            <div className="flex flex-wrap gap-2">
              {Object.entries(detail.baseStats).map(([stat, value]) => (
                <span key={stat} className="rounded bg-[#1A222C] px-1.5 py-0.5 text-white">
                  {stat} {value}
                </span>
              ))}
            </div>
          </div>
        )}
      </div>
    </div>
  );
}
