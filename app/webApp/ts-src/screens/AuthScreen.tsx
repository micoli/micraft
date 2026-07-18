import { useState, useEffect, useRef } from "react";
import { useNavigate } from "react-router";
import { KeyboardEvent } from "react";
import { Input } from "../primitives/Input";
import { Label } from "../primitives/Label";
import { Button } from "../primitives/Button";
import { Panel, FormField } from "../primitives/Panel";
import {
  AuthMode,
  getStoredToken,
  storeToken,
  clearStoredToken,
  getLastLang,
  saveLastLang,
  getLastUser,
  saveLastUser,
  storeDisplayName,
  getStoredDisplayName,
  getLastPlayer,
  saveAccountEmail,
} from "../lib/authStorage";

const SUPPORTED_LANGS: { code: string; label: string }[] = [
  { code: "en", label: "English" },
  { code: "fr", label: "Français" },
];

export function AuthScreen() {
  const navigate = useNavigate();
  const [authMode, setAuthMode] = useState<AuthMode>("loading");
  const [username, setUsername] = useState("");
  const [password, setPassword] = useState("");
  const [authError, setAuthError] = useState("");
  const [authLoading, setAuthLoading] = useState(false);
  const [lang, setLang] = useState(getLastLang());
  const [serverReady, setServerReady] = useState(false);
  const [autoConnecting, setAutoConnecting] = useState(false);

  const usernameInputRef = useRef<HTMLInputElement>(null);
  const passwordInputRef = useRef<HTMLInputElement>(null);

  useEffect(() => {
    fetch("/api/auth/config")
      .then((r) => r.json())
      .then((d: { provider: string }) => {
        setAuthMode((d.provider as AuthMode) || "none");
        setServerReady(true);
      })
      .catch(() => {
        setAuthMode("none");
        setServerReady(true);
      });
  }, []);

  useEffect(() => {
    if (authMode === "loading") return;
    const hash = window.location.hash;
    if (hash.includes("auth_token=")) {
      const params = new URLSearchParams(hash.replace(/^#/, ""));
      const oauthToken = params.get("auth_token") || "";
      const oauthName = decodeURIComponent(params.get("auth_name") || "");
      const oauthEmail = decodeURIComponent(params.get("auth_email") || "");
      if (oauthToken) {
        storeToken(oauthToken);
        storeDisplayName(oauthName || "player");
        if (oauthEmail) saveAccountEmail(oauthEmail);
        window.history.replaceState(null, "", window.location.pathname + window.location.search);
        saveLastUser(oauthName || "player");
        navigate("/chars");
        return;
      }
    }
    const saved = getStoredToken();
    if (saved && (authMode === "local" || authMode === "oauth")) {
      const savedName = getStoredDisplayName() || getLastUser();
      if (savedName && getLastPlayer(savedName)) {
        // eslint-disable-next-line react-hooks/set-state-in-effect
        setAutoConnecting(true);
        return;
      }
      if (savedName) {
        navigate("/chars");
      } else {
        fetch("/auth/me", { headers: { Authorization: `Bearer ${saved}` } })
          .then((r) => (r.ok ? r.json() : null))
          .then((d: { displayName: string; email?: string } | null) => {
            const name = d?.displayName || "";
            if (name) {
              storeDisplayName(name);
              if (d?.email) saveAccountEmail(d.email);
              saveLastUser(name);
              if (getLastPlayer(name)) {
                setAutoConnecting(true);
              } else {
                navigate("/chars");
              }
            } else {
              clearStoredToken();
            }
          })
          .catch(() => clearStoredToken());
      }
      return;
    }
    if (authMode === "none") {
      const last = getLastUser();
      if (last && getLastPlayer(last)) {
        setAutoConnecting(true);
        return;
      }
      if (last) {
        navigate("/chars");
        return;
      }
      setTimeout(() => usernameInputRef.current?.focus(), 50);
    }
  }, [authMode]);

  useEffect(() => {
    if (authMode !== "loading") {
      setTimeout(() => usernameInputRef.current?.focus(), 50);
    }
  }, [authMode]);

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
      const data: { token: string; displayName: string; email?: string } = await r.json();
      storeToken(data.token);
      storeDisplayName(data.displayName || user);
      saveAccountEmail(data.email || user);
      saveLastUser(data.displayName || user);
      setAuthLoading(false);
      navigate("/chars");
    } catch {
      setAuthError("Connection error. Is the server running?");
      setAuthLoading(false);
    }
  }

  function doOAuthLogin() {
    const returnUrl = window.location.origin + window.location.pathname;
    window.location.href = `/auth/oauth/start?returnUrl=${encodeURIComponent(returnUrl)}`;
  }

  async function handleNoneContinue() {
    const trimmed = username.trim();
    if (!trimmed) return;
    if (!/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(trimmed)) {
      setAuthError("Please enter a valid email address.");
      return;
    }
    setAuthLoading(true);
    setAuthError("");
    try {
      const r = await fetch("/auth/noauth-login", {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({ email: trimmed }),
      });
      if (!r.ok) {
        setAuthError("Invalid email address.");
        setAuthLoading(false);
        return;
      }
    } catch {
      setAuthError("Connection error. Is the server running?");
      setAuthLoading(false);
      return;
    }
    saveLastUser(trimmed);
    saveAccountEmail(trimmed);
    saveLastLang(lang);
    setAuthLoading(false);
    navigate("/chars");
  }

  if (authMode === "loading" || autoConnecting) {
    return (
      <div className="fixed inset-0 flex items-center justify-center bg-black/82 z-[2000]">
        <Panel className="min-w-[340px]">
          {autoConnecting ? (
            <div className="flex flex-col items-center gap-4 py-5 font-mono">
              <div className="flex gap-1">
                {[0, 1, 2].map((i) => (
                  <span
                    key={i}
                    className="w-2 h-2 rounded-full bg-blue-400 animate-bounce"
                    style={{ animationDelay: `${i * 0.15}s` }}
                  />
                ))}
              </div>
              <span className="text-white/80 text-sm">Reconnexion en cours…</span>
              <button
                className="text-xs text-white/30 hover:text-white/60 underline cursor-pointer mt-1"
                onClick={() => {
                  setAutoConnecting(false);
                  navigate("/chars");
                }}
              >
                Connexion manuelle
              </button>
            </div>
          ) : (
            <div className="text-center text-[#888] py-5">Loading…</div>
          )}
        </Panel>
      </div>
    );
  }

  return (
    <div className="fixed inset-0 flex items-center justify-center bg-black/82 z-[2000]">
      <Panel className="min-w-[340px]">
        <div className="text-[30px] font-bold text-center mb-6 text-blue-400">MiCraft</div>

        <div className="mb-4 text-center">
          <span className="text-xs font-mono px-2 py-0.5 rounded bg-[#1a1a2e] border border-blue-900/60 text-blue-400/70">
            {authMode === "local" && "Local auth"}
            {authMode === "oauth" && "OAuth"}
            {authMode === "none" && "Open server"}
          </span>
        </div>

        {!serverReady && (
          <div className="mb-4 flex items-center gap-1.5 justify-center text-[10px] text-yellow-500/70 font-mono">
            <span className="inline-block w-1.5 h-1.5 rounded-full bg-yellow-500/70 animate-pulse" />
            Connexion au serveur…
          </div>
        )}

        {authMode === "local" && (
          <div className="space-y-5">
            <FormField>
              <Label>Email</Label>
              <Input
                ref={usernameInputRef}
                type="email"
                placeholder="your@email.com"
                value={username}
                onChange={(e) => setUsername(e.target.value)}
                onKeyDown={(e: KeyboardEvent<HTMLInputElement>) => {
                  if (e.key === "Enter") passwordInputRef.current?.focus();
                }}
              />
            </FormField>
            <FormField>
              <Label>Password</Label>
              <Input
                ref={passwordInputRef}
                type="password"
                placeholder="••••••••"
                value={password}
                onChange={(e) => setPassword(e.target.value)}
                onKeyDown={(e: KeyboardEvent<HTMLInputElement>) => {
                  if (e.key === "Enter") void doLocalLogin();
                }}
              />
            </FormField>
            {authError && <div className="text-red-400 text-sm">{authError}</div>}
            <Button
              variant="blue"
              size="lg"
              className="w-full"
              onClick={() => void doLocalLogin()}
              disabled={authLoading}
            >
              {authLoading ? "Logging in…" : "Login"}
            </Button>
          </div>
        )}

        {authMode === "oauth" && (
          <div className="space-y-5">
            <div className="text-center text-[#aaa] text-sm">Sign in to play</div>
            <button
              className="w-full py-3 bg-white border border-[#ccc] rounded text-[#333] font-mono font-bold text-[15px] cursor-pointer flex items-center justify-center gap-2 hover:bg-gray-100"
              onClick={doOAuthLogin}
            >
              <span>G</span> Continue with Google
            </button>
          </div>
        )}

        {authMode === "none" && (
          <div className="space-y-5">
            <FormField>
              <Label>Email</Label>
              <Input
                ref={usernameInputRef}
                type="email"
                placeholder="your@email.com"
                value={username}
                onChange={(e) => {
                  setUsername(e.target.value);
                  setAuthError("");
                }}
                onKeyDown={(e: KeyboardEvent<HTMLInputElement>) => {
                  if (e.key === "Enter" && username.trim()) void handleNoneContinue();
                }}
              />
            </FormField>
            <FormField>
              <Label>Language</Label>
              <select
                value={lang}
                onChange={(e) => setLang(e.target.value)}
                className="w-full bg-[#111] border border-[#444] rounded px-3 py-2 text-sm text-white cursor-pointer"
              >
                {SUPPORTED_LANGS.map((l) => (
                  <option key={l.code} value={l.code}>
                    {l.label}
                  </option>
                ))}
              </select>
            </FormField>
            {authError && <div className="text-red-400 text-sm">{authError}</div>}
            <Button
              variant="blue"
              size="lg"
              className="w-full"
              onClick={() => void handleNoneContinue()}
              disabled={!username.trim() || authLoading}
            >
              {authLoading ? "Connecting…" : "Continue"}
            </Button>
          </div>
        )}
      </Panel>
    </div>
  );
}
