import { useCallback, useEffect, useMemo, useRef, useState } from "react";
import { getApiSkins } from "../../../generated/api/requests";
import { useBlockDefsReady, useBlockPreviews } from "../../shared/BlockPreview";
import { animationsFromBbmodel } from "../../../lib/animationHelpers";
import type { AnimationEntry } from "../../../lib/animationHelpers";
import { BlockList, BlockDetail, filterBlocks } from "./BlockList";
import { ItemList, ItemDetail, filterItems } from "./ItemList";
import { NpcList, NpcDetail, filterNpcs } from "./NpcList";
import { VehicleList, VehicleDetail, filterVehicles } from "./VehicleList";
import { SkinList, SkinDetail, filterSkins } from "./SkinList";
import { AnimationList, AnimationDetail, filterAnimations } from "./AnimationList";

export interface BlockEntry {
  ordinal: number;
  name: string;
  hardness: number;
  solid: boolean;
  transparent: boolean;
  minimapColor: [number, number, number];
  modelElement: string;
  liquid: boolean;
  plainColorable?: boolean;
}

export interface ItemEntry {
  name: string;
  buildable: boolean;
  placesBlock: string | null;
  plainColor?: string | null;
}

export interface NpcEntry {
  type: string;
  bbmodelFile: string;
  behaviorKey: string;
  width: number;
  height: number;
  wanderSpeed: number;
  autoSpawn: boolean;
}

export interface VehicleEntry {
  type: string;
  bbmodelFile: string;
  width: number;
  height: number;
  speed: number;
}

type CodexTab = "bestiary" | "vehicles" | "blocks" | "items" | "skins" | "animations";
type CodexEntry = BlockEntry | ItemEntry | NpcEntry | VehicleEntry | string | AnimationEntry;
interface Selection {
  kind: CodexTab;
  key: string | number;
}

interface Props {
  open: boolean;
  onClose: () => void;
}

