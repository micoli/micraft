import { useCallback, useEffect, useMemo, useRef, useState } from "react";
import { getApiSkins } from "../../generated/api/requests";
import { useBlockDefsReady, useBlockPreviews } from "../shared/BlockPreview";
import { animDisplayName, animationsFromBbmodel } from "../../lib/animationHelpers";
import type { AnimationEntry } from "../../lib/animationHelpers";
import { BlockCard } from "./BlockCard";
import { ItemCard } from "./ItemCard";
import { NpcCard } from "./NpcCard";
import { VehicleCard } from "./VehicleCard";
import { SkinCard } from "./SkinCard";
import { AnimationCard } from "./AnimationCard";
import { BlockDetail } from "./BlockDetail";
import { ItemDetail } from "./ItemDetail";
import { NpcDetail } from "./NpcDetail";
import { VehicleDetail } from "./VehicleDetail";
import { SkinDetail } from "./SkinDetail";
import { AnimationDetail } from "./AnimationDetail";

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
type Selection =
  | { kind: "block"; ordinal: number }
  | { kind: "item"; name: string }
  | { kind: "npc"; npcType: string }
  | { kind: "vehicle"; vehicleType: string }
  | { kind: "skin"; name: string }
  | { kind: "animation"; fullName: string };

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

  const filteredBlocks = allBlocks.filter((b) => b.name.toLowerCase().includes(filter.toLowerCase()));
  const filteredItems = allItems.filter((it) => it.name.toLowerCase().includes(filter.toLowerCase()));
  const filteredNpcs = allNpcs.filter((n) => n.type.toLowerCase().includes(filter.toLowerCase()));
  const filteredVehicles = allVehicles.filter((v) => v.type.toLowerCase().includes(filter.toLowerCase()));
  const filteredSkins = allSkins.filter((s) => s.toLowerCase().includes(filter.toLowerCase()));
  const filteredAnimations = allAnimations.filter((a) =>
    animDisplayName(a.fullName).toLowerCase().includes(filter.toLowerCase()),
  );

  const currentList: (BlockEntry | ItemEntry | NpcEntry | VehicleEntry | string | AnimationEntry)[] =
    tab === "bestiary"
      ? filteredNpcs
      : tab === "vehicles"
        ? filteredVehicles
        : tab === "blocks"
          ? filteredBlocks
          : tab === "items"
            ? filteredItems
            : tab === "skins"
              ? filteredSkins
              : filteredAnimations;

  const currentIdx =
    selection === null
      ? -1
      : tab === "bestiary"
        ? filteredNpcs.findIndex((n) => n.type === (selection as { kind: "npc"; npcType: string }).npcType)
        : tab === "vehicles"
          ? filteredVehicles.findIndex(
              (v) => v.type === (selection as { kind: "vehicle"; vehicleType: string }).vehicleType,
            )
          : tab === "blocks"
            ? filteredBlocks.findIndex((b) => b.ordinal === (selection as { kind: "block"; ordinal: number }).ordinal)
            : tab === "items"
              ? filteredItems.findIndex((it) => it.name === (selection as { kind: "item"; name: string }).name)
              : tab === "skins"
                ? filteredSkins.findIndex((s) => s === (selection as { kind: "skin"; name: string }).name)
                : filteredAnimations.findIndex(
                    (a) => a.fullName === (selection as { kind: "animation"; fullName: string }).fullName,
                  );

  useEffect(() => {
    if (!open) return;

    const selectIdx = (idx: number) => {
      if (idx < 0 || idx >= currentList.length) return;
      const item = currentList[idx];
      if (tab === "bestiary") setSelection({ kind: "npc", npcType: (item as NpcEntry).type });
      else if (tab === "vehicles") setSelection({ kind: "vehicle", vehicleType: (item as VehicleEntry).type });
      else if (tab === "blocks") setSelection({ kind: "block", ordinal: (item as BlockEntry).ordinal });
      else if (tab === "items") setSelection({ kind: "item", name: (item as ItemEntry).name });
      else if (tab === "skins") setSelection({ kind: "skin", name: item as string });
      else setSelection({ kind: "animation", fullName: (item as AnimationEntry).fullName });
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
  }, [open, tab, currentIdx, currentList, onClose]);

  useEffect(() => {
    if (!open || currentIdx < 0) return;
    const item = currentList[currentIdx];
    if (!item) return;
    const key =
      tab === "bestiary"
        ? (item as NpcEntry).type
        : tab === "vehicles"
          ? (item as VehicleEntry).type
          : tab === "blocks"
            ? (item as BlockEntry).ordinal
            : tab === "items"
              ? (item as ItemEntry).name
              : tab === "animations"
                ? (item as AnimationEntry).fullName
                : (item as string);
    itemRefsMap.current.get(key)?.scrollIntoView({ block: "nearest" });
    // eslint-disable-next-line react-hooks/exhaustive-deps -- currentList derives from state already captured by currentIdx/tab
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

  const grid: React.CSSProperties = {
    flex: 1,
    overflowY: "auto",
    padding: "8px 12px 12px",
    display: "flex",
    flexWrap: "wrap",
    alignContent: "flex-start",
    gap: 4,
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
            <div style={{ padding: "8px 12px 4px", flexShrink: 0 }}>
              <input
                type="text"
                placeholder="Filtrer…"
                value={filter}
                onChange={(e) => setFilter(e.target.value)}
                style={{
                  width: "100%",
                  boxSizing: "border-box",
                  background: "#1e1e1e",
                  border: "1px solid #3a3a3a",
                  borderRadius: 5,
                  color: "#ddd",
                  fontFamily: "monospace",
                  fontSize: 12,
                  padding: "5px 10px",
                  outline: "none",
                }}
              />
            </div>
            {tab === "animations" && allSkins.length > 1 && (
              <div style={{ padding: "4px 12px 4px", flexShrink: 0, display: "flex", gap: 4, flexWrap: "wrap" }}>
                {allSkins.map((s) => (
                  <button
                    key={s}
                    onClick={() => {
                      setSelectedAnimSkin(s);
                      setSelection(null);
                    }}
                    style={{
                      background: selectedAnimSkin === s ? "#2a3a2a" : "#1e1e1e",
                      border: `1px solid ${selectedAnimSkin === s ? "#7aac7a" : "#3a3a3a"}`,
                      borderRadius: 4,
                      color: selectedAnimSkin === s ? "#7aac7a" : "#888",
                      fontFamily: "monospace",
                      fontSize: 11,
                      padding: "3px 8px",
                      cursor: "pointer",
                    }}
                  >
                    {s}
                  </button>
                ))}
              </div>
            )}
            <div ref={gridRef} style={grid}>
              {tab === "bestiary" &&
                filteredNpcs.map((npc) => (
                  <NpcCard
                    key={npc.type}
                    ref={(el) => {
                      itemRefsMap.current.set(npc.type, el);
                    }}
                    npc={npc}
                    selected={selection?.kind === "npc" && selection.npcType === npc.type}
                    onClick={() => setSelection({ kind: "npc", npcType: npc.type })}
                  />
                ))}
              {tab === "vehicles" &&
                filteredVehicles.map((vehicle) => (
                  <VehicleCard
                    key={vehicle.type}
                    ref={(el) => {
                      itemRefsMap.current.set(vehicle.type, el);
                    }}
                    vehicle={vehicle}
                    selected={selection?.kind === "vehicle" && selection.vehicleType === vehicle.type}
                    onClick={() => setSelection({ kind: "vehicle", vehicleType: vehicle.type })}
                  />
                ))}
              {tab === "blocks" &&
                filteredBlocks.map((block) => (
                  <BlockCard
                    key={block.name}
                    ref={(el) => {
                      itemRefsMap.current.set(block.ordinal, el);
                    }}
                    block={block}
                    defsReady={defsReady}
                    getPreview={getPreview}
                    selected={selection?.kind === "block" && selection.ordinal === block.ordinal}
                    onClick={() => setSelection({ kind: "block", ordinal: block.ordinal })}
                  />
                ))}
              {tab === "items" &&
                filteredItems.map((item) => (
                  <ItemCard
                    key={item.name}
                    ref={(el) => {
                      itemRefsMap.current.set(item.name, el);
                    }}
                    item={item}
                    blocks={allBlocks}
                    defsReady={defsReady}
                    getPreview={getPreview}
                    selected={selection?.kind === "item" && selection.name === item.name}
                    onClick={() => setSelection({ kind: "item", name: item.name })}
                  />
                ))}
              {tab === "skins" &&
                filteredSkins.map((skin) => (
                  <SkinCard
                    key={skin}
                    ref={(el) => {
                      itemRefsMap.current.set(skin, el);
                    }}
                    name={skin}
                    selected={selection?.kind === "skin" && selection.name === skin}
                    onClick={() => setSelection({ kind: "skin", name: skin })}
                  />
                ))}
              {tab === "animations" &&
                filteredAnimations.map((anim) => (
                  <AnimationCard
                    key={anim.fullName}
                    ref={(el) => {
                      itemRefsMap.current.set(anim.fullName, el);
                    }}
                    anim={anim}
                    selected={selection?.kind === "animation" && selection.fullName === anim.fullName}
                    onClick={() => setSelection({ kind: "animation", fullName: anim.fullName })}
                  />
                ))}
            </div>
          </div>

          <div style={detail}>
            {!selection && (
              <span style={{ color: "#444", fontSize: 12, textAlign: "center", padding: "0 12px" }}>
                Sélectionner un élément
              </span>
            )}
            {selection?.kind === "block" &&
              (() => {
                const block = allBlocks.find((b) => b.ordinal === selection.ordinal);
                const giveItemName = block
                  ? (allItems.find((it) => it.placesBlock === block.name)?.name ?? null)
                  : null;
                return block ? <BlockDetail block={block} defsReady={defsReady} giveItemName={giveItemName} /> : null;
              })()}
            {selection?.kind === "item" &&
              (() => {
                const item = allItems.find((it) => it.name === selection.name);
                return item ? <ItemDetail item={item} blocks={allBlocks} defsReady={defsReady} /> : null;
              })()}
            {selection?.kind === "npc" &&
              (() => {
                const npc = allNpcs.find((n) => n.type === selection.npcType);
                return npc ? <NpcDetail npc={npc} /> : null;
              })()}
            {selection?.kind === "vehicle" &&
              (() => {
                const vehicle = allVehicles.find((v) => v.type === selection.vehicleType);
                return vehicle ? <VehicleDetail vehicle={vehicle} /> : null;
              })()}
            {selection?.kind === "skin" && <SkinDetail name={selection.name} />}
            {selection?.kind === "animation" &&
              (() => {
                const anim = allAnimations.find((a) => a.fullName === selection.fullName);
                return anim ? <AnimationDetail anim={anim} skin={selectedAnimSkin} /> : null;
              })()}
          </div>
        </div>
      </div>
    </div>
  );
}
