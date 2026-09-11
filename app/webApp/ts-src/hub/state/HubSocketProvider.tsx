import { createContext, useContext, useEffect, useMemo, useRef, useState } from "react";
import { createHubSocket, type HubSocket, type HubStatus } from "../lib/hubSocket";
import { getStoredToken, getAccountEmail } from "../../lib/authStorage";
import { getApiPlayersByEmailByEmail, getApiItemsMeta } from "../../generated/api/requests";
import type { ItemMetaEntry } from "../../game/types";
import type { InventoryUpdateMsg, WalletUpdateMsg } from "../lib/hubCodec";
import { getGameSession, getTestPlayerId, getTestPlayerName } from "../lib/hubTestOverrides";

interface HubSocketContextValue {
  socket: HubSocket | null;
  status: HubStatus;
  /** The account's own character — iteration 1 picks the first one, same as the server
   *  (HubConnection.kt's listPlayersByEmail fallback). Empty until resolved. */
  playerId: string;
  playerName: string;
  /** Kept here (not per-route) so switching between Mail/Auction doesn't lose sync state or
   *  re-request it — one InventoryUpdate/WalletUpdate listener for the whole SPA. */
  inventory: Record<string, number>;
  wallet: number;
  /** Item catalog (label/color per ItemType) — same `GET /api/items/meta` the game client loads,
   *  fetched once, shared by every screen that renders an item (Mail attachments, Auction). */
  itemMeta: Record<string, ItemMetaEntry>;
}

const HubSocketContext = createContext<HubSocketContextValue>({
  socket: null,
  status: "closed",
  playerId: "",
  playerName: "",
  inventory: {},
  wallet: 0,
  itemMeta: {},
});

/**
 * Owns the one `/hub` WebSocket for the whole companion SPA, plus the small set of shared state
 * every screen needs (own player id/name, inventory, wallet, item catalog). Mounted once inside
 * `HubAuthGate` — every route below reads/sends through `useHubSocket()`/`useHubSend()`.
 */
export function HubSocketProvider({ children }: { children: React.ReactNode }) {
  const [socket, setSocket] = useState<HubSocket | null>(null);
  const [status, setStatus] = useState<HubStatus>("connecting");
  const [playerId, setPlayerId] = useState("");
  const [playerName, setPlayerName] = useState("");
  const [inventory, setInventory] = useState<Record<string, number>>({});
  const [wallet, setWallet] = useState(0);
  const [itemMeta, setItemMeta] = useState<Record<string, ItemMetaEntry>>({});

  useEffect(() => {
    let cancelled = false;
    const email = getAccountEmail();
    const gameSession = getGameSession();
    const testPlayerName = getTestPlayerName();

    function openSocket(playerName: string | undefined) {
      if (cancelled) return;
      // No playerName sent for the normal (non-test) path even when known: the server resolves
      // the account's own character by email regardless (HubConnection.kt) — sending a literal
      // name would make it look up that name instead of "the" character. userName carries the
      // account email, required for auth.provider=none where it doubles as the login itself.
      const created = createHubSocket({
        token: getStoredToken(),
        userName: email,
        playerName,
        gameSession,
      });
      setSocket(created);
      created.onStatusChange(setStatus);
      created.on("InventoryUpdate", (p) => setInventory((p as InventoryUpdateMsg).inventory));
      created.on("WalletUpdate", (p) => setWallet((p as WalletUpdateMsg).copper));
    }

    if (gameSession) {
      // Browser E2E: GET /api/players/by-email always reads the DEFAULT world's on-disk
      // persistence, which an isolated E2E world (memory-only, reserved via
      // POST /api/admin/players) never touches — nothing there to look up. The test hands the
      // character's name/id straight through instead (see hubTestOverrides.ts).
      setPlayerId(getTestPlayerId() ?? "");
      setPlayerName(testPlayerName ?? "");
      openSocket(testPlayerName);
    } else {
      getApiPlayersByEmailByEmail({ path: { email } })
        .then((r) => r.data?.[0])
        .catch(() => undefined)
        .then((first) => {
          if (cancelled) return;
          setPlayerId(first?.id ?? "");
          setPlayerName(first?.name ?? "");
          openSocket(undefined);
        });
    }

    const loadItemMeta = () =>
      getApiItemsMeta({ throwOnError: true })
        .then((r) => r.data as unknown as Record<string, ItemMetaEntry>)
        .then((data) => {
          if (!cancelled) setItemMeta(data);
        })
        .catch(() => {
          if (!cancelled) setTimeout(loadItemMeta, 2000);
        });
    loadItemMeta();

    return () => {
      cancelled = true;
      setSocket((s) => {
        s?.close();
        return null;
      });
    };
  }, []);

  const value = useMemo(
    () => ({ socket, status, playerId, playerName, inventory, wallet, itemMeta }),
    [socket, status, playerId, playerName, inventory, wallet, itemMeta],
  );

  return <HubSocketContext.Provider value={value}>{children}</HubSocketContext.Provider>;
}

export function useHubSocket(): HubSocketContextValue {
  return useContext(HubSocketContext);
}

export function useHubSend(): (name: string, payload: unknown) => void {
  const { socket } = useHubSocket();
  return (name, payload) => socket?.send(name, payload);
}

/** Subscribes to one server message name for the component's lifetime. */
export function useHubMessage<T>(name: string, handler: (payload: T) => void) {
  const { socket } = useHubSocket();
  const handlerRef = useRef(handler);
  useEffect(() => {
    handlerRef.current = handler;
  }, [handler]);

  useEffect(() => {
    if (!socket) return;
    return socket.on(name, (payload) => handlerRef.current(payload as T));
  }, [socket, name]);
}
