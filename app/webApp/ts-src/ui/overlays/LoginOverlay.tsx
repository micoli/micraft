import { useState, useEffect, useRef, KeyboardEvent } from "react";

function usePlayerModelReady(skin: string): boolean {
  const [ready, setReady] = useState(() => !!window.mc.isPlayerBbmodelReady?.(skin));
  useEffect(() => {
    setReady(!!window.mc.isPlayerBbmodelReady?.(skin));
    window.mc.initPlayerModel?.(skin);
    if (window.mc.isPlayerBbmodelReady?.(skin)) return;
    const iv = setInterval(() => {
      if (window.mc.isPlayerBbmodelReady?.(skin)) {
        setReady(true);
        clearInterval(iv);
      }
    }, 150);
    return () => clearInterval(iv);
  }, [skin]);
  return ready;
}

function useArmorModelsReady(armors: string[]): boolean {
  const [ready, setReady] = useState(false);
  useEffect(() => {
    if (armors.length === 0) {
      setReady(true);
      return;
    }
    armors.forEach((a) => window.mc.initArmorModel?.(a));
    const check = () => armors.every((a) => window.mc.isArmorModelReady?.(a));
    if (check()) {
      setReady(true);
      return;
    }
    const iv = setInterval(() => {
      if (check()) {
        setReady(true);
        clearInterval(iv);
      }
    }, 150);
    return () => clearInterval(iv);
  }, [armors.join(",")]);
  return ready;
}

function PlayerModelPreview({ skin, armors, walking }: { skin: string; armors: string[]; walking: boolean }) {
  const canvasRef = useRef<HTMLCanvasElement>(null);
  const skinReady = usePlayerModelReady(skin);
  const armorsReady = useArmorModelsReady(armors);
  const ready = skinReady && armorsReady;
  const walkingRef = useRef(walking);
  walkingRef.current = walking;

  useEffect(() => {
    if (!ready) return;
    const canvas = canvasRef.current;
    if (!canvas) return;
    const B = (window as any).BABYLON;
    if (!B) return;

    const engine = new B.Engine(canvas, true, { preserveDrawingBuffer: true, antialias: true });
    const scene = new B.Scene(engine);
    scene.clearColor = new B.Color4(0, 0, 0, 0);

    new B.ArcRotateCamera("cam", -Math.PI * 0.25, Math.PI / 3.2, 3.0, new B.Vector3(0, 0.9, 0), scene);

    const light = new B.HemisphericLight("light", new B.Vector3(1, 2, 0.5), scene);
    light.intensity = 1.1;
    light.groundColor = new B.Color3(0.2, 0.2, 0.2);

    const model = window.mc.createPlayerModelNow?.(scene, skin) ?? null;
    if (model) armors.forEach((a) => window.mc.attachArmor?.(model, a, scene));

    let angle = 0;
    scene.onBeforeRenderObservable.add(() => {
      angle += 0.015;
      if (model) window.mc.setPlayerTransform?.(model, 0, 0, 0, angle, 0, walkingRef.current);
    });

    engine.runRenderLoop(() => scene.render());

    return () => {
      if (model) window.mc.disposePlayerModel?.(model);
      engine.dispose();
    };
  }, [ready]);

  return (
    <canvas
      ref={canvasRef}
      width={160}
      height={220}
      style={{ display: "block", width: 160, height: 220, borderRadius: 6, background: "#111" }}
    />
  );
}

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

const SUPPORTED_LANGS: { code: string; label: string }[] = [
  { code: "en", label: "English" },
  { code: "fr", label: "Français" },
];

const SKINS = ["player", "askin"];

const inputStyle: React.CSSProperties = {
  width: "100%",
  boxSizing: "border-box",
  padding: "8px 10px",
  background: "#111",
  border: "1px solid #555",
  borderRadius: 4,
  color: "#eee",
  font: "15px monospace",
  outline: "none",
};
const btnPrimary: React.CSSProperties = {
  marginTop: 16,
  width: "100%",
  padding: 10,
  background: "#4a8fff",
  border: "none",
  borderRadius: 4,
  color: "#fff",
  font: "bold 15px monospace",
  cursor: "pointer",
};
const btnSecondary: React.CSSProperties = {
  marginTop: 8,
  width: "100%",
  padding: 8,
  background: "transparent",
  border: "1px solid #555",
  borderRadius: 4,
  color: "#aaa",
  font: "14px monospace",
  cursor: "pointer",
};
const btnGoogle: React.CSSProperties = {
  marginTop: 16,
  width: "100%",
  padding: 10,
  background: "#fff",
  border: "1px solid #ccc",
  borderRadius: 4,
  color: "#333",
  font: "bold 15px monospace",
  cursor: "pointer",
  display: "flex",
  alignItems: "center",
  justifyContent: "center",
  gap: 8,
};

