// Pure in-browser stand-in for `MiniGameManager` (server/.../game/minigame/MiniGameManager.kt):
// same rules (host-only invite, max players, promote-next-member-on-host-leave, dissolve when
// empty, broadcast to every other member), but keyed by fake player ids instead of real
// PlayerSessions — lets a dev exercise the create/invite/accept/leave/broadcast flow with several
// "players" from a single browser tab, without a real second client or a live websocket.

export interface FakePlayer {
  id: string;
  name: string;
}

export interface SimMember {
  playerId: string;
  playerName: string;
  online: boolean;
}

export interface SimRoom {
  id: string;
  hostId: string;
  hostName: string;
  gameType: string;
  members: SimMember[];
}

export interface SimInvite {
  roomId: string;
  gameType: string;
  fromName: string;
}

export interface SimLogEntry {
  id: string;
  at: number;
  text: string;
}

export interface SimAction {
  roomId: string;
  fromPlayerId: string;
  payload: string;
}

export interface SimState {
  players: FakePlayer[];
  rooms: Record<string, SimRoom>;
  /** targetPlayerId -> pending invite */
  pendingInvites: Record<string, SimInvite>;
  /** targetPlayerId -> last action delivered to them (mirrors the host's single-slot `lastAction`). */
  actionsByPlayer: Record<string, SimAction>;
  log: SimLogEntry[];
}

export function initialSimState(): SimState {
  return { players: [], rooms: {}, pendingInvites: {}, actionsByPlayer: {}, log: [] };
}

let nextId = 1;
const freshId = (prefix: string) => `${prefix}-${nextId++}`;

function withLog(state: SimState, text: string): SimState {
  return { ...state, log: [{ id: freshId("log"), at: Date.now(), text }, ...state.log].slice(0, 200) };
}

function roomOf(state: SimState, playerId: string): SimRoom | undefined {
  return Object.values(state.rooms).find((r) => r.members.some((m) => m.playerId === playerId));
}

export function addFakePlayer(state: SimState, name: string): SimState {
  const player: FakePlayer = { id: freshId("player"), name };
  return withLog({ ...state, players: [...state.players, player] }, `+ fake player "${name}"`);
}

export function removeFakePlayer(state: SimState, playerId: string): SimState {
  const player = state.players.find((p) => p.id === playerId);
  const afterLeave = roomOf(state, playerId) ? leave(state, playerId) : state;
  return withLog(
    {
      ...afterLeave,
      players: afterLeave.players.filter((p) => p.id !== playerId),
      pendingInvites: Object.fromEntries(
        Object.entries(afterLeave.pendingInvites).filter(([targetId]) => targetId !== playerId),
      ),
    },
    `- fake player "${player?.name ?? playerId}"`,
  );
}

export function create(state: SimState, hostId: string, gameType: string, maxPlayers: number): SimState {
  const host = state.players.find((p) => p.id === hostId);
  if (!host) return state;
  if (roomOf(state, hostId)) return withLog(state, `deny: "${host.name}" already in a room`);
  const room: SimRoom = {
    id: freshId("room"),
    hostId,
    hostName: host.name,
    gameType,
    members: [{ playerId: host.id, playerName: host.name, online: true }],
  };
  void maxPlayers;
  return withLog(
    { ...state, rooms: { ...state.rooms, [room.id]: room } },
    `"${host.name}" created room ${room.id} (${gameType})`,
  );
}

export function invite(
  state: SimState,
  hostId: string,
  roomId: string,
  targetId: string,
  maxPlayers: number,
): SimState {
  const room = state.rooms[roomId];
  const target = state.players.find((p) => p.id === targetId);
  if (!room || !target) return state;
  if (room.hostId !== hostId) return withLog(state, `deny: only the host can invite`);
  if (room.members.length >= maxPlayers) return withLog(state, `deny: room ${roomId} is full`);
  if (roomOf(state, targetId)) return withLog(state, `deny: "${target.name}" already in a room`);
  return withLog(
    {
      ...state,
      pendingInvites: {
        ...state.pendingInvites,
        [targetId]: { roomId: room.id, gameType: room.gameType, fromName: room.hostName },
      },
    },
    `"${room.hostName}" invited "${target.name}" to room ${room.id}`,
  );
}

export function respondInvite(state: SimState, targetId: string, accept: boolean, maxPlayers: number): SimState {
  const pending = state.pendingInvites[targetId];
  const target = state.players.find((p) => p.id === targetId);
  const withoutInvite: SimState = {
    ...state,
    pendingInvites: Object.fromEntries(Object.entries(state.pendingInvites).filter(([id]) => id !== targetId)),
  };
  if (!pending || !target) return withoutInvite;
  if (!accept) return withLog(withoutInvite, `"${target.name}" declined the invite`);
  const room = withoutInvite.rooms[pending.roomId];
  if (!room) return withLog(withoutInvite, `deny: room ${pending.roomId} no longer exists`);
  if (room.members.length >= maxPlayers) return withLog(withoutInvite, `deny: room ${room.id} is full`);
  const updatedRoom: SimRoom = {
    ...room,
    members: [...room.members, { playerId: target.id, playerName: target.name, online: true }],
  };
  return withLog(
    { ...withoutInvite, rooms: { ...withoutInvite.rooms, [room.id]: updatedRoom } },
    `"${target.name}" joined room ${room.id}`,
  );
}

/** Mirrors `MiniGameManager.leave`: a player leaving mid-game stops it for everyone — never
 * partial continuation/re-hosting. */
export function leave(state: SimState, playerId: string): SimState {
  const room = roomOf(state, playerId);
  if (!room) return state;
  const player = room.members.find((m) => m.playerId === playerId);
  const { [room.id]: _removed, ...rest } = state.rooms;
  void _removed;
  return withLog(
    { ...state, rooms: rest },
    `"${player?.playerName ?? playerId}" left room ${room.id} — room dissolved for everyone`,
  );
}

/** Mirrors `MiniGameManager.broadcastAction`: sender never receives their own action back. */
export function broadcastAction(state: SimState, senderId: string, payload: string): SimState {
  const room = roomOf(state, senderId);
  const sender = state.players.find((p) => p.id === senderId);
  if (!room || !sender) return state;
  const recipients = room.members.map((m) => m.playerId).filter((id) => id !== senderId);
  const actionsByPlayer = { ...state.actionsByPlayer };
  for (const id of recipients) {
    actionsByPlayer[id] = { roomId: room.id, fromPlayerId: senderId, payload };
  }
  return withLog(
    { ...state, actionsByPlayer },
    `"${sender.name}" sent action in room ${room.id}: ${payload} -> [${recipients
      .map((id) => state.players.find((p) => p.id === id)?.name ?? id)
      .join(", ")}]`,
  );
}
