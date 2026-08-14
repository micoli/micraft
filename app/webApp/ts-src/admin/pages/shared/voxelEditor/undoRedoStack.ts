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
//
// Each stack slot is a GROUP of entries rather than a single entry — a bulk selection edit
// (fill/shell/cut in the voxel editor) pushes every touched voxel's prior state as ONE group, so it
// undoes/redoes as a single gesture and still only costs one of the MAX_UNDO_ENTRIES slots, not one
// per voxel. A single place/break edit is just a group of one.
export type UndoGroup<T> = T[];

export function makeUndoRedoController<T extends UndoEntryBase>(
  undoStackRef: MutableRefObject<UndoGroup<T>[]>,
  redoStackRef: MutableRefObject<UndoGroup<T>[]>,
  captureBlock: (at: T) => T,
  applyEntry: (entry: T, onSuccess: () => void) => void,
) {
  function pushUndo(entry: T | UndoGroup<T>) {
    pushCapped(undoStackRef.current, Array.isArray(entry) ? entry : [entry]);
    redoStackRef.current = [];
  }

  // Applies every entry in a group, then pushes the group's pre-apply snapshot onto the other
  // stack once ALL entries have been applied (a completion counter rather than awaiting a promise,
  // since applyEntry's onSuccess fires synchronously today — see the doc comment above).
  function applyGroup(group: UndoGroup<T>, otherStackRef: MutableRefObject<UndoGroup<T>[]>) {
    const inverse = group.map((entry) => captureBlock(entry));
    let remaining = group.length;
    for (const entry of group) {
      applyEntry(entry, () => {
        remaining--;
        if (remaining === 0) pushCapped(otherStackRef.current, inverse);
      });
    }
  }

  function performUndo() {
    const group = undoStackRef.current.pop();
    if (!group) return;
    applyGroup(group, redoStackRef);
  }

  function performRedo() {
    const group = redoStackRef.current.pop();
    if (!group) return;
    applyGroup(group, undoStackRef);
  }

  return { pushUndo, performUndo, performRedo };
}
