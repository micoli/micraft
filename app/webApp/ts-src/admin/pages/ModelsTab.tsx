import { useT } from "../i18n";
import { useCallback, useEffect, useMemo, useState } from "react";
import { AnimationEntry, animationsFromBbmodel, animDisplayName, animEmoji } from "../../lib/animationHelpers";
import { getApiSkins } from "../../generated/api/requests";
import { BbmodelAnimationViewer } from "../components/BbmodelAnimationViewer";
import { SidebarList } from "./SidebarList";
import { PropRow } from "../PropRow";
import { EmptyDetail } from "../../primitives/EmptyDetail";

type ModelsTabProps = {
  selectedKey: string | null;
  onSelectKey: (key: string | null) => void;
};

function parseSelectedKey(key: string | null): { skin: string; animFullName: string | null } {
  if (!key) return { skin: "articulated", animFullName: null };
  const separatorIndex = key.indexOf(":");
  if (separatorIndex === -1) return { skin: key, animFullName: null };
  return { skin: key.slice(0, separatorIndex), animFullName: key.slice(separatorIndex + 1) };
}

export function ModelsTab({ selectedKey, onSelectKey }: ModelsTabProps) {
  const t = useT();
  const { skin: initialSkin, animFullName: initialAnimFullName } = parseSelectedKey(selectedKey);
  const [skins, setSkins] = useState<string[]>([]);
  const [selectedSkin, setSelectedSkin] = useState(initialSkin);
  const [bbmodel, setBbmodel] = useState<BbModel | null>(null);
  const [selectedAnim, setSelectedAnim] = useState<AnimationEntry | null>(null);
  const [filter, setFilter] = useState("");

  useEffect(() => {
    getApiSkins({ throwOnError: true })
      .then((r) => setSkins(r.data))
      .catch(console.error);
  }, []);

  const loadBbmodel = useCallback((skin: string) => {
    // Not an OpenAPI route (staticFiles mount) — kept as a manual fetch.
    fetch(`/api/models/models/${encodeURIComponent(skin)}/${encodeURIComponent(skin)}.bbmodel`)
      .then((r) => r.json() as Promise<BbModel>)
      .then(setBbmodel)
      .catch(() => setBbmodel(null));
  }, []);

  useEffect(() => {
    loadBbmodel(selectedSkin);
  }, [selectedSkin, loadBbmodel]);

  const anims = useMemo(() => (bbmodel ? animationsFromBbmodel(bbmodel) : []), [bbmodel]);
  const filtered = anims.filter((a) => animDisplayName(a.fullName).toLowerCase().includes(filter.toLowerCase()));

  useEffect(() => {
    if (!initialAnimFullName || selectedAnim) return;
    const match = anims.find((a) => a.fullName === initialAnimFullName);
    if (match) setSelectedAnim(match);
  }, [anims, initialAnimFullName, selectedAnim]);

  const selectAnim = (anim: AnimationEntry | null) => {
    setSelectedAnim(anim);
    onSelectKey(anim ? `${selectedSkin}:${anim.fullName}` : null);
  };

  return (
    <div className="flex h-full overflow-hidden">
      <aside className="w-56 shrink-0 flex flex-col border-r border-[#2E3A4E] overflow-hidden">
        <div className="px-3 py-2 border-b border-[#2E3A4E] space-y-2">
          <select
            className="w-full bg-[#1A222C] border border-[#2E3A4E] text-[#8A99AF] text-xs rounded px-2 py-1 outline-none"
            value={selectedSkin}
            onChange={(e) => {
              setSelectedSkin(e.target.value);
              selectAnim(null);
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
          onSelect={selectAnim}
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
