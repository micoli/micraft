import { DragMode, type dragMode } from "../../../../primitives/DragMode";

const CAMERA_MODES = [
  { key: "zoom", label: "Zoom", hint: "⌃" },
  { key: "pan", label: "Pan", hint: "⌥" },
  { key: "rotate", label: "Rotate", hint: "⌘" },
] as const;

// Floating over the viewport canvas (not the sidebar) since these are camera-modifier hints tied
// to mouse interaction over the 3D view itself, unlike Place/Select/Clip Pane which are editing
// modes that live alongside the block palette.
export function ViewportCameraHud({ activeDragMode }: { activeDragMode: dragMode }) {
  return (
    <div className="absolute top-2 left-1/2 -translate-x-1/2 flex gap-1.5 pointer-events-none items-center justify-center z-10">
      {CAMERA_MODES.map((m) => (
        <DragMode key={m.key} m={m} activeDragMode={activeDragMode} />
      ))}
    </div>
  );
}
