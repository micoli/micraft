import { useT } from "../i18n";
import { useCallback, useEffect, useState } from "react";
import { AnimationEntry, animationsFromBbmodel, animDisplayName, animEmoji } from "../../lib/animationHelpers";
import { api } from "../api";
import { BbmodelAnimationViewer } from "../components/BbmodelAnimationViewer";
import { SidebarList } from "./SidebarList";
import { PropRow } from "../PropRow";
import { EmptyDetail } from "./EmptyDetail";

export function AnimationsTab() {
  const t = useT();
  const [skins, setSkins] = useState<string[]>([]);
  const [selectedSkin, setSelectedSkin] = useState("articulated");
  const [bbmodel, setBbmodel] = useState<BbModel | null>(null);
  const [selectedAnim, setSelectedAnim] = useState<AnimationEntry | null>(null);
  const [filter, setFilter] = useState("");

  useEffect(() => {
    api.skins.list().then(setSkins).catch(console.error);
  }, []);

  const loadBbmodel = useCallback((skin: string) => {
    api.skins
      .bbmodel(skin)
      .then(setBbmodel)
      .catch(() => setBbmodel(null));
  }, []);

  useEffect(() => {
    loadBbmodel(selectedSkin);
  }, [selectedSkin, loadBbmodel]);

  const anims = bbmodel ? animationsFromBbmodel(bbmodel) : [];
  const filtered = anims.filter((a) => animDisplayName(a.fullName).toLowerCase().includes(filter.toLowerCase()));

  return (
    <div className="flex h-full overflow-hidden">
      <aside className="w-56 shrink-0 flex flex-col border-r border-[#2E3A4E] overflow-hidden">
        <div className="px-3 py-2 border-b border-[#2E3A4E] space-y-2">
          <select
            className="w-full bg-[#1A222C] border border-[#2E3A4E] text-[#8A99AF] text-xs rounded px-2 py-1 outline-none"
            value={selectedSkin}
            onChange={(e) => {
              setSelectedSkin(e.target.value);
              setSelectedAnim(null);
            }}
          >
            {skins.map((s) => (
              <option key={s} value={s}>
                {s}
              </option>
            ))}
          </select>
          <input
            className="w-full bg-[#1A222C] border border-[#2E3A4E] rounded px-2 py-1 text-xs text-white placeholder-[#8A99AF] outline-none"
            placeholder={t("administration.filter")}
            value={filter}
            onChange={(e) => setFilter(e.target.value)}
          />
        </div>
        <SidebarList
          items={filtered}
          selected={selectedAnim}
          getKey={(a) => a.fullName}
          getLabel={(a) => `${animEmoji(a.fullName)} ${animDisplayName(a.fullName)}`}
          onSelect={setSelectedAnim}
        />
      </aside>
      <div className="flex-1 overflow-auto p-6">
        {selectedAnim ? (
          <div className="flex gap-6">
            <BbmodelAnimationViewer bbmodel={bbmodel} animFullName={selectedAnim.fullName} width={360} height={460} />
            <div className="flex-1 min-w-0">
              <h2 className="text-white font-semibold text-base mb-4">{animDisplayName(selectedAnim.fullName)}</h2>
              <PropRow label={t("administration.skin")} value={selectedSkin} />
              <PropRow label={t("administration.duration")} value={`${selectedAnim.length.toFixed(3)} s`} />
              <PropRow label={t("administration.animatedBones")} value={selectedAnim.boneCount} />
              <PropRow label={t("administration.fullId")} value={selectedAnim.fullName} />
            </div>
          </div>
        ) : (
          <EmptyDetail message={t("administration.selectAnimation")} />
        )}
      </div>
    </div>
  );
}
