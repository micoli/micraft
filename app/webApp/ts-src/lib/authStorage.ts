export type AuthMode = "none" | "local" | "oauth" | "loading";

export type PlayerEntry = { name: string; id: string };

export function getUsers(): Record<string, PlayerEntry[]> {
  try {
    const raw = JSON.parse(localStorage.getItem("micraft_users") || "{}") as Record<string, (string | PlayerEntry)[]>;
    return Object.fromEntries(
      Object.entries(raw).map(([user, chars]) => [
        user,
        Array.isArray(chars) ? chars.map((c) => (typeof c === "string" ? { name: c, id: "" } : c)) : [],
      ]),
    );
  } catch {
    return {};
  }
}

export function saveUsers(u: Record<string, PlayerEntry[]>) {
  try {
    localStorage.setItem("micraft_users", JSON.stringify(u));
  } catch {
    /* empty */
  }
}

export function getPlayerEntries(accountKey: string): PlayerEntry[] {
  return getUsers()[accountKey] ?? [];
}

export function getLastPlayerEntry(username: string): PlayerEntry | null {
  const accountKey = getAccountEmail() || username;
  const name = getLastPlayer(accountKey);
  if (!name) return null;
  return getPlayerEntries(accountKey).find((e) => e.name === name) ?? null;
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
  } catch {
    /* empty */
  }
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
  } catch {
    /* empty */
  }
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
  } catch {
    /* empty */
  }
}

export function clearStoredToken() {
  try {
    sessionStorage.removeItem("micraft_auth_token");
    sessionStorage.removeItem("micraft_auth_display");
    sessionStorage.removeItem("micraft_auth_email");
  } catch {
    /* empty */
  }
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
  } catch {
    /* empty */
  }
}

export function clearLastUser() {
  try {
    localStorage.removeItem("micraft_last_user");
  } catch {
    /* empty */
  }
}

export function clearLastPlayer(username: string) {
  try {
    localStorage.removeItem("micraft_last_player_" + username);
  } catch {
    /* empty */
  }
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
  } catch {
    /* empty */
  }
}

export function getAccountEmail(): string {
  try {
    return sessionStorage.getItem("micraft_auth_email") || "";
  } catch {
    return "";
  }
}

export function saveAccountEmail(email: string) {
  try {
    sessionStorage.setItem("micraft_auth_email", email);
  } catch {
    /* empty */
  }
}

export function clearAccountEmail() {
  try {
    sessionStorage.removeItem("micraft_auth_email");
  } catch {
    /* empty */
  }
}
