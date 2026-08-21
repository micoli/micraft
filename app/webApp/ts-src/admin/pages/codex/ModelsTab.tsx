import { useT } from "../../i18n";
import { useCallback, useEffect, useMemo, useRef, useState } from "react";
import { AnimationEntry, animationsFromBbmodel, animDisplayName, animEmoji } from "../../../lib/animationHelpers";
import { getApiSkins, getApiWeapons, getApiTools } from "../../../generated/api/requests";
import { BbmodelAnimationViewer } from "../../components/BbmodelAnimationViewer";
import { useWeaponModelsReady } from "../../../game/shared/PlayerModelPreview";
import { SidebarList } from "../SidebarList";
import { PropRow } from "../../PropRow";
import { EmptyDetail } from "../../../primitives/EmptyDetail";
import { RotateSliders } from "./RotateSliders";

type Rotation = { x: number; y: number; z: number };
type HandItemDefinition = { category: string; rotate?: Rotation };
const ZERO_ROTATION: Rotation = { x: 0, y: 0, z: 0 };

type ModelsTabProps = {
  selectedKey: string | null;
  onSelectKey: (key: string | null, options?: { replace?: boolean }) => void;
};

const DEFAULT_ZOOM = 3.0;
const DEFAULT_ANGLE = 0;

function parseSelectedKey(key: string | null): {
  skin: string;
  animFullName: string | null;
  rightHandItem: string | null;
  leftHandItem: string | null;
  paused: boolean;
  zoom: number | undefined;
  angle: number | undefined;
} {
  if (!key) {
    return {
      skin: "articulated",
      animFullName: null,
      rightHandItem: null,
      leftHandItem: null,
      paused: false,
      zoom: undefined,
      angle: undefined,
    };
  }
  const [skin, animFullName, right, left, paused, zoom, angle] = key.split(":");
  return {
    skin,
    animFullName: animFullName ?? null,
    rightHandItem: right || null,
    leftHandItem: left || null,
    paused: paused === "1",
    zoom: zoom ? parseFloat(zoom) : undefined,
    angle: angle ? parseFloat(angle) : undefined,
  };
}

function buildKey(
  skin: string,
  animFullName: string,
  rightHandItem: string | null,
  leftHandItem: string | null,
  paused: boolean,
  zoom: number,
  angle: number,
) {
  return `${skin}:${animFullName}:${rightHandItem ?? ""}:${leftHandItem ?? ""}:${paused ? "1" : "0"}:${zoom.toFixed(3)}:${angle.toFixed(3)}`;
}

