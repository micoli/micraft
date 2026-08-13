// Live block-edit channel for collaborative scene/instance editing: the only write path for a
// single block change (see AdminController.registerEditWs on the server — the old PUT
// .../blocks REST endpoints were removed). Sends are fire-and-forget/optimistic (the caller
// already applies the edit locally); onBlock delivers edits broadcast from other admins editing
// the same scene/instance, onError surfaces edits the server rejected (unknown block type,
// coordinate outside bounds).
export type BlockEditKind = "scenes" | "instances";

export interface BlockEditSocket<T> {
  send: (edit: T) => void;
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
): BlockEditSocket<T> {
  const socket = new WebSocket(wsUrl(kind, id));
  socket.onmessage = (event) => {
    let message: { type?: string; message?: string } & Partial<T>;
    try {
      message = JSON.parse(event.data as string);
    } catch {
      return;
    }
    if (message.type === "error") {
      onError(message.message ?? "Edit rejected");
      return;
    }
    onBlock(message as T);
  };
  socket.onerror = () => onError("Connection lost");
  return {
    send: (edit: T) => {
      if (socket.readyState === WebSocket.OPEN) socket.send(JSON.stringify(edit));
    },
    close: () => socket.close(),
  };
}
