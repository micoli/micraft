import { useState, useEffect, useCallback, useMemo } from "react";
import { api, type BlockInfoDto, type NpcTypeDto, type ItemDto } from "../api";
import { BbmodelAnimationViewer } from "../components/BbmodelAnimationViewer";
import { animDisplayName, animEmoji, animationsFromBbmodel, type AnimationEntry } from "../../lib/animationHelpers";

type AdminTab = "blocks" | "items" | "bestiary" | "skins" | "animations";

const TAB_LABELS: Record<AdminTab, string> = {
  blocks: "Blocs",
  items: "Items",
  bestiary: "Bestiaire",
  skins: "Skins",
  animations: "Animations",
};

// ── Shared list sidebar ───────────────────────────────────────────────────────
function SidebarList<T>({
  items,
  selected,
  getKey,
  getLabel,
  onSelect,
}: {
  items: T[];
  selected: T | null;
  getKey: (item: T) => string;
  getLabel: (item: T) => string;
  onSelect: (item: T) => void;
}) {
  return (
    <div className="flex-1 overflow-y-auto py-2">
      {items.map((item) => {
        const key = getKey(item);
        const isSelected = selected ? getKey(selected) === key : false;
        return (
          <button
            key={key}
            onClick={() => onSelect(item)}
            className={`w-full text-left px-4 py-2 text-sm truncate transition-colors ${
              isSelected ? "bg-[#3C50E0]/20 text-white" : "text-[#8A99AF] hover:text-white hover:bg-[#2E3A4E]"
            }`}
          >
            {getLabel(item)}
          </button>
        );
      })}
    </div>
  );
}

function EmptyDetail({ message }: { message: string }) {
  return <div className="flex-1 flex items-center justify-center text-[#8A99AF] text-sm">{message}</div>;
}

function PropRow({ label, value }: { label: string; value: string | number | boolean }) {
  return (
    <div className="flex justify-between py-2 border-b border-[#2E3A4E] text-sm">
      <span className="text-[#8A99AF]">{label}</span>
      <span className="text-white font-mono">{String(value)}</span>
    </div>
  );
}

// ── Blocks tab ────────────────────────────────────────────────────────────────
function BlocksTab() {
  const [blocks, setBlocks] = useState<BlockInfoDto[]>([]);
  const [selected, setSelected] = useState<BlockInfoDto | null>(null);
  const [filter, setFilter] = useState("");

  useEffect(() => {
    api.blocks
      .list()
      .then((b) => setBlocks(b.filter((x) => x.name !== "AIR")))
      .catch(console.error);
  }, []);

  const filtered = blocks
    .filter((b) => b.name.toLowerCase().includes(filter.toLowerCase()))
    .sort((a, b) => a.name.localeCompare(b.name));

  return (
    <div className="flex h-full overflow-hidden">
      <aside className="w-56 shrink-0 flex flex-col border-r border-[#2E3A4E] overflow-hidden">
        <div className="px-3 py-2 border-b border-[#2E3A4E]">
          <input
            className="w-full bg-[#1A222C] border border-[#2E3A4E] rounded px-2 py-1 text-xs text-white placeholder-[#8A99AF] outline-none"
            placeholder="Filtrer…"
            value={filter}
            onChange={(e) => setFilter(e.target.value)}
          />
        </div>
        <SidebarList
          items={filtered}
          selected={selected}
          getKey={(b) => b.name}
          getLabel={(b) => b.name.replace(/_/g, " ")}
          onSelect={setSelected}
        />
      </aside>
      <div className="flex-1 overflow-auto p-6">
        {selected ? (
          <div className="max-w-sm">
            <div className="w-12 h-12 rounded mb-4" style={{ background: `rgb(${selected.minimapColor.join(",")})` }} />
            <h2 className="text-white font-semibold text-base mb-4">{selected.name.replace(/_/g, " ")}</h2>
            <PropRow label="Dureté" value={selected.hardness === -1 ? "∞" : selected.hardness} />
            <PropRow label="Solide" value={selected.solid ? "oui" : "non"} />
            <PropRow label="Transparent" value={selected.transparent ? "oui" : "non"} />
            <PropRow label="Liquide" value={selected.liquid ? "oui" : "non"} />
            {selected.modelElement && <PropRow label="Modèle" value={selected.modelElement} />}
          </div>
        ) : (
          <EmptyDetail message="Sélectionner un bloc" />
        )}
      </div>
    </div>
  );
}

