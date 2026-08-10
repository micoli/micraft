import { useT } from "../i18n";
import { useEffect, useMemo, useState } from "react";
import { api, NpcTypeDto } from "../api";
import { animationsFromBbmodel, animDisplayName, animEmoji } from "../../lib/animationHelpers";
import { BbmodelAnimationViewer } from "../components/BbmodelAnimationViewer";
import { SidebarList } from "./SidebarList";
import { PropRow } from "../PropRow";
import { EmptyDetail } from "./EmptyDetail";

export function BestiaryTab() {
  const t = useT();
  const [types, setTypes] = useState<Record<string, NpcTypeDto>>({});
  const [selected, setSelected] = useState<{ name: string; dto: NpcTypeDto } | null>(null);
  const [filter, setFilter] = useState("");
  const [bbmodel, setBbmodel] = useState<BbModel | null>(null);

  useEffect(() => {
    api.npcTypes.list().then(setTypes).catch(console.error);
  }, []);

  useEffect(() => {
    if (!selected) {
      setBbmodel(null);
      return;
    }
    const skinName = selected.dto.bbmodelFile.replace(".bbmodel", "");
    api.skins
      .bbmodel(skinName)
      .then(setBbmodel)
      .catch(() => setBbmodel(null));
  }, [selected]);

  const entries = Object.entries(types)
    .filter(([name]) => name.toLowerCase().includes(filter.toLowerCase()))
    .sort(([a], [b]) => a.localeCompare(b))
    .map(([name, dto]) => ({ name, dto }));

  const [selectedAnim, setSelectedAnim] = useState<string | null>(null);
  const anims = useMemo(() => (bbmodel ? animationsFromBbmodel(bbmodel) : []), [bbmodel]);

  useEffect(() => {
    if (anims.length > 0) setSelectedAnim(anims[0].fullName);
    else setSelectedAnim(null);
  }, [anims]);

  return (
    <div className="flex h-full overflow-hidden">
      <aside className="w-56 shrink-0 flex flex-col border-r border-[#2E3A4E] overflow-hidden">
        <div className="px-3 py-2 border-b border-[#2E3A4E]">
          <input
            className="w-full bg-[#1A222C] border border-[#2E3A4E] rounded px-2 py-1 text-xs text-white placeholder-[#8A99AF] outline-none"
            placeholder={t("administration.filter")}
            value={filter}
            onChange={(e) => setFilter(e.target.value)}
          />
        </div>
        <SidebarList
          items={entries}
          selected={selected}
          getKey={(e) => e.name}
          getLabel={(e) => e.name}
          onSelect={setSelected}
        />
      </aside>
      <div className="flex-1 overflow-auto p-6">
        {selected ? (
          <div className="flex gap-6">
            <div>
              <BbmodelAnimationViewer bbmodel={bbmodel} animFullName={selectedAnim ?? ""} width={360} height={460} />
              {anims.length > 0 && (
                <select
                  className="mt-2 w-full bg-[#1A222C] border border-[#2E3A4E] text-[#8A99AF] text-xs rounded px-2 py-1 outline-none"
                  value={selectedAnim ?? ""}
                  onChange={(e) => setSelectedAnim(e.target.value)}
                >
                  {anims.map((a) => (
                    <option key={a.fullName} value={a.fullName}>
                      {animEmoji(a.fullName)} {animDisplayName(a.fullName)}
                    </option>
                  ))}
                </select>
              )}
            </div>
            <div className="flex-1 min-w-0">
              <h2 className="text-white font-semibold text-base mb-4">{selected.name}</h2>
              <PropRow label={t("administration.behavior")} value={selected.dto.behaviorKey} />
              <PropRow label={t("administration.model")} value={selected.dto.bbmodelFile} />
              <PropRow label={t("administration.width")} value={selected.dto.width} />
              <PropRow label={t("administration.height")} value={selected.dto.height} />
              <PropRow label={t("administration.speed")} value={selected.dto.wanderSpeed} />
              <PropRow
                label={t("administration.autoSpawn")}
                value={t(selected.dto.autoSpawn ? "common.yes" : "common.no")}
              />
            </div>
          </div>
        ) : (
          <EmptyDetail message={t("administration.selectNpcType")} />
        )}
      </div>
    </div>
  );
}
