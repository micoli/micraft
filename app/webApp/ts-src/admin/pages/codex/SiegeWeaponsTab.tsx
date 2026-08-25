import { useCallback, useEffect, useMemo, useRef, useState } from "react";
import { useT } from "../../i18n";
import { getApiSiegeWeapons } from "../../../generated/api/requests";
import type { OrgMicoliMicraftPlaceableSiegeSiegeWeaponDefinition } from "../../../generated/api/requests";
import { AnimationEntry, animationsFromBbmodel, animDisplayName, animEmoji } from "../../../lib/animationHelpers";
import { SidebarList } from "../SidebarList";
import { PropRow } from "../../PropRow";
import { EmptyDetail } from "../../../primitives/EmptyDetail";
import { BbmodelAnimationViewer } from "../../components/BbmodelAnimationViewer";

type SiegeWeaponsTabProps = {
  selectedKey: string | null;
  onSelectKey: (key: string | null, options?: { replace?: boolean }) => void;
};

type SiegeWeaponEntry = { name: string; def: OrgMicoliMicraftPlaceableSiegeSiegeWeaponDefinition };

function bbmodelUrl(name: string) {
  return `/api/models/siege/weapons/${name}/${name}.bbmodel`;
}

function parseSelectedKey(key: string | null): { weapon: string | null; animFullName: string | null; paused: boolean } {
  if (!key) return { weapon: null, animFullName: null, paused: false };
  const [weapon, animFullName, paused] = key.split(":");
  return { weapon, animFullName: animFullName || null, paused: paused === "1" };
}

function buildKey(weapon: string, animFullName: string, paused: boolean) {
  return `${weapon}:${animFullName}:${paused ? "1" : "0"}`;
}

