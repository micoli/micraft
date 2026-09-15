import { useState, useEffect, useRef } from "react";
import { useNavigate } from "react-router";
import { KeyboardEvent } from "react";
import { useForm } from "@tanstack/react-form";
import { Input } from "../primitives/Input";
import { Label } from "../primitives/Label";
import { Button } from "../primitives/Button";
import { Panel } from "../primitives/Panel";
import { FormField } from "../primitives/FormField";
import { getApiAuthConfig, postAuthLogin, getAuthMe } from "../generated/api/requests";
import {
  AuthMode,
  getStoredToken,
  storeToken,
  clearStoredToken,
  getLastUser,
  saveLastUser,
  storeDisplayName,
  getStoredDisplayName,
  getLastPlayer,
  saveAccountEmail,
} from "../lib/authStorage";

export function AuthScreen() {
  const navigate = useNavigate();
  const [authMode, setAuthMode] = useState<AuthMode>("loading");
  const [requirePassword, setRequirePassword] = useState(true);
  const [authError, setAuthError] = useState("");
  const [authLoading, setAuthLoading] = useState(false);
  const [serverReady, setServerReady] = useState(false);

  const usernameInputRef = useRef<HTMLInputElement>(null);
  const passwordInputRef = useRef<HTMLInputElement>(null);

  const localForm = useForm({
    defaultValues: { username: "", password: "" },
    onSubmit: async ({ value }) => {
      const user = value.username.trim();
      if (!user || (requirePassword && !value.password)) return;
      setAuthLoading(true);
      setAuthError("");
      try {
        const { data, response } = await postAuthLogin({
          body: { email: user, password: requirePassword ? value.password : "" },
        });
        if (!response?.ok || !data) {
          setAuthError("Invalid email or password.");
          setAuthLoading(false);
          passwordInputRef.current?.focus();
          return;
        }
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

  useEffect(() => {
    getApiAuthConfig()
      .then(({ data }) => {
        setAuthMode((data?.provider as AuthMode) || "local");
        setRequirePassword(data?.requirePassword ?? true);
        setServerReady(true);
      })
      .catch(() => {
        setAuthMode("local");
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
    if (saved) {
      const savedName = getStoredDisplayName() || getLastUser();
      if (savedName && getLastPlayer(savedName)) {
        window.mcState.intentionalDisconnect = true;
        navigate("/chars");
        return;
      }
      if (savedName) {
        navigate("/chars");
      } else {
        getAuthMe({ headers: { Authorization: `Bearer ${saved}` } })
          .then(({ data }) => {
            const name = data?.displayName || "";
            if (name) {
              storeDisplayName(name);
              if (data?.email) saveAccountEmail(data.email);
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
                      if (e.key === "Enter" && requirePassword) {
                        e.preventDefault();
                        passwordInputRef.current?.focus();
                      }
                    }}
                  />
                )}
              </localForm.Field>
            </FormField>
            {requirePassword && (
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
            )}
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
      </Panel>
    </div>
  );
}
