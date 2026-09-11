import { decodeHubMessage, encodeHubMessage } from "./hubCodec";

// Mirrors core/.../protocol/ClientMessage.kt's SUPERSEDED_CONNECTION_CLOSE_CODE — a second
// connection for the same player (game or hub) took over this session. Just like the wasm
// GameClient (see 745d9995 "fix(session): stop reconnect ping-pong on second tab connect"), the
// fix is to NOT auto-reconnect on this code: racing the connection that just replaced us is
// exactly the ping-pong bug that commit fixed.
export const SUPERSEDED_CONNECTION_CLOSE_CODE = 4001;

function randomConnectionId(): string {
  return Array.from({ length: 4 }, () => Math.floor(Math.random() * 0xffffffff).toString(16)).join("");
}

export type HubStatus = "connecting" | "open" | "reconnecting" | "superseded" | "closed";

type Listener = (payload: unknown) => void;

export interface HubSocket {
  status: HubStatus;
  send: (name: string, payload: unknown) => void;
  on: (name: string, listener: Listener) => () => void;
  onStatusChange: (listener: (status: HubStatus) => void) => () => void;
  reconnect: () => void;
  close: () => void;
}

function wsBase(): string {
  return `${window.location.protocol === "https:" ? "wss" : "ws"}://${window.location.host}`;
}

/**
 * Opens the `/hub` WebSocket and speaks the same binary protocol `/game` does (see `hubCodec.ts`).
 * One `connectionId` per tab/instance, generated once and reused across reconnects — same
 * convention as `GameClient.kt`.
 */
export function createHubSocket(opts: {
  token: string;
  /** Account identifier — the email, for `auth.provider: none` this IS the login. */
  userName?: string;
  /** Character name, when known. Left blank on purpose otherwise: the server picks the
   *  account's own character by email (see `HubConnection.kt`) — sending anything non-blank
   *  here would make it look up that literal name instead. */
  playerName?: string;
  lang?: string;
  /** `?gameSession=` — routes this connection to a browser E2E test's isolated, memory-only world
   *  instead of the shared default one. Production callers never set this. */
  gameSession?: string;
}): HubSocket {
  const connectionId = randomConnectionId();
  const listeners = new Map<string, Set<Listener>>();
  const statusListeners = new Set<(status: HubStatus) => void>();
  let status: HubStatus = "connecting";
  let ws: WebSocket | null = null;
  let retryDelayMs = 1000;
  let retryTimer: ReturnType<typeof setTimeout> | null = null;
  let deliberatelyClosed = false;

  function setStatus(next: HubStatus) {
    status = next;
    statusListeners.forEach((l) => l(next));
  }

  function emit(name: string, payload: unknown) {
    listeners.get(name)?.forEach((l) => l(payload));
  }

  function send(name: string, payload: unknown) {
    if (!ws || ws.readyState !== WebSocket.OPEN) return;
    ws.send(encodeHubMessage(name as never, payload));
  }

  function connect() {
    setStatus(status === "closed" ? "connecting" : status === "connecting" ? "connecting" : "reconnecting");
    const url = new URL("/hub", wsBase());
    if (opts.lang) url.searchParams.set("lang", opts.lang);
    if (opts.gameSession) url.searchParams.set("gameSession", opts.gameSession);
    const socket = new WebSocket(url.toString());
    socket.binaryType = "arraybuffer";
    ws = socket;

    socket.onopen = () => {
      retryDelayMs = 1000;
      setStatus("open");
      send("Connect", {
        playerName: opts.playerName ?? "",
        userName: opts.userName ?? "",
        preferredLanguage: opts.lang ?? "en",
        token: opts.token,
        needsWorld: false,
        connectionId,
      });
    };

    socket.onmessage = (event) => {
      if (!(event.data instanceof ArrayBuffer)) return;
      const decoded = decodeHubMessage(new Uint8Array(event.data));
      if (decoded) emit(decoded.name, decoded.payload);
    };

    socket.onclose = (event) => {
      ws = null;
      if (deliberatelyClosed) {
        setStatus("closed");
        return;
      }
      if (event.code === SUPERSEDED_CONNECTION_CLOSE_CODE) {
        setStatus("superseded");
        return;
      }
      setStatus("reconnecting");
      retryTimer = setTimeout(connect, retryDelayMs);
      retryDelayMs = Math.min(retryDelayMs * 2, 15_000);
    };
  }

  connect();

  return {
    get status() {
      return status;
    },
    send,
    on(name, listener) {
      const set = listeners.get(name) ?? new Set();
      set.add(listener);
      listeners.set(name, set);
      return () => set.delete(listener);
    },
    onStatusChange(listener) {
      statusListeners.add(listener);
      return () => statusListeners.delete(listener);
    },
    reconnect() {
      if (retryTimer) clearTimeout(retryTimer);
      deliberatelyClosed = false;
      retryDelayMs = 1000;
      connect();
    },
    close() {
      deliberatelyClosed = true;
      if (retryTimer) clearTimeout(retryTimer);
      ws?.close();
    },
  };
}
