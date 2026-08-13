import { client } from "../generated/api/requests/client.gen";
import { getStoredToken } from "./authStorage";

let configured = false;

/** Injects the bearer token (if any) into every generated-client request. Same origin — no base URL needed. */
export function configureApiClient() {
  if (configured) return;
  configured = true;
  client.interceptors.request.use((request) => {
    const token = getStoredToken();
    if (token) request.headers.set("Authorization", `Bearer ${token}`);
    return request;
  });
}
