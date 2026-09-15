// localStorage, not sessionStorage: unlike the game/hub (one login per tab is fine), the admin
// panel is typically left open across reloads and reopened in fresh tabs — a stored token should
// follow the browser, not the tab.
const KEY = "micraft_admin_token";

export function getAdminToken(): string {
  try {
    return localStorage.getItem(KEY) || "";
  } catch {
    return "";
  }
}

export function storeAdminToken(token: string) {
  try {
    localStorage.setItem(KEY, token);
  } catch {
    /* empty */
  }
}

export function clearAdminToken() {
  try {
    localStorage.removeItem(KEY);
  } catch {
    /* empty */
  }
}
