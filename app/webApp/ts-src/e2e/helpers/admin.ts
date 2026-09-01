import {E2eAccount} from "./connectClient";
import {createClient, createConfig} from "../../generated/api/requests/client";
import {postApiAdminUsers} from "../../generated/api/requests";

const PORT = process.env.E2E_PORT ?? "8091";
const BASE = `http://localhost:${PORT}`;

/** Generated OpenAPI client, pointed at the E2E server. */
const api = createClient(createConfig({baseUrl: BASE}));

/**
 * Options that scope a generated-client call to this test's isolated GameWorld.
 * The `X-Micraft-Game-Session` header isn't part of the OpenAPI spec, so it rides
 * along untyped here — this is the single place that knows about it.
 */
export const adminWorldContext = (acct: E2eAccount) =>
    ({client: api, headers: {"X-Micraft-Game-Session": acct.session}, throwOnError: true}) as const;

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

/** Create the no-auth account for `email` (idempotent — 409 = already exists). */
export async function createUser(acct: E2eAccount, email: string): Promise<void> {
    const {response} = await postApiAdminUsers({
        ...adminWorldContext(acct),
        throwOnError: false,
        body: {email, password: "", displayName: email, groups: []}
    });
    if (response.status !== 201 && response.status !== 409) {
        throw new Error(`createUser ${email} failed: ${response.status} ${await response.text()}`);
    }
}

export interface CreatedPlayer {
    playerId: string;
    name: string;
    email: string;
    characterClass?: string;
    level?: number;
}

export interface CreatePlayerOptions {
    /** RPG class — default "WARRIOR". Pass null for a plain (non-RPG) player. */
    characterClass?: string | null;
    str?: number;
    dex?: number;
    intel?: number;
    wis?: number;
    con?: number;
    cha?: number;
}

/**
 * Create the RPG player for this test's world and get its id + character back — the identity
 * `onConnect` will assign, known before the browser connects. Replaces the in-game
 * character-creation flow: with a class set, the client receives CharacterSync, never
 * CharacterCreationRequired, so it never routes to /char-rpg-create.
 */
export async function createPlayer(acct: E2eAccount, opts: CreatePlayerOptions = {}): Promise<CreatedPlayer> {
    const cc = opts.characterClass === undefined ? "WARRIOR" : opts.characterClass;
    const body: Record<string, unknown> = {name: acct.charName, email: acct.email};
    if (cc !== null) {
        body.characterClass = cc;
        body.str = opts.str ?? 8;
        body.dex = opts.dex ?? 8;
        body.intel = opts.intel ?? 8;
        body.wis = opts.wis ?? 8;
        body.con = opts.con ?? 8;
        body.cha = opts.cha ?? 8;
    }
    const r = await admin(acct, "/api/admin/players", {method: "POST", body: JSON.stringify(body)});
    if (!r.ok) throw new Error(`createPlayer ${acct.charName} failed: ${r.status} ${await r.text()}`);
    return (await r.json()) as CreatedPlayer;
}
