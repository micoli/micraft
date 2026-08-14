import { useEffect, useRef, useState, type Dispatch, type SetStateAction } from "react";
import { CLIP_AXES, ClipAxis, ClipPlaneState } from "./clipAxis";
import { ClipAxesInput } from "../../../../primitives/clipAxesInput";

const PANEL_POS_STORAGE_KEY = "voxelEditorClipPlanesPanelPos";
const PANEL_DEFAULT_POS = { top: 48, left: 280 };

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

// Floating, draggable window holding the X/Y/Z clip-plane sliders. Position persists across
// reloads in localStorage — the gizmo/canvas underneath stays fully interactive since the panel
// only occupies its own bounding box.
export function VoxelClipPlanesFloatingPanel({
  clipPlanes,
  clipBounds,
  setClipPlanes,
}: {
  clipPlanes: Record<ClipAxis, ClipPlaneState>;
  clipBounds: Record<ClipAxis, readonly [number, number]>;
  setClipPlanes: Dispatch<SetStateAction<Record<ClipAxis, ClipPlaneState>>>;
}) {
  const [pos, setPos] = useState(loadPanelPos);
  const dragStateRef = useRef<{ startX: number; startY: number; startTop: number; startLeft: number } | null>(null);

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

  return (
    <div
      className="fixed z-20 flex flex-col rounded-lg border border-[#2E3A4E] bg-[#0B1220]/95 shadow-lg backdrop-blur-sm w-48"
      style={{ top: pos.top, left: pos.left }}
    >
      <div
        onMouseDown={handleDragStart}
        className="shrink-0 flex items-center justify-center px-2 py-1 border-b border-[#2E3A4E] cursor-move rounded-t-lg bg-black/30"
      >
        <span className="text-[9px] text-[#8A99AF] font-medium">Clip planes</span>
      </div>
      <div className="flex flex-col gap-1.5 p-2">
        {CLIP_AXES.map((axis) => (
          <ClipAxesInput
            key={axis}
            axis={axis}
            clipPlanes={clipPlanes}
            clipBounds={clipBounds}
            setClipPlanes={setClipPlanes}
          />
        ))}
      </div>
    </div>
  );
}
