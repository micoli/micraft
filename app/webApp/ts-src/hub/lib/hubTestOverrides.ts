/**
 * Test-only seams, all inert for production visitors. Captured once at boot from the URL (react-
 * router's `navigate()` calls in this app drop query strings, so the raw URL isn't a reliable
 * place to keep reading them from) into sessionStorage, so they survive client-side navigation for
 * the rest of the tab's life.
 *
 * `gameSession` routes the `/hub` WebSocket to a browser E2E test's isolated, memory-only
 * GameWorld (GameWorldRegistry.resolve) instead of the shared default one — same `?gameSession=`
 * convention as `/game` and the admin API's `X-Micraft-Game-Session` header.
 *
 * `playerName`/`playerId` bypass HubSocketProvider's normal "look up the account's own character
 * via GET /api/players/by-email" — that route always reads the DEFAULT world's on-disk
 * persistence, which an isolated E2E world (memory-only, players reserved via
 * `POST /api/admin/players`) never touches. A spec passes the id `createPlayer()` already returned
 * it, instead of a lookup that would find nothing there.
 */
const GAME_SESSION_KEY = "micraft_hub_game_session";
const PLAYER_NAME_KEY = "micraft_hub_test_player_name";
const PLAYER_ID_KEY = "micraft_hub_test_player_id";

export function captureHubTestOverridesFromUrl() {
  try {
    const params = new URLSearchParams(window.location.search);
    const gameSession = params.get("gameSession");
    const playerName = params.get("playerName");
    const playerId = params.get("playerId");
    if (gameSession) sessionStorage.setItem(GAME_SESSION_KEY, gameSession);
    if (playerName) sessionStorage.setItem(PLAYER_NAME_KEY, playerName);
    if (playerId) sessionStorage.setItem(PLAYER_ID_KEY, playerId);
  } catch {
    /* empty */
  }
}

function read(key: string): string | undefined {
  try {
    return sessionStorage.getItem(key) || undefined;
  } catch {
    return undefined;
  }
}

export const getGameSession = (): string | undefined => read(GAME_SESSION_KEY);
export const getTestPlayerName = (): string | undefined => read(PLAYER_NAME_KEY);
export const getTestPlayerId = (): string | undefined => read(PLAYER_ID_KEY);
