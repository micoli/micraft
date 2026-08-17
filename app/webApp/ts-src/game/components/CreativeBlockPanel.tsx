import { useEffect, useRef, useState } from "react";
import { CssBlockCube, useBlockDefsReady, useBlockPreviews } from "../shared/BlockPreview";
import { CodexCard } from "./codex/CodexCard";
import type { BlockEntry, ItemEntry } from "./codex/CodexModal";

const PANEL_POS_STORAGE_KEY = "gameCreativePanelPos";
const PANEL_DEFAULT_POS = { top: 48, left: 16 };

function clampPanelPos(pos: { top: number; left: number }): { top: number; left: number } {
  const maxLeft = Math.max(0, window.innerWidth - 40);
  const maxTop = Math.max(0, window.innerHeight - 40);
  return { top: Math.min(maxTop, Math.max(0, pos.top)), left: Math.min(maxLeft, Math.max(0, pos.left)) };
}

function loadPanelPos(): { top: number; left: number } {
  try {
    const stored = JSON.parse(localStorage.getItem(PANEL_POS_STORAGE_KEY) ?? "null");
    if (stored && Number.isFinite(stored.top) && Number.isFinite(stored.left)) return clampPanelPos(stored);
  } catch {
    // fall through to default
  }
  return PANEL_DEFAULT_POS;
}

interface Props {
  visible: boolean;
  selectedItem: string | null;
  onSelectItem: (itemName: string) => void;
}

// Floating, draggable palette of every buildable item — used by creative mode (see
// game/lib/creativeMode.ts) for unlimited block placement, independent of the real inventory.
export function CreativeBlockPanel({ visible, selectedItem, onSelectItem }: Props) {
  const [pos, setPos] = useState(loadPanelPos);
  const dragStateRef = useRef<{ startX: number; startY: number; startTop: number; startLeft: number } | null>(null);
  const defsReady = useBlockDefsReady();
  const getPreview = useBlockPreviews();

  useEffect(() => {
    localStorage.setItem(PANEL_POS_STORAGE_KEY, JSON.stringify(pos));
  }, [pos]);

  useEffect(() => {
    function onMouseMove(e: MouseEvent) {
      const state = dragStateRef.current;
      if (!state) return;
      setPos(
        clampPanelPos({
          top: state.startTop + (e.clientY - state.startY),
          left: state.startLeft + (e.clientX - state.startX),
        }),
      );
    }
    function onMouseUp() {
      dragStateRef.current = null;
      document.body.style.userSelect = "";
    }
    window.addEventListener("mousemove", onMouseMove);
    window.addEventListener("mouseup", onMouseUp);
    return () => {
      window.removeEventListener("mousemove", onMouseMove);
      window.removeEventListener("mouseup", onMouseUp);
    };
  }, []);

  function handleDragStart(e: React.MouseEvent) {
    e.preventDefault();
    dragStateRef.current = { startX: e.clientX, startY: e.clientY, startTop: pos.top, startLeft: pos.left };
    document.body.style.userSelect = "none";
  }

  if (!visible) return null;

  const blocks: BlockEntry[] = (window.mcState.codexBlocks ?? []).map(
    (b: Omit<BlockEntry, "ordinal">, i: number) => ({ ...b, ordinal: i }) as BlockEntry,
  );
  const items: ItemEntry[] = Object.entries(window.mcState.codexItems ?? {})
    .map(([name, info]: [string, unknown]) => ({ name, ...(info as Omit<ItemEntry, "name">) }))
    .filter((it: ItemEntry) => it.buildable)
    .sort((a: ItemEntry, b: ItemEntry) => a.name.localeCompare(b.name));

  return (
    <div
      className="fixed z-[1000] flex flex-col rounded-lg border border-[#2E3A4E] bg-[#0B1220]/95 shadow-lg backdrop-blur-sm w-64"
      style={{ top: pos.top, left: pos.left }}
    >
      <div
        onMouseDown={handleDragStart}
        className="shrink-0 flex items-center justify-center px-2 py-1 border-b border-[#2E3A4E] cursor-move rounded-t-lg bg-black/30"
      >
        <span className="text-[9px] text-[#8A99AF] font-medium">Creative — blocks</span>
      </div>
      <div className="flex flex-wrap gap-1 p-2 overflow-y-auto max-h-[70vh] justify-center">
        {items.map((item) => {
          const linkedBlock = item.placesBlock ? blocks.find((b) => b.name === item.placesBlock) : null;
          const preview = linkedBlock ? getPreview(linkedBlock.ordinal) : null;
          return (
            <CodexCard
              key={item.name}
              selected={selectedItem === item.name}
              onClick={() => onSelectItem(item.name)}
              title={item.name}
              label={item.name.replace(/_/g, " ")}
              width={80}
              padding="6px 4px"
              gap={2}
              labelFontSize={10}
              thumbnail={
                preview ? (
                  <img
                    alt="preview"
                    src={preview}
                    width={48}
                    height={48}
                    style={{ imageRendering: "pixelated", display: "block" }}
                  />
                ) : linkedBlock && defsReady ? (
                  <CssBlockCube ordinal={linkedBlock.ordinal} size={36} />
                ) : (
                  <div
                    style={{
                      width: 36,
                      height: 36,
                      borderRadius: 4,
                      background: linkedBlock
                        ? `rgb(${linkedBlock.minimapColor[0]},${linkedBlock.minimapColor[1]},${linkedBlock.minimapColor[2]})`
                        : "#6a5acd",
                      display: "flex",
                      alignItems: "center",
                      justifyContent: "center",
                      fontSize: 18,
                    }}
                  >
                    {!linkedBlock ? "✦" : ""}
                  </div>
                )
              }
            />
          );
        })}
      </div>
    </div>
  );
}
