import { OrgMicoliMicraftGameMinigameMiniGameDefinition as MiniGameDefinition } from "../../../generated/api/requests/types.gen";
import { useT } from "../../i18n";
import { MiniGameLivePanel } from "./MiniGameLivePanel";
import { FakePlayer, SimAction, SimRoom, SimInvite } from "./miniGameSimulator";

interface Props {
  player: FakePlayer;
  room: SimRoom | undefined;
  pendingInvite: SimInvite | undefined;
  lastAction: SimAction | undefined;
  games: MiniGameDefinition[];
  otherPlayers: FakePlayer[];
  onCreate: (gameType: string) => void;
  onInvite: (targetId: string) => void;
  onRespond: (accept: boolean) => void;
  onLeave: () => void;
  onSendAction: (payload: unknown) => void;
  onRemove: () => void;
}

/** Full panel for the active tab's fake player. Once in a room: the game (4/5, left) next to a
 * "manage participants" sidebar (1/5, right) — mirrors `MiniGameContainer`'s real in-game layout. */
export function FakePlayerCard({
  player,
  room,
  pendingInvite,
  lastAction,
  games,
  otherPlayers,
  onCreate,
  onInvite,
  onRespond,
  onLeave,
  onSendAction,
  onRemove,
}: Props) {
  const t = useT();
  const isHost = room?.hostId === player.id;
  const invitable = otherPlayers.filter((p) => !room?.members.some((m) => m.playerId === p.id));

  return (
    <div className="flex flex-col gap-4">
      <div className="flex items-center justify-between">
        <span className="font-medium text-white text-lg">{player.name}</span>
        <button className="text-xs text-[#8A99AF] hover:text-red-400" onClick={onRemove}>
          {t("miniGameTest.remove")}
        </button>
      </div>

      {!room && !pendingInvite && (
        <div className="flex flex-col gap-2">
          <span className="text-xs text-[#8A99AF]">{t("miniGameTest.noRoom")}</span>
          <div className="flex flex-wrap gap-2">
            {games.map((g) => (
              <button
                key={g.gameType}
                className="text-sm rounded-lg bg-[#1C2434] border border-[#2E3A4E] px-3 py-1.5 hover:border-[#3C50E0]"
                onClick={() => onCreate(g.gameType)}
              >
                {t("miniGameTest.create")} {g.displayName}
              </button>
            ))}
          </div>
        </div>
      )}

      {pendingInvite && (
        <div className="flex flex-col gap-2 rounded-lg bg-[#1C2434] p-3 border border-[#3C50E0] w-fit">
          <span className="text-xs text-white">
            {pendingInvite.fromName} → {pendingInvite.gameType}
          </span>
          <div className="flex gap-2">
            <button className="text-xs rounded-md bg-green-600/80 px-2 py-1 text-white" onClick={() => onRespond(true)}>
              {t("miniGameTest.accept")}
            </button>
            <button className="text-xs rounded-md bg-red-600/70 px-2 py-1 text-white" onClick={() => onRespond(false)}>
              {t("miniGameTest.decline")}
            </button>
          </div>
        </div>
      )}

      {room && (
        <div className="flex gap-3" style={{ height: 520 }}>
          <div className="flex-[4] min-w-0">
            <MiniGameLivePanel
              room={room}
              myPlayerId={player.id}
              games={games}
              lastAction={lastAction}
              onSendAction={onSendAction}
              onLeave={onLeave}
            />
          </div>
          <div className="flex-1 min-w-[180px] max-w-[260px] rounded-xl border border-[#2E3A4E] p-3 flex flex-col gap-3 overflow-y-auto">
            <div>
              <span className="text-xs text-[#8A99AF]">
                {t("miniGameTest.room")} {room.id} ({room.gameType})
              </span>
            </div>
            <ul className="flex flex-col gap-1">
              {room.members.map((m) => (
                <li key={m.playerId} className="text-xs text-white">
                  {m.playerId === room.hostId ? "★ " : ""}
                  {m.playerName}
                </li>
              ))}
            </ul>
            {isHost && invitable.length > 0 && (
              <div className="flex flex-col gap-1">
                {invitable.map((p) => (
                  <button
                    key={p.id}
                    className="text-xs rounded-md bg-[#1C2434] border border-[#2E3A4E] px-2 py-1 hover:border-[#3C50E0]"
                    onClick={() => onInvite(p.id)}
                  >
                    {t("miniGameTest.invite")} {p.name}
                  </button>
                ))}
              </div>
            )}
            <button className="text-xs text-[#8A99AF] hover:text-red-400 mt-auto text-left" onClick={onLeave}>
              {t("miniGameTest.leave")}
            </button>
          </div>
        </div>
      )}
    </div>
  );
}
