import { useEffect, useRef, useState } from "react";
import { useNavigate } from "react-router";
import { useForm } from "@tanstack/react-form";
import { Input } from "../../primitives/Input";
import { Label } from "../../primitives/Label";
import { Button } from "../../primitives/Button";
import { Panel } from "../../primitives/Panel";
import { FormField } from "../../primitives/FormField";
import { getApiAuthConfig, postAuthLogin } from "../../generated/api/requests";
import {
  AuthMode,
  storeToken,
  storeDisplayName,
  saveAccountEmail,
  saveLastUser,
  getAccountEmail,
  getLastEmail,
  saveLastEmail,
} from "../../lib/authStorage";
import { useT } from "../i18n";

/**
 * Standalone login screen for the `/hub` companion — deliberately NOT sharing `AuthScreen.tsx`
 * (the game's hot login path). See the web-companion plan's risk list: extracting a shared
 * `AuthPanel` is a mechanical-looking refactor of a screen that's easy to regress; this duplicates
 * the ~3-mode form instead, at the cost of some drift risk if the auth flow changes shape.
 */
export function HubLoginRoute() {
  const navigate = useNavigate();
  const t = useT();
  const [authMode, setAuthMode] = useState<AuthMode>("loading");
  const [requirePassword, setRequirePassword] = useState(true);
  const [error, setError] = useState("");
  const [loading, setLoading] = useState(false);
  const emailInputRef = useRef<HTMLInputElement>(null);

  const localForm = useForm({
    defaultValues: { email: getAccountEmail() || getLastEmail(), password: "" },
    onSubmit: async ({ value }) => {
      const email = value.email.trim();
      if (!email || (requirePassword && !value.password)) return;
      setLoading(true);
      setError("");
      try {
        const { data, response } = await postAuthLogin({
          body: { email, password: requirePassword ? value.password : "" },
        });
        if (!response?.ok || !data) {
          setError(t("auth.invalidCredentials"));
          setLoading(false);
          return;
        }
        storeToken(data.token);
        storeDisplayName(data.displayName || email);
        saveAccountEmail(data.email || email);
        saveLastEmail(data.email || email);
        saveLastUser(data.displayName || email);
        navigate("/hub/mail");
      } catch {
        setError(t("auth.connectionError"));
        setLoading(false);
      }
    },
  });

  useEffect(() => {
    getApiAuthConfig()
      .then(({ data }) => {
        setAuthMode((data?.provider as AuthMode) || "local");
        setRequirePassword(data?.requirePassword ?? true);
      })
      .catch(() => setAuthMode("local"));
  }, []);

  useEffect(() => {
    if (authMode !== "loading") setTimeout(() => emailInputRef.current?.focus(), 50);
  }, [authMode]);

  function doOAuthLogin() {
    const returnUrl = window.location.origin + "/hub/callback";
    window.location.href = `/auth/oauth/start?returnUrl=${encodeURIComponent(returnUrl)}`;
  }

  return (
    <div className="fixed inset-0 flex items-center justify-center bg-black/82 z-[2000]">
      <Panel className="min-w-[340px]">
        <div className="mb-6 text-center text-xl font-bold">{t("shell.title")}</div>

        <div className="mb-4 text-center">
          <span className="text-xs font-mono px-2 py-0.5 rounded bg-[#1a1a2e] border border-blue-900/60 text-blue-400/70">
            {authMode === "local" && t("auth.modeLocal")}
            {authMode === "oauth" && t("auth.modeOauth")}
          </span>
        </div>

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
              <Label>{t("auth.email")}</Label>
              <localForm.Field name="email">
                {(field) => (
                  <Input
                    ref={emailInputRef}
                    type="email"
                    placeholder="your@email.com"
                    value={field.state.value}
                    onChange={(e) => field.handleChange(e.target.value)}
                    onBlur={field.handleBlur}
                  />
                )}
              </localForm.Field>
            </FormField>
            {requirePassword && (
              <FormField>
                <Label>{t("auth.password")}</Label>
                <localForm.Field name="password">
                  {(field) => (
                    <Input
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
            {error && <div className="text-red-400 text-sm">{error}</div>}
            <Button variant="blue" size="lg" className="w-full" type="submit" disabled={loading}>
              {loading ? t("auth.loggingIn") : t("auth.login")}
            </Button>
          </form>
        )}

        {authMode === "oauth" && (
          <div className="space-y-5">
            <button
              className="w-full py-3 bg-white border border-[#ccc] rounded text-[#333] font-mono font-bold text-[15px] cursor-pointer flex items-center justify-center gap-2 hover:bg-gray-100"
              onClick={doOAuthLogin}
            >
              <span>G</span> {t("auth.continueWithGoogle")}
            </button>
          </div>
        )}
      </Panel>
    </div>
  );
}
