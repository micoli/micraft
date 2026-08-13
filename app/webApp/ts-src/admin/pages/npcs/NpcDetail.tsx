import { NpcAdminDto } from "../../apiTypes";
import type { Translate } from "../../i18n";

export function NpcDetail({ npc, t }: { npc: NpcAdminDto; t: Translate }) {
  const teleport = `/teleport ${Math.round(npc.x)} ${Math.round(npc.y)} ${Math.round(npc.z)}`;
  return (
    <div className="bg-[#0E1726] border-t border-[#2E3A4E] px-6 py-4 grid grid-cols-2 gap-x-8 gap-y-3 text-sm">
      <div>
        <p className="text-[10px] font-semibold uppercase tracking-widest text-[#8A99AF] mb-1">{t("npcs.position")}</p>
        <code className="text-xs text-emerald-400 font-mono select-all">{teleport}</code>
        <p className="text-[10px] text-[#8A99AF] mt-0.5">
          {t("npcs.coords", npc.x.toFixed(1), npc.y.toFixed(1), npc.z.toFixed(1), npc.zone)}
        </p>
      </div>

      {npc.skills.length > 0 && (
        <div>
          <p className="text-[10px] font-semibold uppercase tracking-widest text-[#8A99AF] mb-1">{t("npcs.skills")}</p>
          <div className="flex flex-wrap gap-1">
            {npc.skills.map((s) => (
              <span key={s} className="px-2 py-0.5 rounded bg-[#1C2434] text-[11px] text-[#8A99AF]">
                {s}
              </span>
            ))}
          </div>
        </div>
      )}

      {npc.parentIds.length > 0 && (
        <div>
          <p className="text-[10px] font-semibold uppercase tracking-widest text-[#8A99AF] mb-1">{t("npcs.parents")}</p>
          <div className="flex flex-col gap-0.5">
            {npc.parentIds.map((pid) => (
              <span key={pid} className="text-[11px] font-mono text-[#8A99AF]">
                {pid.slice(0, 8)}…
              </span>
            ))}
          </div>
        </div>
      )}

      {npc.ageGameDays != null && (
        <div className="col-span-2">
          <p className="text-[10px] font-semibold uppercase tracking-widest text-[#8A99AF] mb-2">
            {t("npcs.animalState")}
          </p>
          <div className="grid grid-cols-2 gap-x-8 gap-y-1.5">
            <div className="flex items-center gap-2">
              <span className="text-[10px] text-[#8A99AF] w-24 shrink-0">{t("npcs.age")}</span>
              <span className="text-xs text-white">{t("npcs.gameDays", npc.ageGameDays.toFixed(1))}</span>
            </div>
            {npc.hunger != null && (
              <div className="flex items-center gap-2">
                <span className="text-[10px] text-[#8A99AF] w-24 shrink-0">{t("npcs.hunger")}</span>
                <div className="flex items-center gap-1.5 flex-1">
                  <div className="flex-1 h-1.5 bg-[#2E3A4E] rounded-full overflow-hidden max-w-[80px]">
                    <div
                      className={`h-full rounded-full ${npc.hunger > 0.6 ? "bg-emerald-500" : npc.hunger > 0.3 ? "bg-yellow-500" : "bg-red-500"}`}
                      style={{ width: `${Math.round(npc.hunger * 100)}%` }}
                    />
                  </div>
                  <span className="text-[10px] text-[#8A99AF]">{Math.round(npc.hunger * 100)}%</span>
                </div>
              </div>
            )}
            {npc.motherLevel != null && npc.motherLevel > 0 && (
              <div className="flex items-center gap-2">
                <span className="text-[10px] text-[#8A99AF] w-24 shrink-0">{t("npcs.motherLevel")}</span>
                <span className="text-xs text-white">{npc.motherLevel}</span>
              </div>
            )}
            {npc.gestationRemainingDays != null && (
              <div className="flex items-center gap-2">
                <span className="text-[10px] text-[#8A99AF] w-24 shrink-0">{t("npcs.gestation")}</span>
                <span className="text-xs text-amber-400">
                  {t("npcs.daysLeft", npc.gestationRemainingDays.toFixed(1))}
                </span>
              </div>
            )}
            {npc.lastReproductionDay != null && (
              <div className="flex items-center gap-2">
                <span className="text-[10px] text-[#8A99AF] w-24 shrink-0">{t("npcs.lastRepro")}</span>
                <span className="text-xs text-[#8A99AF]">{t("npcs.dayValue", npc.lastReproductionDay.toFixed(1))}</span>
              </div>
            )}
            {npc.animalStats != null && (
              <div className="col-span-2 mt-1">
                <p className="text-[10px] font-semibold uppercase tracking-widest text-[#8A99AF] mb-1.5">
                  {t("npcs.stats")}
                </p>
                <div className="flex flex-wrap gap-x-4 gap-y-1">
                  {(["str", "dex", "intel", "wis", "con", "cha"] as const).map((k) => (
                    <div key={k} className="flex items-center gap-1">
                      <span className="text-[10px] uppercase text-[#8A99AF] w-8">{k}</span>
                      <span className="text-xs font-mono text-white">{npc.animalStats![k]}</span>
                    </div>
                  ))}
                </div>
              </div>
            )}
          </div>
        </div>
      )}
    </div>
  );
}
