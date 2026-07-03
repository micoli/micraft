import { useState, useEffect, useRef } from "react";

function getUsers(): Record<string, string[]> {
  try {
    return JSON.parse(localStorage.getItem("micraft_users") || "{}");
  } catch {
    return {};
  }
}
function saveUsers(u: Record<string, string[]>) {
  try {
    localStorage.setItem("micraft_users", JSON.stringify(u));
  } catch {}
}
function getLastPlayer(username: string): string {
  try {
    return localStorage.getItem("micraft_last_player_" + username) || "";
  } catch {
    return "";
  }
}
function saveLastPlayer(username: string, playerName: string) {
  try {
    localStorage.setItem("micraft_last_player_" + username, playerName);
  } catch {}
}
function getLastLang(): string {
  try {
    return localStorage.getItem("micraft_last_lang") || "en";
  } catch {
    return "en";
  }
}
function saveLastLang(lang: string) {
  try {
    localStorage.setItem("micraft_last_lang", lang);
  } catch {}
}
function getStoredToken(): string {
  try {
    return sessionStorage.getItem("micraft_auth_token") || "";
  } catch {
    return "";
  }
}
function storeToken(token: string) {
  try {
    sessionStorage.setItem("micraft_auth_token", token);
  } catch {}
}
function clearStoredToken() {
  try {
    sessionStorage.removeItem("micraft_auth_token");
    sessionStorage.removeItem("micraft_auth_display");
  } catch {}
}

export type AuthMode = "none" | "local" | "oauth" | "loading";
export type LoginStep = "auth" | "chars" | "create";

interface UseLoginParams {
  visible: boolean;
  loginResultRef: React.MutableRefObject<string>;
  onHide: () => void;
}