interface Props {
  visible: boolean;
  loginResultRef: React.MutableRefObject<string>;
  onHide: () => void;
}

type AuthMode = "none" | "local" | "oauth" | "loading";
type Step = "auth" | "chars" | "create";

export function LoginOverlay({ visible, loginResultRef, onHide }: Props) {
  const [authMode, setAuthMode] = useState<AuthMode>("loading");
  const [step, setStep] = useState<Step>("auth");
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

  // Fetch auth config once on mount
  useEffect(() => {
    fetch("/api/auth/config")
      .then((r) => r.json())
      .then((d: { provider: string }) => {
        setAuthMode((d.provider as AuthMode) || "none");
      })
      .catch(() => setAuthMode("none"));
  }, []);

  // Check for OAuth callback token in URL fragment
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
    // Restore persisted token from session
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
          // No cached name — verify token and fetch identity
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
                // Token invalid or no identity — back to auth step
                clearStoredToken();
              }
            })
            .catch(() => {
              clearStoredToken();
            });
        }
      } catch {
        clearStoredToken();
      }
      return;
    }
    // none mode: restore last user directly to chars, or stay on auth step for name entry
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
    const updated = users[username];
    setChars(updated);
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

  if (!visible) return null;

  return (
    <div
      style={{
        position: "fixed",
        inset: 0,
        display: "flex",
        alignItems: "center",
        justifyContent: "center",
        background: "rgba(0,0,0,0.82)",
        zIndex: 2000,
      }}
    >
      <div
        style={{
          background: "#1a1a1a",
          border: "1px solid #444",
          borderRadius: 8,
          padding: "32px 40px",
          minWidth: 320,
          fontFamily: "monospace",
          color: "#eee",
        }}
      >
        <div style={{ fontSize: 28, fontWeight: "bold", textAlign: "center", marginBottom: 24, color: "#6af" }}>
          MiCraft
        </div>

        {authMode === "loading" && (
          <div style={{ textAlign: "center", color: "#888", padding: "20px 0" }}>Loading…</div>
        )}

        {/* Local auth step */}
        {authMode === "local" && step === "auth" && (
          <div>
            <label style={{ display: "block", fontSize: 13, color: "#aaa", marginBottom: 6 }}>Email</label>
            <input
              ref={usernameInputRef}
              style={inputStyle}
              type="email"
              placeholder="your@email.com"
              value={username}
              onChange={(e) => setUsername(e.target.value)}
              onKeyDown={(e: KeyboardEvent<HTMLInputElement>) => {
                if (e.key === "Enter") passwordInputRef.current?.focus();
              }}
            />
            <label style={{ display: "block", fontSize: 13, color: "#aaa", marginBottom: 6, marginTop: 12 }}>
              Password
            </label>
            <input
              ref={passwordInputRef}
              style={inputStyle}
              type="password"
              placeholder="••••••••"
              value={password}
              onChange={(e) => setPassword(e.target.value)}
              onKeyDown={(e: KeyboardEvent<HTMLInputElement>) => {
                if (e.key === "Enter") doLocalLogin();
              }}
            />
            {authError && <div style={{ marginTop: 8, color: "#f66", fontSize: 13 }}>{authError}</div>}
            <button style={btnPrimary} onClick={doLocalLogin} disabled={authLoading}>
              {authLoading ? "Logging in…" : "Login"}
            </button>
          </div>
        )}

        {/* OAuth auth step */}
        {authMode === "oauth" && step === "auth" && (
          <div>
            <div style={{ textAlign: "center", color: "#aaa", fontSize: 14, marginBottom: 20 }}>Sign in to play</div>
            <button style={btnGoogle} onClick={doOAuthLogin}>
              <span>G</span>
              Continue with Google
            </button>
          </div>
        )}

        {/* None mode — username + lang directly */}
        {authMode === "none" && step === "auth" && (
          <div>
            <label style={{ display: "block", fontSize: 13, color: "#aaa", marginBottom: 6 }}>Username</label>
            <input
              ref={usernameInputRef}
              style={inputStyle}
              type="text"
              placeholder="Enter your username"
              value={username}
              onChange={(e) => setUsername(e.target.value)}
              onKeyDown={(e: KeyboardEvent<HTMLInputElement>) => {
                if (e.key === "Enter") {
                  if (username.trim()) goChars(username.trim());
                }
              }}
            />
            <div style={{ marginTop: 14 }}>
              <label style={{ display: "block", fontSize: 13, color: "#aaa", marginBottom: 6 }}>Language</label>
              <select
                value={lang}
                onChange={(e) => setLang(e.target.value)}
                style={{ ...inputStyle, cursor: "pointer" }}
              >
                {SUPPORTED_LANGS.map((l) => (
                  <option key={l.code} value={l.code}>
                    {l.label}
                  </option>
                ))}
              </select>
            </div>
            <button
              style={btnPrimary}
              onClick={() => {
                if (username.trim()) goChars(username.trim());
              }}
            >
              Continue
            </button>
          </div>
        )}

        {/* Character selection step */}
        {step === "chars" && (
          <div
            style={{ display: "flex", gap: 24, alignItems: "flex-start" }}
            onKeyDown={(e: KeyboardEvent<HTMLDivElement>) => {
              if (e.key === "Enter" && selected) {
                e.stopPropagation();
                doPlay();
              }
            }}
          >
            <div style={{ minWidth: 280 }}>
              <div style={{ fontSize: 14, color: "#aaa", marginBottom: 14 }}>
                Welcome, {username}! Choose your character:
              </div>
              {(authMode === "local" || authMode === "oauth") && (
                <div style={{ marginTop: 14 }}>
                  <label style={{ display: "block", fontSize: 13, color: "#aaa", marginBottom: 6 }}>Language</label>
                  <select
                    value={lang}
                    onChange={(e) => setLang(e.target.value)}
                    style={{ ...inputStyle, cursor: "pointer" }}
                  >
                    {SUPPORTED_LANGS.map((l) => (
                      <option key={l.code} value={l.code}>
                        {l.label}
                      </option>
                    ))}
                  </select>
                </div>
              )}
              <div style={{ marginBottom: 12, marginTop: 14 }}>
                {chars.length === 0 && (
                  <div style={{ fontSize: 13, color: "#666", marginBottom: 8 }}>No characters yet.</div>
                )}
                {chars.map((name, i) => (
                  <div key={name} style={{ display: "flex", alignItems: "center", gap: 8, padding: "4px 0" }}>
                    <input
                      type="radio"
                      name="mc-char"
                      value={name}
                      id={`mc-char-${i}`}
                      checked={selected === name}
                      onChange={() => setSelected(name)}
                    />
                    <label htmlFor={`mc-char-${i}`} style={{ fontSize: 14, cursor: "pointer" }}>
                      {name}
                    </label>
                  </div>
                ))}
              </div>
              <button
                style={{ ...btnSecondary, marginTop: 4, color: "#7af", borderColor: "#3a6aaa" }}
                onClick={goCreate}
              >
                + Create new character
              </button>
              <button ref={playButtonRef} style={{ ...btnPrimary, opacity: selected ? 1 : 0.4 }} onClick={doPlay} disabled={!selected}>
                Play
              </button>
              {authMode === "local" || authMode === "oauth" ? (
                <button style={btnSecondary} onClick={doLogout}>
                  ← Log out
                </button>
              ) : (
                <button
                  style={btnSecondary}
                  onClick={() => {
                    setStep("auth");
                    setTimeout(() => usernameInputRef.current?.focus(), 50);
                  }}
                >
                  ← Back
                </button>
              )}
            </div>
            <div style={{ display: "flex", flexDirection: "column", alignItems: "center", gap: 8 }}>
              {selected ? (
                <>
                  <PlayerModelPreview
                    key={previewSkin + previewArmors.join(",")}
                    skin={previewSkin}
                    armors={previewArmors}
                    walking={previewWalking}
                  />
                  <div style={{ display: "flex", gap: 4, width: 160 }}>
                    <button
                      onClick={() => setPreviewWalking(false)}
                      style={{
                        flex: 1,
                        background: !previewWalking ? "#2a3d2a" : "#1e1e1e",
                        border: `1px solid ${!previewWalking ? "#4a7a4a" : "#333"}`,
                        borderRadius: 4,
                        color: !previewWalking ? "#7aac7a" : "#666",
                        fontFamily: "monospace",
                        fontSize: 11,
                        cursor: "pointer",
                        padding: "4px 0",
                      }}
                    >
                      Statique
                    </button>
                    <button
                      onClick={() => setPreviewWalking(true)}
                      style={{
                        flex: 1,
                        background: previewWalking ? "#2a3d2a" : "#1e1e1e",
                        border: `1px solid ${previewWalking ? "#4a7a4a" : "#333"}`,
                        borderRadius: 4,
                        color: previewWalking ? "#7aac7a" : "#666",
                        fontFamily: "monospace",
                        fontSize: 11,
                        cursor: "pointer",
                        padding: "4px 0",
                      }}
                    >
                      Marche
                    </button>
                  </div>
                </>
              ) : (
                <div
                  style={{
                    width: 160,
                    height: 220,
                    borderRadius: 6,
                    background: "#111",
                    border: "1px solid #333",
                    display: "flex",
                    alignItems: "center",
                    justifyContent: "center",
                    color: "#444",
                    fontSize: 12,
                    textAlign: "center",
                  }}
                >
                  No character selected
                </div>
              )}
            </div>
          </div>
        )}

        {/* Character creation step */}
        {step === "create" && (
          <div style={{ display: "flex", gap: 24, alignItems: "flex-start" }}>
            <div style={{ minWidth: 280 }}>
              <div style={{ fontSize: 14, color: "#aaa", marginBottom: 16 }}>New character</div>

              <label style={{ display: "block", fontSize: 13, color: "#aaa", marginBottom: 6 }}>Name</label>
              <input
                ref={createNameInputRef}
                style={inputStyle}
                type="text"
                placeholder="Character name"
                value={createName}
                onChange={(e) => {
                  setCreateName(e.target.value);
                  setCreateError("");
                }}
                onKeyDown={(e: KeyboardEvent<HTMLInputElement>) => {
                  if (e.key === "Enter") doCreate();
                }}
              />
              {createError && <div style={{ marginTop: 6, color: "#f66", fontSize: 13 }}>{createError}</div>}

              <label style={{ display: "block", fontSize: 13, color: "#aaa", marginBottom: 8, marginTop: 16 }}>
                Skin
              </label>
              <div style={{ display: "flex", alignItems: "center", gap: 8 }}>
                <button
                  onClick={() => {
                    const idx = SKINS.indexOf(createSkin);
                    setCreateSkin(SKINS[(idx - 1 + SKINS.length) % SKINS.length]);
                  }}
                  style={{
                    background: "#222",
                    border: "1px solid #555",
                    borderRadius: 4,
                    color: "#eee",
                    fontFamily: "monospace",
                    fontSize: 16,
                    cursor: "pointer",
                    padding: "4px 10px",
                  }}
                >
                  ‹
                </button>
                <div
                  style={{
                    flex: 1,
                    textAlign: "center",
                    background: "#111",
                    border: "1px solid #555",
                    borderRadius: 4,
                    padding: "6px 0",
                    fontSize: 14,
                  }}
                >
                  {createSkin}
                </div>
                <button
                  onClick={() => {
                    const idx = SKINS.indexOf(createSkin);
                    setCreateSkin(SKINS[(idx + 1) % SKINS.length]);
                  }}
                  style={{
                    background: "#222",
                    border: "1px solid #555",
                    borderRadius: 4,
                    color: "#eee",
                    fontFamily: "monospace",
                    fontSize: 16,
                    cursor: "pointer",
                    padding: "4px 10px",
                  }}
                >
                  ›
                </button>
              </div>

              <button style={btnPrimary} onClick={doCreate}>
                Create
              </button>
              <button style={btnSecondary} onClick={() => setStep("chars")}>
                ← Back
              </button>
            </div>

            <div style={{ display: "flex", flexDirection: "column", alignItems: "center", gap: 8 }}>
              <PlayerModelPreview key={createSkin} skin={createSkin} armors={[]} walking={createWalking} />
              <div style={{ display: "flex", gap: 4, width: 160 }}>
                <button
                  onClick={() => setCreateWalking(false)}
                  style={{
                    flex: 1,
                    background: !createWalking ? "#2a3d2a" : "#1e1e1e",
                    border: `1px solid ${!createWalking ? "#4a7a4a" : "#333"}`,
                    borderRadius: 4,
                    color: !createWalking ? "#7aac7a" : "#666",
                    fontFamily: "monospace",
                    fontSize: 11,
                    cursor: "pointer",
                    padding: "4px 0",
                  }}
                >
                  Statique
                </button>
                <button
                  onClick={() => setCreateWalking(true)}
                  style={{
                    flex: 1,
                    background: createWalking ? "#2a3d2a" : "#1e1e1e",
                    border: `1px solid ${createWalking ? "#4a7a4a" : "#333"}`,
                    borderRadius: 4,
                    color: createWalking ? "#7aac7a" : "#666",
                    fontFamily: "monospace",
                    fontSize: 11,
                    cursor: "pointer",
                    padding: "4px 0",
                  }}
                >
                  Marche
                </button>
              </div>
            </div>
          </div>
        )}
      </div>
    </div>
  );
}
