import { client } from "../generated/api/requests/client.gen";
import { getStoredToken } from "./authStorage";

let configured = false;

/**
 * Injects the bearer token (if any) into every generated-client request. Same origin — no base
 * URL needed. `getToken` defaults to the game/hub's per-tab sessionStorage token; the admin
 * bundle passes its own localStorage-backed getter (see admin/auth/adminTokenStorage.ts) since
 * that panel is expected to survive reloads and fresh tabs, unlike a game session.
 */
export function configureApiClient(getToken: () => string = getStoredToken) {
  if (configured) return;
  configured = true;
  client.interceptors.request.use((request) => {
    const token = getToken();
    if (token) request.headers.set("Authorization", `Bearer ${token}`);
    return request;
  });
}
