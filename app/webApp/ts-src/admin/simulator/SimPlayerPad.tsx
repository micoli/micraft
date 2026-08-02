import { useT } from "../i18n";
import type { SimPlayer } from "./types";

interface Props {
  players: SimPlayer[];
  onInput: (name: string, dx: number, dz: number, yaw: number, jump: boolean) => void;
}

const KEY =
  "flex h-5 w-6 items-center justify-center rounded bg-[#2E3A4E] text-[10px] text-[#C7D2FE] hover:bg-[#3C50E0]/60";

/**
 * Movement pad for the stand-in players, sitting next to the timeline.
 *
 * Compact on purpose: it shares a row with the timeline instead of a column in the setup panel, since
 * driving a player is something you do *while* watching the arena run — same reason the speed presets
 * are there and not in the left column.
 */
export function SimPlayerPad({ players, onInput }: Props) {
  const t = useT();
  if (players.length === 0) return null;

  return (
    <div className="flex shrink-0 items-start gap-3 border-l border-[#2E3A4E] pl-3">
      {players.map((player) => (
        <div key={player.id}>
          <p className="mb-1 truncate text-[10px] text-[#8A99AF]" title={player.name}>
            {player.name}
          </p>
          <div className="grid grid-cols-3 gap-0.5">
            <span />
            <button
              type="button"
              title={t("sim.pad.forward")}
              onClick={() => onInput(player.name, 0, 1, 0, false)}
              className={KEY}
            >
              ↑
            </button>
            <span />
            <button
              type="button"
              title={t("sim.pad.left")}
              onClick={() => onInput(player.name, -1, 0, -Math.PI / 2, false)}
              className={KEY}
            >
              ←
            </button>
            <button
              type="button"
              title={t("sim.pad.jump")}
              onClick={() => onInput(player.name, 0, 0, 0, true)}
              className={KEY}
            >
              ⤒
            </button>
            <button
              type="button"
              title={t("sim.pad.right")}
              onClick={() => onInput(player.name, 1, 0, Math.PI / 2, false)}
              className={KEY}
            >
              →
            </button>
            <span />
            <button
              type="button"
              title={t("sim.pad.backward")}
              onClick={() => onInput(player.name, 0, -1, Math.PI, false)}
              className={KEY}
            >
              ↓
            </button>
            <span />
          </div>
        </div>
      ))}
    </div>
  );
}
