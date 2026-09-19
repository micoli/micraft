import { MiniGameDefinition, MiniGameRoom } from "../types";

/** A room isn't playable yet below its game's `minPlayers` — the host must wait/invite more. */
export function miniGameRoomIsReady(room: MiniGameRoom, available: MiniGameDefinition[]): boolean {
  const minPlayers = available.find((d) => d.gameType === room.gameType)?.minPlayers ?? 1;
  return room.members.length >= minPlayers;
}
