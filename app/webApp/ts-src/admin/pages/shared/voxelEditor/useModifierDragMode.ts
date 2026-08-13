import { useEffect, useState } from "react";
import { dragMode } from "../../../../primitives/DragMode";

// Tracks which camera-drag modifier is currently held, to highlight the matching badge in the
// legend overlay — mirrors the shiftKey/metaKey/altKey precedence the pointer handler uses.
// Shared by the Instance and Scene editors.
export function useModifierDragMode() {
  const [modKeys, setModKeys] = useState({ shift: false, meta: false, alt: false, ctrl: false });
  useEffect(() => {
    const update = (e: KeyboardEvent) =>
      setModKeys({ shift: e.shiftKey, meta: e.metaKey, alt: e.altKey, ctrl: e.ctrlKey });
    const reset = () => setModKeys({ shift: false, meta: false, alt: false, ctrl: false });
    window.addEventListener("keydown", update);
    window.addEventListener("keyup", update);
    window.addEventListener("blur", reset);
    return () => {
      window.removeEventListener("keydown", update);
      window.removeEventListener("keyup", update);
      window.removeEventListener("blur", reset);
    };
  }, []);
  const activeDragMode: dragMode = modKeys.meta ? "rotate" : modKeys.alt ? "pan" : modKeys.ctrl ? "zoom" : "place";
  return { modKeys, activeDragMode };
}
