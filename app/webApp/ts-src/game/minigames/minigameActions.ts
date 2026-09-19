import { ClientEventPrefix } from "../../generated/input/clientEvents";

// Pure network-emission helpers for the mini-game framework (host side). Shared by the
// slash-command handler and MiniGameDialog/MiniGameContainer so both paths send the exact
// same wire events. Field names must match the Kotlin `ClientMessage.MiniGameXxx` classes
// exactly, since the server decodes this JSON directly.

export function createMiniGame(gameType: string): void {
  window.mcState.events.push(`${ClientEventPrefix.MINIGAME_CREATE}${JSON.stringify({ gameType })}`);
}

export function inviteMiniGame(roomId: string, targetName: string): void {
  window.mcState.events.push(`${ClientEventPrefix.MINIGAME_INVITE}${JSON.stringify({ roomId, targetName })}`);
}

export function respondMiniGameInvite(roomId: string, accept: boolean): void {
  window.mcState.events.push(`${ClientEventPrefix.MINIGAME_RESPOND}${JSON.stringify({ roomId, accept })}`);
}

export function leaveMiniGame(roomId: string): void {
  window.mcState.events.push(`${ClientEventPrefix.MINIGAME_LEAVE}${JSON.stringify({ roomId })}`);
}

export function sendMiniGameAction(roomId: string, payload: unknown): void {
  window.mcState.events.push(
    `${ClientEventPrefix.MINIGAME_ACTION}${JSON.stringify({ roomId, payload: JSON.stringify(payload) })}`,
  );
}