export function SiegeWeaponsTab({ selectedKey, onSelectKey }: SiegeWeaponsTabProps) {
  const t = useT();
  const {
    weapon: initialWeapon,
    animFullName: initialAnimFullName,
    paused: initialPaused,
  } = parseSelectedKey(selectedKey);
  const [entries, setEntries] = useState<SiegeWeaponEntry[]>([]);
  const [filter, setFilter] = useState("");
  const [bbmodel, setBbmodel] = useState<BbModel | null>(null);
  const [selectedAnim, setSelectedAnim] = useState<AnimationEntry | null>(null);
  const [paused, setPaused] = useState(initialPaused);

  useEffect(() => {
    getApiSiegeWeapons({ throwOnError: true })
      .then((res) => {
        setEntries(Object.entries(res.data).map(([name, def]) => ({ name, def })));
      })
      .catch(console.error);
  }, []);

  const filtered = entries
    .filter((e) => e.name.toLowerCase().includes(filter.toLowerCase()))
    .sort((a, b) => a.name.localeCompare(b.name));

  const selected = initialWeapon ? (entries.find((e) => e.name === initialWeapon) ?? null) : null;

  const hasAutoSelected = useRef(false);
  useEffect(() => {
    if (hasAutoSelected.current || initialWeapon || filtered.length === 0) return;
    hasAutoSelected.current = true;
    onSelectKey(buildKey(filtered[0].name, "still", false));
  }, [filtered, initialWeapon, onSelectKey]);

  useEffect(() => {
    setBbmodel(null);
    setSelectedAnim(null);
    if (!selected) return;
    fetch(bbmodelUrl(selected.name))
      .then((r) => r.json() as Promise<BbModel>)
      .then(setBbmodel)
      .catch(console.error);
  }, [selected]);

  const anims = useMemo(() => (bbmodel ? animationsFromBbmodel(bbmodel) : []), [bbmodel]);

  useEffect(() => {
    if (selectedAnim || anims.length === 0) return;
    const match = initialAnimFullName ? anims.find((a) => a.fullName === initialAnimFullName) : null;
    setSelectedAnim(match ?? anims[0]);
  }, [anims, initialAnimFullName, selectedAnim]);

  const selectWeapon = (name: string) => onSelectKey(buildKey(name, "still", false));

  const selectAnim = useCallback(
    (anim: AnimationEntry) => {
      if (!selected) return;
      setSelectedAnim(anim);
      onSelectKey(buildKey(selected.name, anim.fullName, paused));
    },
    [selected, paused, onSelectKey],
  );

  const selectAnimByName = (fullName: string) => {
    const anim = anims.find((a) => a.fullName === fullName);
    if (anim) selectAnim(anim);
  };

  const togglePause = () => {
    if (!selected || !selectedAnim) return;
    const next = !paused;
    setPaused(next);
    onSelectKey(buildKey(selected.name, selectedAnim.fullName, next), { replace: true });
  };

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
          items={filtered}
          selected={selected}
          getKey={(e) => e.name}
          getLabel={(e) => e.name.replace(/_/g, " ")}
          onSelect={(e) => selectWeapon(e.name)}
        />
      </aside>
      {selected ? (
        <>
          <div className="flex-1 overflow-auto p-6">
            {selectedAnim ? (
              <div className="flex gap-6">
                <div className="flex flex-col items-center gap-2">
                  <BbmodelAnimationViewer
                    bbmodel={bbmodel}
                    animFullName={selectedAnim.fullName}
                    paused={paused}
                    standaloneItem
                    width={280}
                    height={280}
                  />
                  <select
                    className="w-full bg-[#1A222C] border border-[#2E3A4E] rounded px-2 py-1 text-xs text-white outline-none"
                    value={selectedAnim.fullName}
                    onChange={(e) => selectAnimByName(e.target.value)}
                  >
                    {anims.map((a) => (
                      <option key={a.fullName} value={a.fullName}>
                        {animEmoji(a.fullName)} {animDisplayName(a.fullName)}
                      </option>
                    ))}
                  </select>
                  <button
                    type="button"
                    onClick={togglePause}
                    className="px-3 py-1 rounded text-xs font-mono border border-[#2E3A4E] text-[#8A99AF] hover:border-[#3C50E0] hover:text-white"
                  >
                    {paused ? "▶ Play" : "⏸ Pause"}
                  </button>
                </div>
                <div className="max-w-sm flex-1">
                  <h2 className="text-white font-semibold text-base mb-4">{selected.name.replace(/_/g, " ")}</h2>
                  <PropRow label={t("administration.duration")} value={`${selectedAnim.length.toFixed(3)} s`} />
                  <PropRow label={t("administration.animatedBones")} value={selectedAnim.boneCount} />
                  <PropRow label={t("administration.fullId")} value={selectedAnim.fullName} />
                  <div className="mt-4 pt-3 border-t border-[#2E3A4E]">
                    <PropRow
                      label={t("administration.siegeWeaponProjectileType")}
                      value={selected.def.projectileType}
                    />
                    <PropRow label={t("administration.siegeWeaponAmmoItem")} value={selected.def.ammoItem ?? "-"} />
                    <PropRow label={t("administration.siegeWeaponLaunchPower")} value={selected.def.launchPower} />
                    <PropRow label={t("administration.siegeWeaponLaunchPitch")} value={selected.def.launchPitchDeg} />
                    <PropRow label={t("administration.siegeWeaponImpactRadius")} value={selected.def.impactRadius} />
                    <PropRow label={t("administration.siegeWeaponImpactDamage")} value={selected.def.impactDamage} />
                    <PropRow label={t("administration.siegeWeaponCooldown")} value={selected.def.cooldownMs} />
                    <PropRow
                      label={t("administration.siegeWeaponPitchStepRange")}
                      value={selected.def.pitchStepRange}
                    />
                    <PropRow
                      label={t("administration.siegeWeaponPowerStepRange")}
                      value={selected.def.powerStepRange}
                    />
                  </div>
                </div>
              </div>
            ) : (
              <EmptyDetail message={t("administration.selectAnimation")} />
            )}
          </div>
        </>
      ) : (
        <div className="flex-1 overflow-auto p-6">
          <EmptyDetail message={t("administration.selectItem")} />
        </div>
      )}
    </div>
  );
}
