// Live block-edit channel for collaborative scene/instance editing: the only write path for a
// single block change (see AdminController.registerEditWs on the server — the old PUT
// .../blocks REST endpoints were removed). Sends are fire-and-forget/optimistic (the caller
// already applies the edit locally); onBlock delivers edits broadcast from other admins editing
// the same scene/instance, onError surfaces edits the server rejected (unknown block type,
// coordinate outside bounds).
export type BlockEditKind = "scenes" | "instances";

export interface BlockEditSocket<T> {
  send: (edit: T) => void;
  // Batch envelope ({"edits":[...]}) — one WS frame and one server-side broadcast for the whole
  // list, instead of N round-trips. Used by bulk selection edits (fill/shell/cut in the voxel
  // editor); AdminController.registerEditWs decodes it before falling back to a single `T`.
  sendBatch: (edits: T[]) => void;
  // Sends an arbitrary JSON payload rather than a `T` edit — used for out-of-band frames the
  // server decodes with its own DTO (e.g. a rail switch toggle, see InstanceSwitchToggleDto)
  // instead of a full block edit.
  sendRaw: (payload: unknown) => void;
  close: () => void;
}

function wsUrl(kind: BlockEditKind, id: string): string {
  const proto = window.location.protocol === "https:" ? "wss:" : "ws:";
  const token = sessionStorage.getItem("micraft-auth-token");
  const query = token ? `?token=${encodeURIComponent(token)}` : "";
  return `${proto}//${window.location.host}/api/admin/ws/${kind}/${encodeURIComponent(id)}${query}`;
}

export function connectEditSocket<T>(
  kind: BlockEditKind,
  id: string,
  onBlock: (edit: T) => void,
  onError: (message: string) => void,
  // Broadcast of a batch applied by ANOTHER admin tab — kept separate from onBlock so callers can
  // apply it efficiently (e.g. dedupe touched chunks) instead of replaying onBlock per voxel.
  onBatch: (edits: T[]) => void,
): BlockEditSocket<T> {
  const socket = new WebSocket(wsUrl(kind, id));
  socket.onmessage = (event) => {
    let message: { type?: string; message?: string; edits?: T[] } & Partial<T>;
    try {
      message = JSON.parse(event.data as string);
    } catch {
      return;
    }
    if (message.type === "error") {
      onError(message.message ?? "Edit rejected");
      return;
    }
    if (Array.isArray(message.edits)) {
      onBatch(message.edits);
      return;
    }
    onBlock(message as T);
  };
  socket.onerror = () => onError("Connection lost");
  return {
    send: (edit: T) => {
      if (socket.readyState === WebSocket.OPEN) socket.send(JSON.stringify(edit));
    },
    sendBatch: (edits: T[]) => {
      if (socket.readyState === WebSocket.OPEN) socket.send(JSON.stringify({ edits }));
    },
    sendRaw: (payload: unknown) => {
      if (socket.readyState === WebSocket.OPEN) socket.send(JSON.stringify(payload));
    },
    close: () => socket.close(),
  };
}
