export type AuthMode = "none" | "local" | "oauth" | "loading";

export function getUsers(): Record<string, string[]> {
  try {
    return JSON.parse(localStorage.getItem("micraft_users") || "{}");
  } catch {
    return {};
  }
}

export function saveUsers(u: Record<string, string[]>) {
  try {
    localStorage.setItem("micraft_users", JSON.stringify(u));
  } catch {}
}

export function getLastPlayer(username: string): string {
  try {
    return localStorage.getItem("micraft_last_player_" + username) || "";
  } catch {
    return "";
  }
}

export function saveLastPlayer(username: string, playerName: string) {
  try {
    localStorage.setItem("micraft_last_player_" + username, playerName);
  } catch {}
}

export function getLastLang(): string {
  try {
    return localStorage.getItem("micraft_last_lang") || "en";
  } catch {
    return "en";
  }
}

export function saveLastLang(lang: string) {
  try {
    localStorage.setItem("micraft_last_lang", lang);
  } catch {}
}

export function getStoredToken(): string {
  try {
    return sessionStorage.getItem("micraft_auth_token") || "";
  } catch {
    return "";
  }
}

export function storeToken(token: string) {
  try {
    sessionStorage.setItem("micraft_auth_token", token);
  } catch {}
}

export function clearStoredToken() {
  try {
    sessionStorage.removeItem("micraft_auth_token");
    sessionStorage.removeItem("micraft_auth_display");
  } catch {}
}

export function getLastUser(): string {
  try {
    return localStorage.getItem("micraft_last_user") || "";
  } catch {
    return "";
  }
}

export function saveLastUser(user: string) {
  try {
    localStorage.setItem("micraft_last_user", user);
  } catch {}
}

export function clearLastUser() {
  try {
    localStorage.removeItem("micraft_last_user");
  } catch {}
}

export function clearLastPlayer(username: string) {
  try {
    localStorage.removeItem("micraft_last_player_" + username);
  } catch {}
}

export function getStoredDisplayName(): string {
  try {
    return sessionStorage.getItem("micraft_auth_display") || "";
  } catch {
    return "";
  }
}

export function storeDisplayName(name: string) {
  try {
    sessionStorage.setItem("micraft_auth_display", name);
  } catch {}
}