// ── Items tab ─────────────────────────────────────────────────────────────────
function ItemsTab() {
  const [items, setItems] = useState<Record<string, ItemDto>>({});
  const [selected, setSelected] = useState<{ name: string; dto: ItemDto } | null>(null);
  const [filter, setFilter] = useState("");

  useEffect(() => {
    api.items.list().then(setItems).catch(console.error);
  }, []);

  const entries = Object.entries(items)
    .filter(([name]) => name.toLowerCase().includes(filter.toLowerCase()))
    .sort(([a], [b]) => a.localeCompare(b))
    .map(([name, dto]) => ({ name, dto }));

  return (
    <div className="flex h-full overflow-hidden">
      <aside className="w-56 shrink-0 flex flex-col border-r border-[#2E3A4E] overflow-hidden">
        <div className="px-3 py-2 border-b border-[#2E3A4E]">
          <input
            className="w-full bg-[#1A222C] border border-[#2E3A4E] rounded px-2 py-1 text-xs text-white placeholder-[#8A99AF] outline-none"
            placeholder="Filtrer…"
            value={filter}
            onChange={(e) => setFilter(e.target.value)}
          />
        </div>
        <SidebarList
          items={entries}
          selected={selected}
          getKey={(e) => e.name}
          getLabel={(e) => e.name.replace(/_/g, " ")}
          onSelect={setSelected}
        />
      </aside>
      <div className="flex-1 overflow-auto p-6">
        {selected ? (
          <div className="max-w-sm">
            <div className="w-12 h-12 rounded mb-4 bg-[#6a5acd] flex items-center justify-center text-2xl">✦</div>
            <h2 className="text-white font-semibold text-base mb-4">{selected.name.replace(/_/g, " ")}</h2>
            <PropRow label="Constructible" value={selected.dto.buildable ? "oui" : "non"} />
            {selected.dto.placesBlock && <PropRow label="Pose le bloc" value={selected.dto.placesBlock} />}
          </div>
        ) : (
          <EmptyDetail message="Sélectionner un item" />
        )}
      </div>
    </div>
  );
}

// ── Bestiary tab ──────────────────────────────────────────────────────────────
function BestiaryTab() {
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
            placeholder="Filtrer…"
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
              <PropRow label="Comportement" value={selected.dto.behaviorKey} />
              <PropRow label="Modèle" value={selected.dto.bbmodelFile} />
              <PropRow label="Largeur" value={selected.dto.width} />
              <PropRow label="Hauteur" value={selected.dto.height} />
              <PropRow label="Vitesse" value={selected.dto.wanderSpeed} />
              <PropRow label="Auto-spawn" value={selected.dto.autoSpawn ? "oui" : "non"} />
            </div>
          </div>
        ) : (
          <EmptyDetail message="Sélectionner un type de NPC" />
        )}
      </div>
    </div>
  );
}

// ── Skins tab ─────────────────────────────────────────────────────────────────
function SkinsTab() {
  const [skins, setSkins] = useState<string[]>([]);
  const [selected, setSelected] = useState<string | null>(null);
  const [bbmodel, setBbmodel] = useState<BbModel | null>(null);
  const [filter, setFilter] = useState("");
  const [selectedAnim, setSelectedAnim] = useState<string | null>(null);

  useEffect(() => {
    api.skins.list().then(setSkins).catch(console.error);
  }, []);

  useEffect(() => {
    if (!selected) {
      setBbmodel(null);
      return;
    }
    api.skins
      .bbmodel(selected)
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
            placeholder="Filtrer…"
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
              <PropRow label="Animations" value={anims.length} />
            </div>
          </div>
        ) : (
          <EmptyDetail message="Sélectionner un skin" />
        )}
      </div>
    </div>
  );
}

// ── Animations tab ────────────────────────────────────────────────────────────
function AnimationsTab() {
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
            placeholder="Filtrer…"
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
              <PropRow label="Skin" value={selectedSkin} />
              <PropRow label="Durée" value={`${selectedAnim.length.toFixed(3)} s`} />
              <PropRow label="Os animés" value={selectedAnim.boneCount} />
              <PropRow label="ID complet" value={selectedAnim.fullName} />
            </div>
          </div>
        ) : (
          <EmptyDetail message="Sélectionner une animation" />
        )}
      </div>
    </div>
  );
}

// ── Page ──────────────────────────────────────────────────────────────────────
export function AdministrationPage() {
  const [tab, setTab] = useState<AdminTab>("blocks");

  return (
    <div className="flex flex-col h-full overflow-hidden -m-6">
      {/* Tab bar */}
      <div className="shrink-0 flex border-b border-[#2E3A4E] px-6 bg-[#1A222C]">
        {(Object.keys(TAB_LABELS) as AdminTab[]).map((t) => (
          <button
            key={t}
            onClick={() => setTab(t)}
            className={`px-4 py-3 text-sm font-medium border-b-2 transition-colors ${
              tab === t ? "border-[#3C50E0] text-white" : "border-transparent text-[#8A99AF] hover:text-white"
            }`}
          >
            {TAB_LABELS[t]}
          </button>
        ))}
      </div>

      {/* Content */}
      <div className="flex-1 overflow-hidden">
        {tab === "blocks" && <BlocksTab />}
        {tab === "items" && <ItemsTab />}
        {tab === "bestiary" && <BestiaryTab />}
        {tab === "skins" && <SkinsTab />}
        {tab === "animations" && <AnimationsTab />}
      </div>
    </div>
  );
}
