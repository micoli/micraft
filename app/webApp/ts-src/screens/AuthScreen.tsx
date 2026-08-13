import { useState, useEffect, useRef } from "react";
import { useNavigate } from "react-router";
import { KeyboardEvent } from "react";
import { useForm } from "@tanstack/react-form";
import { z } from "zod";
import { Input } from "../primitives/Input";
import { Label } from "../primitives/Label";
import { Button } from "../primitives/Button";
import { Panel } from "../primitives/Panel";
import { FormField } from "../primitives/FormField";
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

const noneEmailSchema = z
  .string()
  .refine((v) => v === "" || /^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(v), "Please enter a valid email address.");

export function AuthScreen() {
  const navigate = useNavigate();
  const [authMode, setAuthMode] = useState<AuthMode>("loading");
  const [authError, setAuthError] = useState("");
  const [authLoading, setAuthLoading] = useState(false);
  const [serverReady, setServerReady] = useState(false);

  const usernameInputRef = useRef<HTMLInputElement>(null);
  const passwordInputRef = useRef<HTMLInputElement>(null);

  const localForm = useForm({
    defaultValues: { username: "", password: "" },
    onSubmit: async ({ value }) => {
      const user = value.username.trim();
      if (!user || !value.password) return;
      setAuthLoading(true);
      setAuthError("");
      try {
        const r = await fetch("/auth/login", {
          method: "POST",
          headers: { "Content-Type": "application/json" },
          body: JSON.stringify({ email: user, password: value.password }),
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
    },
  });

  const noneForm = useForm({
    defaultValues: { username: "", lang: getLastLang() },
    onSubmit: async ({ value }) => {
      const trimmed = value.username.trim();
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
      saveLastLang(value.lang);
      setAuthLoading(false);
      navigate("/chars");
    },
  });

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
        window.mcState.intentionalDisconnect = true;
        navigate("/chars");
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
                window.mcState.intentionalDisconnect = true;
                navigate("/chars");
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
        window.mcState.intentionalDisconnect = true;
        navigate("/chars");
        return;
      }
      if (last) {
        navigate("/chars");
        return;
      }
      setTimeout(() => usernameInputRef.current?.focus(), 50);
    }
    // eslint-disable-next-line react-hooks/exhaustive-deps -- navigate/refs are stable; only authMode drives this logic
  }, [authMode]);

  useEffect(() => {
    if (authMode !== "loading") {
      setTimeout(() => usernameInputRef.current?.focus(), 50);
    }
  }, [authMode]);

  function doOAuthLogin() {
    const returnUrl = window.location.origin + window.location.pathname;
    window.location.href = `/auth/oauth/start?returnUrl=${encodeURIComponent(returnUrl)}`;
  }

  return (
    <div className="fixed inset-0 flex items-center justify-center bg-black/82 z-[2000]">
      <Panel className="min-w-[340px]">
        <div className="flex justify-center mb-6">
          <img src="/assets/splash.png" alt="MiCraft" className="max-w-[260px] w-full rounded-md" />
        </div>

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
          <form
            onSubmit={(e) => {
              e.preventDefault();
              e.stopPropagation();
              localForm.handleSubmit();
            }}
            className="space-y-5"
          >
            <FormField>
              <Label>Email</Label>
              <localForm.Field name="username">
                {(field) => (
                  <Input
                    ref={usernameInputRef}
                    type="email"
                    placeholder="your@email.com"
                    value={field.state.value}
                    onChange={(e) => field.handleChange(e.target.value)}
                    onBlur={field.handleBlur}
                    onKeyDown={(e: KeyboardEvent<HTMLInputElement>) => {
                      if (e.key === "Enter") {
                        e.preventDefault();
                        passwordInputRef.current?.focus();
                      }
                    }}
                  />
                )}
              </localForm.Field>
            </FormField>
            <FormField>
              <Label>Password</Label>
              <localForm.Field name="password">
                {(field) => (
                  <Input
                    ref={passwordInputRef}
                    type="password"
                    placeholder="••••••••"
                    value={field.state.value}
                    onChange={(e) => field.handleChange(e.target.value)}
                    onBlur={field.handleBlur}
                  />
                )}
              </localForm.Field>
            </FormField>
            {authError && <div className="text-red-400 text-sm">{authError}</div>}
            <Button variant="blue" size="lg" className="w-full" type="submit" disabled={authLoading}>
              {authLoading ? "Logging in…" : "Login"}
            </Button>
          </form>
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
          <form
            onSubmit={(e) => {
              e.preventDefault();
              e.stopPropagation();
              noneForm.handleSubmit();
            }}
            className="space-y-5"
          >
            <FormField>
              <Label>Email</Label>
              <noneForm.Field name="username" validators={{ onChange: noneEmailSchema }}>
                {(field) => (
                  <Input
                    ref={usernameInputRef}
                    type="email"
                    placeholder="your@email.com"
                    value={field.state.value}
                    onChange={(e) => {
                      field.handleChange(e.target.value);
                      setAuthError("");
                    }}
                    onBlur={field.handleBlur}
                  />
                )}
              </noneForm.Field>
            </FormField>
            <FormField>
              <Label>Language</Label>
              <noneForm.Field name="lang">
                {(field) => (
                  <select
                    value={field.state.value}
                    onChange={(e) => field.handleChange(e.target.value)}
                    className="w-full bg-[#111] border border-[#444] rounded px-3 py-2 text-sm text-white cursor-pointer"
                  >
                    {SUPPORTED_LANGS.map((l) => (
                      <option key={l.code} value={l.code}>
                        {l.label}
                      </option>
                    ))}
                  </select>
                )}
              </noneForm.Field>
            </FormField>
            {authError && <div className="text-red-400 text-sm">{authError}</div>}
            <noneForm.Subscribe selector={(state) => state.values.username}>
              {(username) => (
                <Button
                  variant="blue"
                  size="lg"
                  className="w-full"
                  type="submit"
                  disabled={!username.trim() || authLoading}
                >
                  {authLoading ? "Connecting…" : "Continue"}
                </Button>
              )}
            </noneForm.Subscribe>
          </form>
        )}
      </Panel>
    </div>
  );
}
