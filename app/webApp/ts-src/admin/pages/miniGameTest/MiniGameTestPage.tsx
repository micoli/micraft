import { useEffect, useState } from "react";
import { getApiMinigames } from "../../../generated/api/requests";
import { OrgMicoliMicraftGameMinigameMiniGameDefinition as MiniGameDefinition } from "../../../generated/api/requests/types.gen";
import { useT } from "../../i18n";
import { FakePlayerCard } from "./FakePlayerCard";
import { MiniGameLogPanel } from "./MiniGameLogPanel";
import { PlayerTabs } from "./PlayerTabs";
import {
  SimState,
  addFakePlayer,
  removeFakePlayer,
  broadcastAction,
  create,
  initialSimState,
  invite,
  leave,
  respondInvite,
} from "./miniGameSimulator";

/**
 * Admin-only test harness: simulates the mini-game room protocol (create/invite/accept/leave/
 * broadcast, same rules as `MiniGameManager.kt`) entirely in the browser with fake players — each
 * player is a tab and the real mini-game bundle is loaded and played inside it, so an
 * invitation/gameplay flow can be exercised without two real logged-in clients or a live
 * websocket connection.
 */
const DEFAULT_PLAYER_NAMES = ["Alice", "Bob", "Charlie", "Diana"];

function defaultSimState(): SimState {
  return DEFAULT_PLAYER_NAMES.reduce((s, name) => addFakePlayer(s, name), initialSimState());
}

export function MiniGameTestPage() {
  const t = useT();
  const [games, setGames] = useState<MiniGameDefinition[]>([]);
  const [state, setState] = useState<SimState>(defaultSimState);
  const [activeId, setActiveId] = useState<string | null>(() => state.players[0]?.id ?? null);
  const [newPlayerName, setNewPlayerName] = useState("");

  useEffect(() => {
    getApiMinigames({ throwOnError: true })
      .then((r) => setGames(r.data as unknown as MiniGameDefinition[]))
      .catch(() => setGames([]));
  }, []);

  const maxPlayersFor = (gameType: string) => games.find((g) => g.gameType === gameType)?.maxPlayers ?? 8;

  const addPlayer = () => {
    const name = newPlayerName.trim() || `Player ${state.players.length + 1}`;
    setState((s) => {
      const next = addFakePlayer(s, name);
      const added = next.players[next.players.length - 1];
      setActiveId((current) => current ?? added.id);
      return next;
    });
    setNewPlayerName("");
  };

  const removePlayer = (id: string) => {
    setState((s) => removeFakePlayer(s, id));
    setActiveId((current) => (current === id ? null : current));
  };

  const active = state.players.find((p) => p.id === activeId) ?? null;
  const activeRoom = active
    ? Object.values(state.rooms).find((r) => r.members.some((m) => m.playerId === active.id))
    : undefined;

  return (
    <div className="flex flex-col h-full gap-4">
      <div className="flex items-center justify-between gap-4">
        <p className="text-sm text-[#8A99AF]">{t("miniGameTest.intro")}</p>
        <div className="flex items-center gap-2">
          <input
            value={newPlayerName}
            onChange={(e) => setNewPlayerName(e.target.value)}
            onKeyDown={(e) => e.key === "Enter" && addPlayer()}
            placeholder={t("miniGameTest.newPlayerName")}
            className="bg-[#1C2434] border border-[#2E3A4E] rounded-lg px-2 py-1 text-sm text-white outline-none focus:border-[#3C50E0]"
          />
          <button className="text-sm rounded-lg bg-[#3C50E0] px-3 py-1.5 text-white" onClick={addPlayer}>
            {t("miniGameTest.addPlayer")}
          </button>
        </div>
      </div>

      {games.length === 0 && <p className="text-sm text-[#8A99AF]">{t("miniGameTest.noGames")}</p>}

      <PlayerTabs players={state.players} activeId={activeId} onSelect={setActiveId} />

      <div className="flex gap-4 flex-1 min-h-0">
        <div className="flex-1 overflow-y-auto">
          {!active && <p className="text-sm text-[#8A99AF]">{t("miniGameTest.noPlayers")}</p>}
          {active && (
            <FakePlayerCard
              player={active}
              room={activeRoom}
              pendingInvite={state.pendingInvites[active.id]}
              lastAction={state.actionsByPlayer[active.id]}
              games={games}
              otherPlayers={state.players.filter((o) => o.id !== active.id)}
              onCreate={(gameType) => setState((s) => create(s, active.id, gameType, maxPlayersFor(gameType)))}
              onInvite={(targetId) => {
                const room = Object.values(state.rooms).find((r) => r.hostId === active.id);
                if (room) setState((s) => invite(s, active.id, room.id, targetId, maxPlayersFor(room.gameType)));
              }}
              onRespond={(accept) => {
                const gameType = state.pendingInvites[active.id]?.gameType ?? "";
                setState((s) => respondInvite(s, active.id, accept, maxPlayersFor(gameType)));
              }}
              onLeave={() => setState((s) => leave(s, active.id))}
              onSendAction={(payload) => setState((s) => broadcastAction(s, active.id, JSON.stringify(payload)))}
              onRemove={() => removePlayer(active.id)}
            />
          )}
        </div>
        <div className="w-96 shrink-0 flex flex-col">
          <MiniGameLogPanel entries={state.log} />
        </div>
      </div>
    </div>
  );
}