export function useLogin({ visible, loginResultRef, onHide }: UseLoginParams) {
  const [authMode, setAuthMode] = useState<AuthMode>("loading");
  const [step, setStep] = useState<LoginStep>("auth");
  const [username, setUsername] = useState("");
  const [password, setPassword] = useState("");
  const [authError, setAuthError] = useState("");
  const [authLoading, setAuthLoading] = useState(false);
  const [token, setToken] = useState("");
  const [lang, setLang] = useState("en");
  const [chars, setChars] = useState<string[]>([]);
  const [selected, setSelected] = useState("");
  const [previewSkin, setPreviewSkin] = useState("player");
  const [previewArmors, setPreviewArmors] = useState<string[]>([]);
  const [previewWalking, setPreviewWalking] = useState(true);
  const [createName, setCreateName] = useState("");
  const [createSkin, setCreateSkin] = useState("player");
  const [createError, setCreateError] = useState("");
  const [createWalking, setCreateWalking] = useState(true);

  const usernameInputRef = useRef<HTMLInputElement>(null);
  const passwordInputRef = useRef<HTMLInputElement>(null);
  const createNameInputRef = useRef<HTMLInputElement>(null);
  const playButtonRef = useRef<HTMLButtonElement>(null);

  useEffect(() => {
    fetch("/api/auth/config")
      .then((r) => r.json())
      .then((d: { provider: string }) => setAuthMode((d.provider as AuthMode) || "none"))
      .catch(() => setAuthMode("none"));
  }, []);

  async function goChars(user: string) {
    const trimmed = user.trim();
    try {
      localStorage.setItem("micraft_last_user", trimmed);
    } catch {}
    setUsername(trimmed);
    setStep("chars");
    setLang(getLastLang());
    const users = getUsers();
    let playerChars = users[trimmed] || [];
    if (playerChars.length > 0) {
      const results = await Promise.all(
        playerChars.map((name) =>
          fetch(`/api/player/${encodeURIComponent(name)}/skin`)
            .then((r) => (r.ok ? name : null))
            .catch(() => name),
        ),
      );
      const existing = results.filter((n): n is string => n !== null);
      if (existing.length !== playerChars.length) {
        users[trimmed] = existing;
        saveUsers(users);
        playerChars = existing;
      }
    }
    const lastPlayer = getLastPlayer(trimmed);
    setChars(playerChars);
    setSelected(lastPlayer && playerChars.includes(lastPlayer) ? lastPlayer : playerChars[0] || "");
  }

  useEffect(() => {
    if (authMode === "loading") return;
    const hash = window.location.hash;
    if (hash.includes("auth_token=")) {
      const params = new URLSearchParams(hash.replace(/^#/, ""));
      const oauthToken = params.get("auth_token") || "";
      const oauthName = decodeURIComponent(params.get("auth_name") || "");
      if (oauthToken) {
        storeToken(oauthToken);
        try {
          sessionStorage.setItem("micraft_auth_display", oauthName);
        } catch {}
        window.history.replaceState(null, "", window.location.pathname + window.location.search);
        setToken(oauthToken);
        setUsername(oauthName || "player");
        goChars(oauthName || "player");
        return;
      }
    }
    const saved = getStoredToken();
    if (saved && (authMode === "local" || authMode === "oauth")) {
      setToken(saved);
      try {
        const savedName =
          sessionStorage.getItem("micraft_auth_display") || localStorage.getItem("micraft_last_user") || "";
        if (savedName) {
          setUsername(savedName);
          goChars(savedName);
        } else {
          fetch("/auth/me", { headers: { Authorization: `Bearer ${saved}` } })
            .then((r) => (r.ok ? r.json() : null))
            .then((d: { displayName: string } | null) => {
              const name = d?.displayName || "";
              if (name) {
                try {
                  sessionStorage.setItem("micraft_auth_display", name);
                } catch {}
                setUsername(name);
                goChars(name);
              } else {
                clearStoredToken();
              }
            })
            .catch(() => clearStoredToken());
        }
      } catch {
        clearStoredToken();
      }
      return;
    }
    if (authMode === "none") {
      try {
        const last = localStorage.getItem("micraft_last_user") || "";
        if (last) {
          goChars(last);
          return;
        }
      } catch {}
      setLang(getLastLang());
      setTimeout(() => usernameInputRef.current?.focus(), 50);
    }
  }, [authMode]);

  useEffect(() => {
    if (visible && step === "auth" && authMode !== "loading") {
      setTimeout(() => usernameInputRef.current?.focus(), 50);
    }
  }, [visible, step, authMode]);

  useEffect(() => {
    if (visible && step === "chars" && selected) {
      setTimeout(() => playButtonRef.current?.focus(), 50);
    }
  }, [visible, step, selected]);

  useEffect(() => {
    if (step !== "chars" || !selected) return;
    const enc = encodeURIComponent(selected);
    Promise.all([
      fetch(`/api/player/${enc}/skin`)
        .then((r) => r.json())
        .catch(() => ({ skin: "player" })),
      fetch(`/api/player/${enc}/armors`)
        .then((r) => r.json())
        .catch(() => []),
    ]).then(([skinData, armors]) => {
      setPreviewSkin(skinData.skin ?? "player");
      setPreviewArmors(Array.isArray(armors) ? armors : []);
    });
  }, [step, selected]);

  function goCreate() {
    setCreateName("");
    setCreateSkin("player");
    setCreateError("");
    setCreateWalking(true);
    setStep("create");
    setTimeout(() => createNameInputRef.current?.focus(), 50);
  }

  async function doCreate() {
    const name = createName.trim();
    if (!name) {
      setCreateError("Name required.");
      createNameInputRef.current?.focus();
      return;
    }
    if (chars.includes(name)) {
      setCreateError("Name already taken.");
      createNameInputRef.current?.focus();
      return;
    }
    await fetch(`/api/player/${encodeURIComponent(name)}/skin`, {
      method: "PUT",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({ skin: createSkin }),
    }).catch(() => {});
    const users = getUsers();
    if (!users[username]) users[username] = [];
    users[username].push(name);
    saveUsers(users);
    setChars(users[username]);
    setSelected(name);
    setPreviewSkin(createSkin);
    setPreviewArmors([]);
    setStep("chars");
  }

  async function doLocalLogin() {
    const user = username.trim();
    if (!user || !password) return;
    setAuthLoading(true);
    setAuthError("");
    try {
      const r = await fetch("/auth/login", {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({ email: user, password }),
      });
      if (!r.ok) {
        setAuthError("Invalid email or password.");
        setAuthLoading(false);
        passwordInputRef.current?.focus();
        return;
      }
      const data: { token: string; displayName: string } = await r.json();
      storeToken(data.token);
      try {
        sessionStorage.setItem("micraft_auth_display", data.displayName);
      } catch {}
      setToken(data.token);
      setUsername(data.displayName || user);
      setAuthLoading(false);
      goChars(data.displayName || user);
    } catch {
      setAuthError("Connection error. Is the server running?");
      setAuthLoading(false);
    }
  }

  function doOAuthLogin() {
    const returnUrl = window.location.origin + window.location.pathname;
    window.location.href = `/auth/oauth/start?returnUrl=${encodeURIComponent(returnUrl)}`;
  }

  function doPlay() {
    if (!selected) return;
    saveLastPlayer(username, selected);
    saveLastLang(lang);
    loginResultRef.current = username + "\t" + selected + "\t" + lang + "\t" + token;
    onHide();
  }

  function doLogout() {
    clearStoredToken();
    setToken("");
    setUsername("");
    setPassword("");
    setStep("auth");
  }

  return {
    authMode,
    step,
    setStep,
    username,
    setUsername,
    password,
    setPassword,
    authError,
    authLoading,
    lang,
    setLang,
    chars,
    selected,
    setSelected,
    previewSkin,
    previewArmors,
    previewWalking,
    setPreviewWalking,
    createName,
    setCreateName,
    createSkin,
    setCreateSkin,
    createError,
    setCreateError,
    createWalking,
    setCreateWalking,
    usernameInputRef,
    passwordInputRef,
    createNameInputRef,
    playButtonRef,
    goChars,
    goCreate,
    doCreate,
    doLocalLogin,
    doOAuthLogin,
    doPlay,
    doLogout,
  };
}
