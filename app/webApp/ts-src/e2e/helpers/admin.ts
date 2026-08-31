import { E2eAccount } from "./connectClient";

const PORT = process.env.E2E_PORT ?? "8091";
const BASE = `http://localhost:${PORT}`;

/**
 * fetch() against the admin REST API, scoped to this test's isolated GameWorld via the
 * `X-Micraft-Game-Session` header. The E2E server runs with `auth.provider=none`, so no token is
 * needed. Use this to seed fixtures (give items, set the game time, create instance zones/scenes,
 * edit blocks) either before or after `connectClient` — under MICRAFT_E2E an unknown session id
 * spawns the world on first use, same as the `/game` WebSocket.
 */
export async function admin(acct: E2eAccount, path: string, init: RequestInit = {}): Promise<Response> {
  return fetch(`${BASE}${path}`, {
    ...init,
    headers: {
      "Content-Type": "application/json",
      "X-Micraft-Game-Session": acct.session,
      ...(init.headers ?? {}),
    },
  });
}
