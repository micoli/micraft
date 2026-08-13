import type { MutableRefObject } from "react";

export interface UndoEntryBase {
  x: number;
  y: number;
  z: number;
  type: string;
  state: number;
}

export const MAX_UNDO_ENTRIES = 10;

export function pushCapped<T>(stack: T[], entry: T) {
  stack.push(entry);
  if (stack.length > MAX_UNDO_ENTRIES) stack.shift();
}

// Undo/redo bookkeeping shared by the Instance and Scene editors: an edit pushes the cell's prior
// content onto the undo stack (and clears redo); undo pops it, re-applies it, and captures the
// cell's current content onto the redo stack before doing so, so undo/redo can ping-pong back and
// forth indefinitely. `captureBlock`/`applyEntry` are supplied by the caller since what "apply"
// means differs (instance: edit socket send + chunk reload; scene: edit socket send + direct WASM
// remesh). Both apply optimistically (send is fire-and-forget) — a server-side rejection surfaces
// asynchronously via the edit socket's onError, not through this call.
export function makeUndoRedoController<T extends UndoEntryBase>(
  undoStackRef: MutableRefObject<T[]>,
  redoStackRef: MutableRefObject<T[]>,
  captureBlock: (at: T) => T,
  applyEntry: (entry: T, onSuccess: () => void) => void,
) {
  function pushUndo(entry: T) {
    pushCapped(undoStackRef.current, entry);
    redoStackRef.current = [];
  }

  function performUndo() {
    const entry = undoStackRef.current.pop();
    if (!entry) return;
    const redoEntry = captureBlock(entry);
    applyEntry(entry, () => pushCapped(redoStackRef.current, redoEntry));
  }

  function performRedo() {
    const entry = redoStackRef.current.pop();
    if (!entry) return;
    const undoEntry = captureBlock(entry);
    applyEntry(entry, () => pushCapped(undoStackRef.current, undoEntry));
  }

  return { pushUndo, performUndo, performRedo };
}
