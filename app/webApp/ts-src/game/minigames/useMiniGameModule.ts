import { ComponentType, useEffect, useState } from "react";
import { MiniGameActionMsg, MiniGameRoom } from "../types";

/** Contract every mini-game module's default export must satisfy (see app/minigames/README.md). */
export interface MiniGameProps {
  room: MiniGameRoom;
  /** `room.members[i].playerId` this instance is being rendered for — never read a host global
   * for this (there may be several "players" in the same page, e.g. the admin test harness). */
  myPlayerId: string;
  lastAction: MiniGameActionMsg | null;
  sendAction: (payload: unknown) => void;
  onLeave: () => void;
}

export type MiniGameLoadState =
  | { status: "loading" }
  | { status: "error"; message: string }
  | { status: "ready"; Component: ComponentType<MiniGameProps> };

/**
 * Loads a mini-game's independent bundle at runtime via a dynamic `import()` — the bundle is
 * never part of the webApp build graph, shared by the real host container
 * (`MiniGameContainer.tsx`) and the admin test-harness live panel so both exercise the exact same
 * loading path.
 */
export function useMiniGameModule(entryUrl: string | undefined, cacheKey: string): MiniGameLoadState {
  const [load, setLoad] = useState<MiniGameLoadState>({ status: "loading" });

  useEffect(() => {
    let cancelled = false;
    setLoad({ status: "loading" });
    if (!entryUrl) {
      setLoad({ status: "error", message: `Unknown mini-game: ${cacheKey}` });
      return;
    }
    // entryUrl is a runtime value (from the /api/minigames registry), not a string literal, so
    // esbuild (this app's bundler — see app/webApp/ts-src/package.json) already leaves this as a
    // native dynamic import() rather than trying to resolve/bundle it statically.
    import(entryUrl)
      .then((mod) => {
        if (cancelled) return;
        if (!mod?.default) {
          setLoad({ status: "error", message: `Invalid module (no default export): ${entryUrl}` });
          return;
        }
        setLoad({ status: "ready", Component: mod.default });
      })
      .catch((e) => {
        if (!cancelled) setLoad({ status: "error", message: `Failed to load: ${String(e)}` });
      });
    return () => {
      cancelled = true;
    };
  }, [entryUrl, cacheKey]);

  return load;
}