export function ModelsTab({ selectedKey, onSelectKey }: ModelsTabProps) {
  const t = useT();
  const {
    skin: initialSkin,
    animFullName: initialAnimFullName,
    rightHandItem: initialRightHandItem,
    leftHandItem: initialLeftHandItem,
    paused: initialPaused,
    zoom: initialZoom,
    angle: initialAngle,
  } = parseSelectedKey(selectedKey);
  const [skins, setSkins] = useState<string[]>([]);
  const [selectedSkin, setSelectedSkin] = useState(initialSkin);
  const [bbmodel, setBbmodel] = useState<BbModel | null>(null);
  const [selectedAnim, setSelectedAnim] = useState<AnimationEntry | null>(null);
  const [filter, setFilter] = useState("");
  const [weapons, setWeapons] = useState<Record<string, HandItemDefinition>>({});
  const [tools, setTools] = useState<Record<string, HandItemDefinition>>({});
  const [rightHandItem, setRightHandItem] = useState<string | null>(initialRightHandItem);
  const [leftHandItem, setLeftHandItem] = useState<string | null>(initialLeftHandItem);
  const [rightRotateOverride, setRightRotateOverride] = useState<Rotation | null>(null);
  const [leftRotateOverride, setLeftRotateOverride] = useState<Rotation | null>(null);
  const [paused, setPaused] = useState(initialPaused);
  const [zoom, setZoom] = useState(initialZoom ?? DEFAULT_ZOOM);
  const [angle, setAngle] = useState(initialAngle ?? DEFAULT_ANGLE);
  const handsReady = useWeaponModelsReady([rightHandItem, leftHandItem]);

  useEffect(() => {
    getApiSkins({ throwOnError: true })
      .then((r) => setSkins(r.data))
      .catch(console.error);
    getApiWeapons({ throwOnError: true })
      .then((r) => setWeapons(r.data as Record<string, HandItemDefinition>))
      .catch(console.error);
    getApiTools({ throwOnError: true })
      .then((r) => setTools(r.data as Record<string, HandItemDefinition>))
      .catch(console.error);
  }, []);

  const sortedWeapons = Object.keys(weapons).sort();
  const sortedTools = Object.keys(tools).sort();
  const handItems = { ...weapons, ...tools };
  const sortedHandItems = [...sortedWeapons, ...sortedTools];

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

  const selectAnim = useCallback(
    (anim: AnimationEntry | null) => {
      setSelectedAnim(anim);
      onSelectKey(
        anim ? buildKey(selectedSkin, anim.fullName, rightHandItem, leftHandItem, paused, zoom, angle) : null,
      );
    },
    [selectedSkin, rightHandItem, leftHandItem, paused, zoom, angle, onSelectKey],
  );

  const hasAutoSelected = useRef(false);
  useEffect(() => {
    if (hasAutoSelected.current || initialAnimFullName || selectedAnim || filtered.length === 0) return;
    hasAutoSelected.current = true;
    selectAnim(filtered[0]);
  }, [filtered, initialAnimFullName, selectedAnim, selectAnim]);

  const setHand = (hand: "right" | "left", name: string | null) => {
    const right = hand === "right" ? name : rightHandItem;
    const left = hand === "left" ? name : leftHandItem;
    setRightHandItem(right);
    setLeftHandItem(left);
    if (hand === "right") setRightRotateOverride(null);
    else setLeftRotateOverride(null);
    if (selectedAnim) onSelectKey(buildKey(selectedSkin, selectedAnim.fullName, right, left, paused, zoom, angle));
  };

  const togglePause = () => {
    const next = !paused;
    setPaused(next);
    if (selectedAnim) {
      onSelectKey(buildKey(selectedSkin, selectedAnim.fullName, rightHandItem, leftHandItem, next, zoom, angle));
    }
  };

  // Wrapped in a ref so the viewer can hold a stable callback identity across renders while
  // always persisting the latest skin/anim/hand/paused state alongside the new camera values.
  const onCameraChangeRef = useRef<(zoom: number, angle: number) => void>(() => {});
  useEffect(() => {
    onCameraChangeRef.current = (newZoom, newAngle) => {
      setZoom(newZoom);
      setAngle(newAngle);
      if (selectedAnim) {
        onSelectKey(
          buildKey(selectedSkin, selectedAnim.fullName, rightHandItem, leftHandItem, paused, newZoom, newAngle),
          { replace: true },
        );
      }
    };
  });
  const handleCameraChange = useCallback((z: number, a: number) => onCameraChangeRef.current(z, a), []);

  const baseRotate = (name: string | null): Rotation => (name && handItems[name]?.rotate) || ZERO_ROTATION;

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
            <div className="flex flex-col items-center gap-2">
              <BbmodelAnimationViewer
                bbmodel={bbmodel}
                animFullName={selectedAnim.fullName}
                paused={paused}
                initialZoom={initialZoom ?? DEFAULT_ZOOM}
                initialAngle={initialAngle}
                onCameraChange={handleCameraChange}
                rightHandItem={handsReady ? rightHandItem : null}
                leftHandItem={handsReady ? leftHandItem : null}
                rightHandRotate={handsReady ? rightRotateOverride : null}
                leftHandRotate={handsReady ? leftRotateOverride : null}
                width={360}
                height={460}
              />
              <button
                type="button"
                onClick={togglePause}
                className="px-3 py-1 rounded text-xs font-mono border border-[#2E3A4E] text-[#8A99AF] hover:border-[#3C50E0] hover:text-white"
              >
                {paused ? "▶ Play" : "⏸ Pause"}
              </button>
            </div>
            <div className="flex-1 min-w-0">
              <h2 className="text-white font-semibold text-base mb-4">{animDisplayName(selectedAnim.fullName)}</h2>
              <PropRow label={t("administration.skin")} value={selectedSkin} />
              <PropRow label={t("administration.duration")} value={`${selectedAnim.length.toFixed(3)} s`} />
              <PropRow label={t("administration.animatedBones")} value={selectedAnim.boneCount} />
              <PropRow label={t("administration.fullId")} value={selectedAnim.fullName} />
              {sortedHandItems.length > 0 && (
                <div className="mt-4 pt-3 border-t border-[#2E3A4E]">
                  <div className="text-xs text-[#8A99AF] mb-2 tracking-widest">HANDS</div>
                  {(
                    [
                      {
                        hand: "right" as const,
                        value: rightHandItem,
                        override: rightRotateOverride,
                        setOverride: setRightRotateOverride,
                      },
                      {
                        hand: "left" as const,
                        value: leftHandItem,
                        override: leftRotateOverride,
                        setOverride: setLeftRotateOverride,
                      },
                    ] as const
                  ).map(({ hand, value, override, setOverride }) => (
                    <div key={hand} className="mb-3">
                      <div className="flex items-center gap-2">
                        <span className="text-xs text-[#8A99AF] w-16 capitalize">{hand} hand</span>
                        <select
                          className="flex-1 bg-[#1A222C] border border-[#2E3A4E] rounded text-xs px-2 py-1 text-white"
                          value={value ?? ""}
                          onChange={(e) => setHand(hand, e.target.value || null)}
                        >
                          <option value="">(empty)</option>
                          {sortedWeapons.length > 0 && (
                            <optgroup label="Weapons">
                              {sortedWeapons.map((name) => (
                                <option key={name} value={name}>
                                  {name} — {handItems[name].category}
                                </option>
                              ))}
                            </optgroup>
                          )}
                          {sortedTools.length > 0 && (
                            <optgroup label="Tools">
                              {sortedTools.map((name) => (
                                <option key={name} value={name}>
                                  {name} — {handItems[name].category}
                                </option>
                              ))}
                            </optgroup>
                          )}
                        </select>
                      </div>
                      {value && (
                        <RotateSliders
                          label={hand}
                          value={override ?? baseRotate(value)}
                          overridden={override !== null}
                          onChange={(axis, axisValue) =>
                            setOverride({ ...(override ?? baseRotate(value)), [axis]: axisValue })
                          }
                          onReset={() => setOverride(null)}
                        />
                      )}
                    </div>
                  ))}
                </div>
              )}
            </div>
          </div>
        ) : (
          <EmptyDetail message={t("administration.selectAnimation")} />
        )}
      </div>
    </div>
  );
}