export function CodexModal({ open, onClose }: Props) {
  const [tab, setTab] = useState<CodexTab>("bestiary");
  const [selection, setSelection] = useState<Selection | null>(null);
  const [filter, setFilter] = useState("");
  const [allSkins, setAllSkins] = useState<string[]>([]);
  const [selectedAnimSkin, setSelectedAnimSkin] = useState("player");
  const defsReady = useBlockDefsReady();
  const getPreview = useBlockPreviews();

  const fetchSkins = useCallback(() => {
    getApiSkins({ throwOnError: true })
      .then((r) => setAllSkins(r.data))
      .catch(() => {});
  }, []);

  useEffect(() => {
    if (open && (tab === "skins" || tab === "animations") && allSkins.length === 0) fetchSkins();
  }, [open, tab, allSkins.length, fetchSkins]);
  const gridRef = useRef<HTMLDivElement>(null);
  const itemRefsMap = useRef<Map<string | number, HTMLDivElement | null>>(new Map());

  const allBlocks: BlockEntry[] = (window.mcState.codexBlocks ?? [])
    .map((b: Omit<BlockEntry, "ordinal">, i: number) => ({ ...b, ordinal: i }))
    .filter((b: BlockEntry) => b.name !== "AIR")
    .sort((a: BlockEntry, b: BlockEntry) => a.name.localeCompare(b.name));

  const allItems: ItemEntry[] = Object.entries(window.mcState.codexItems ?? {})
    .map(([name, info]: [string, unknown]) => ({ name, ...(info as Omit<ItemEntry, "name">) }))
    .sort((a: ItemEntry, b: ItemEntry) => a.name.localeCompare(b.name));

  const allNpcs: NpcEntry[] = Object.entries(window.mcState.codexNpcs ?? {})
    .map(([type, info]: [string, unknown]) => ({ type, ...(info as Omit<NpcEntry, "type">) }))
    .sort((a: NpcEntry, b: NpcEntry) => a.type.localeCompare(b.type));

  const allVehicles: VehicleEntry[] = Object.entries(window.mcState.codexVehicles ?? {})
    .map(([type, info]: [string, unknown]) => ({ type, ...(info as Omit<VehicleEntry, "type">) }))
    .sort((a: VehicleEntry, b: VehicleEntry) => a.type.localeCompare(b.type));

  const allAnimations: AnimationEntry[] = useMemo(() => {
    if (!open) return [];
    const bbmodel = window.mcState?.playerBbmodels?.[selectedAnimSkin];
    return animationsFromBbmodel(bbmodel!);
  }, [open, selectedAnimSkin]);

  const filteredBlocks = filterBlocks(allBlocks, filter);
  const filteredItems = filterItems(allItems, filter);
  const filteredNpcs = filterNpcs(allNpcs, filter);
  const filteredVehicles = filterVehicles(allVehicles, filter);
  const filteredSkins = filterSkins(allSkins, filter);
  const filteredAnimations = filterAnimations(allAnimations, filter);

  const TAB_CONFIG: Record<CodexTab, { list: CodexEntry[]; keyOf: (item: CodexEntry) => string | number }> = {
    bestiary: { list: filteredNpcs, keyOf: (i) => (i as NpcEntry).type },
    vehicles: { list: filteredVehicles, keyOf: (i) => (i as VehicleEntry).type },
    blocks: { list: filteredBlocks, keyOf: (i) => (i as BlockEntry).ordinal },
    items: { list: filteredItems, keyOf: (i) => (i as ItemEntry).name },
    skins: { list: filteredSkins, keyOf: (i) => i as string },
    animations: { list: filteredAnimations, keyOf: (i) => (i as AnimationEntry).fullName },
  };
  const { list: currentList, keyOf } = TAB_CONFIG[tab];

  const currentIdx = selection?.kind === tab ? currentList.findIndex((item) => keyOf(item) === selection.key) : -1;

  useEffect(() => {
    if (!open) return;

    const selectIdx = (idx: number) => {
      if (idx < 0 || idx >= currentList.length) return;
      setSelection({ kind: tab, key: keyOf(currentList[idx]) });
    };

    const handler = (e: KeyboardEvent) => {
      if (e.key === "Escape") {
        onClose();
        return;
      }
      if (!["ArrowLeft", "ArrowRight", "ArrowUp", "ArrowDown"].includes(e.key)) return;
      if ((e.target as HTMLElement)?.tagName === "INPUT") return;
      e.preventDefault();

      const cardWidth = tab === "bestiary" || tab === "vehicles" || tab === "skins" ? 98 : 88;
      const cols = gridRef.current ? Math.max(1, Math.floor(gridRef.current.clientWidth / cardWidth)) : 4;

      if (currentIdx === -1) {
        selectIdx(0);
        return;
      }

      let newIdx = currentIdx;
      if (e.key === "ArrowRight") newIdx = Math.min(currentIdx + 1, currentList.length - 1);
      else if (e.key === "ArrowLeft") newIdx = Math.max(currentIdx - 1, 0);
      else if (e.key === "ArrowDown") newIdx = Math.min(currentIdx + cols, currentList.length - 1);
      else if (e.key === "ArrowUp") newIdx = Math.max(currentIdx - cols, 0);

      selectIdx(newIdx);
    };

    window.addEventListener("keydown", handler);
    return () => window.removeEventListener("keydown", handler);
  }, [open, tab, currentIdx, currentList, keyOf, onClose]);

  useEffect(() => {
    if (!open || currentIdx < 0) return;
    const item = currentList[currentIdx];
    if (!item) return;
    itemRefsMap.current.get(keyOf(item))?.scrollIntoView({ block: "nearest" });
    // eslint-disable-next-line react-hooks/exhaustive-deps -- currentList/keyOf derive from state already captured by currentIdx/tab
  }, [open, currentIdx, tab]);

  if (!open) return null;

  const TAB_LABEL: Record<CodexTab, string> = {
    bestiary: `Bestiaire (${allNpcs.length})`,
    vehicles: `Véhicules (${allVehicles.length})`,
    blocks: `Blocs (${allBlocks.length})`,
    items: `Items (${allItems.length})`,
    skins: `Skins (${allSkins.length})`,
    animations: `Animations (${allAnimations.length})`,
  };

  const overlay: React.CSSProperties = {
    position: "fixed",
    inset: 0,
    background: "rgba(0,0,0,0.6)",
    zIndex: 6000,
    display: "flex",
    alignItems: "center",
    justifyContent: "center",
  };

  const modal: React.CSSProperties = {
    background: "#161616",
    border: "2px solid #444",
    borderRadius: 10,
    boxShadow: "0 12px 48px rgba(0,0,0,0.8)",
    width: "min(820px, 92vw)",
    height: "min(580px, 88vh)",
    display: "flex",
    flexDirection: "column",
    overflow: "hidden",
    fontFamily: "monospace",
    color: "#eee",
  };

  const header: React.CSSProperties = {
    display: "flex",
    alignItems: "center",
    justifyContent: "space-between",
    padding: "10px 16px",
    borderBottom: "1px solid #333",
    flexShrink: 0,
  };

  const tabBar: React.CSSProperties = {
    display: "flex",
    gap: 4,
    padding: "8px 12px 0",
    borderBottom: "1px solid #333",
    flexShrink: 0,
  };

  const content: React.CSSProperties = {
    display: "flex",
    flex: 1,
    overflow: "hidden",
  };

  const gridPanel: React.CSSProperties = {
    flex: 1,
    display: "flex",
    flexDirection: "column",
    overflow: "hidden",
  };

  const detail: React.CSSProperties = {
    width: 320,
    flexShrink: 0,
    borderLeft: "1px solid #2a2a2a",
    overflowY: "auto",
    display: "flex",
    flexDirection: "column",
    justifyContent: selection ? "flex-start" : "center",
    alignItems: selection ? "stretch" : "center",
  };

  return (
    <div style={overlay} onClick={onClose}>
      <div style={modal} onClick={(e) => e.stopPropagation()}>
        <div style={header}>
          <span style={{ fontSize: 16, fontWeight: "bold", letterSpacing: 2, color: "#7aac7a" }}>CODEX</span>
          <button
            style={{
              background: "none",
              border: "none",
              color: "#aaa",
              fontSize: 18,
              cursor: "pointer",
              padding: "0 4px",
              lineHeight: 1,
            }}
            onClick={onClose}
          >
            ✕
          </button>
        </div>

        <div style={tabBar}>
          {(["bestiary", "vehicles", "blocks", "items", "skins", "animations"] as CodexTab[]).map((t) => (
            <button
              key={t}
              style={{
                background: tab === t ? "#2a2a2a" : "none",
                border: "none",
                borderBottom: tab === t ? "2px solid #7aac7a" : "2px solid transparent",
                color: tab === t ? "#eee" : "#777",
                cursor: "pointer",
                padding: "6px 14px",
                fontFamily: "monospace",
                fontSize: 12,
                borderRadius: "4px 4px 0 0",
              }}
              onClick={() => {
                setTab(t);
                setSelection(null);
                setFilter("");
              }}
            >
              {TAB_LABEL[t]}
            </button>
          ))}
        </div>

        <div style={content}>
          <div style={gridPanel}>
            {tab === "bestiary" && (
              <NpcList
                npcs={allNpcs}
                filter={filter}
                onFilterChange={setFilter}
                gridRef={gridRef}
                selectedKey={selection?.kind === "bestiary" ? selection.key : undefined}
                onSelect={(key) => setSelection({ kind: "bestiary", key })}
                registerRef={(key, el) => itemRefsMap.current.set(key, el)}
              />
            )}
            {tab === "vehicles" && (
              <VehicleList
                vehicles={allVehicles}
                filter={filter}
                onFilterChange={setFilter}
                gridRef={gridRef}
                selectedKey={selection?.kind === "vehicles" ? selection.key : undefined}
                onSelect={(key) => setSelection({ kind: "vehicles", key })}
                registerRef={(key, el) => itemRefsMap.current.set(key, el)}
              />
            )}
            {tab === "blocks" && (
              <BlockList
                blocks={allBlocks}
                filter={filter}
                onFilterChange={setFilter}
                gridRef={gridRef}
                selectedKey={selection?.kind === "blocks" ? selection.key : undefined}
                onSelect={(key) => setSelection({ kind: "blocks", key })}
                registerRef={(key, el) => itemRefsMap.current.set(key, el)}
                defsReady={defsReady}
                getPreview={getPreview}
              />
            )}
            {tab === "items" && (
              <ItemList
                items={allItems}
                blocks={allBlocks}
                filter={filter}
                onFilterChange={setFilter}
                gridRef={gridRef}
                selectedKey={selection?.kind === "items" ? selection.key : undefined}
                onSelect={(key) => setSelection({ kind: "items", key })}
                registerRef={(key, el) => itemRefsMap.current.set(key, el)}
                defsReady={defsReady}
                getPreview={getPreview}
              />
            )}
            {tab === "skins" && (
              <SkinList
                skins={allSkins}
                filter={filter}
                onFilterChange={setFilter}
                gridRef={gridRef}
                selectedKey={selection?.kind === "skins" ? selection.key : undefined}
                onSelect={(key) => setSelection({ kind: "skins", key })}
                registerRef={(key, el) => itemRefsMap.current.set(key, el)}
              />
            )}
            {tab === "animations" && (
              <AnimationList
                animations={allAnimations}
                filter={filter}
                onFilterChange={setFilter}
                gridRef={gridRef}
                selectedKey={selection?.kind === "animations" ? selection.key : undefined}
                onSelect={(key) => setSelection({ kind: "animations", key })}
                registerRef={(key, el) => itemRefsMap.current.set(key, el)}
                allSkins={allSkins}
                selectedAnimSkin={selectedAnimSkin}
                onSelectAnimSkin={(s) => {
                  setSelectedAnimSkin(s);
                  setSelection(null);
                }}
              />
            )}
          </div>

          <div style={detail}>
            {!selection && (
              <span style={{ color: "#444", fontSize: 12, textAlign: "center", padding: "0 12px" }}>
                Sélectionner un élément
              </span>
            )}
            {selection?.kind === "blocks" &&
              (() => {
                const block = allBlocks.find((b) => b.ordinal === selection.key);
                const giveItemName = block
                  ? (allItems.find((it) => it.placesBlock === block.name)?.name ?? null)
                  : null;
                return block ? <BlockDetail block={block} defsReady={defsReady} giveItemName={giveItemName} /> : null;
              })()}
            {selection?.kind === "items" &&
              (() => {
                const item = allItems.find((it) => it.name === selection.key);
                return item ? <ItemDetail item={item} blocks={allBlocks} defsReady={defsReady} /> : null;
              })()}
            {selection?.kind === "bestiary" &&
              (() => {
                const npc = allNpcs.find((n) => n.type === selection.key);
                return npc ? <NpcDetail npc={npc} /> : null;
              })()}
            {selection?.kind === "vehicles" &&
              (() => {
                const vehicle = allVehicles.find((v) => v.type === selection.key);
                return vehicle ? <VehicleDetail vehicle={vehicle} /> : null;
              })()}
            {selection?.kind === "skins" && <SkinDetail name={selection.key as string} />}
            {selection?.kind === "animations" &&
              (() => {
                const anim = allAnimations.find((a) => a.fullName === selection.key);
                return anim ? <AnimationDetail anim={anim} skin={selectedAnimSkin} /> : null;
              })()}
          </div>
        </div>
      </div>
    </div>
  );
}
