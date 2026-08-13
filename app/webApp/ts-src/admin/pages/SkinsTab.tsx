import { useT } from "../i18n";
import { useEffect, useMemo, useState } from "react";
import { getApiSkins } from "../../generated/api/requests";
import { animationsFromBbmodel, animDisplayName, animEmoji } from "../../lib/animationHelpers";
import { BbmodelAnimationViewer } from "../components/BbmodelAnimationViewer";
import { SidebarList } from "./SidebarList";
import { PropRow } from "../PropRow";
import { EmptyDetail } from "../../primitives/EmptyDetail";

export function SkinsTab() {
  const t = useT();
  const [skins, setSkins] = useState<string[]>([]);
  const [selected, setSelected] = useState<string | null>(null);
  const [bbmodel, setBbmodel] = useState<BbModel | null>(null);
  const [filter, setFilter] = useState("");
  const [selectedAnim, setSelectedAnim] = useState<string | null>(null);

  useEffect(() => {
    getApiSkins({ throwOnError: true })
      .then((r) => setSkins(r.data))
      .catch(console.error);
  }, []);

  useEffect(() => {
    if (!selected) {
      setBbmodel(null);
      return;
    }
    // Not an OpenAPI route (staticFiles mount) — kept as a manual fetch.
    fetch(`/api/models/skins/${encodeURIComponent(selected)}/${encodeURIComponent(selected)}.bbmodel`)
      .then((r) => r.json() as Promise<BbModel>)
      .then(setBbmodel)
      .catch(() => setBbmodel(null));
  }, [selected]);

  const anims = useMemo(() => (bbmodel ? animationsFromBbmodel(bbmodel) : []), [bbmodel]);

  useEffect(() => {
    const walk = anims.find((a) => a.fullName.toLowerCase().includes("walking"));
    setSelectedAnim(walk?.fullName ?? anims[0]?.fullName ?? null);
  }, [anims]);

  const filtered = skins.filter((s) => s.toLowerCase().includes(filter.toLowerCase())).sort();

  return (
    <div className="flex h-full overflow-hidden">
      <aside className="w-48 shrink-0 flex flex-col border-r border-[#2E3A4E] overflow-hidden">
        <div className="px-3 py-2 border-b border-[#2E3A4E]">
          <input
            className="w-full bg-[#1A222C] border border-[#2E3A4E] rounded px-2 py-1 text-xs text-white placeholder-[#8A99AF] outline-none"
            placeholder={t("administration.filter")}
            value={filter}
            onChange={(e) => setFilter(e.target.value)}
          />
        </div>
        <SidebarList
          items={filtered}
          selected={selected}
          getKey={(s) => s}
          getLabel={(s) => s.replace(/_/g, " ")}
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
              <h2 className="text-white font-semibold text-base mb-4">{selected.replace(/_/g, " ")}</h2>
              <PropRow label={t("administration.animations")} value={anims.length} />
            </div>
          </div>
        ) : (
          <EmptyDetail message={t("administration.selectSkin")} />
        )}
      </div>
    </div>
  );
}
