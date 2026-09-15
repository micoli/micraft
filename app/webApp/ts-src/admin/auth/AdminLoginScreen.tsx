import { useEffect, useRef, useState } from "react";
import { useForm } from "@tanstack/react-form";
import { Input } from "../../primitives/Input";
import { Label } from "../../primitives/Label";
import { Button } from "../../primitives/Button";
import { Panel } from "../../primitives/Panel";
import { FormField } from "../../primitives/FormField";
import { getApiAuthConfig, postAuthLogin } from "../../generated/api/requests";
import { storeAdminToken } from "./adminTokenStorage";

type AuthMode = "loading" | "local" | "oauth";

export function AdminLoginScreen({ onAuthenticated }: { onAuthenticated: () => void }) {
  const [mode, setMode] = useState<AuthMode>("loading");
  const [requirePassword, setRequirePassword] = useState(true);
  const [error, setError] = useState("");
  const [loading, setLoading] = useState(false);
  const emailRef = useRef<HTMLInputElement>(null);

  useEffect(() => {
    getApiAuthConfig()
      .then(({ data }) => {
        setMode((data?.provider as AuthMode) || "local");
        setRequirePassword(data?.requirePassword ?? true);
      })
      .catch(() => setMode("local"));
  }, []);

  useEffect(() => {
    if (mode !== "loading") setTimeout(() => emailRef.current?.focus(), 50);
  }, [mode]);

  const form = useForm({
    defaultValues: { email: "", password: "" },
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
          setError("Invalid email or password.");
          setLoading(false);
          return;
        }
        storeAdminToken(data.token);
        onAuthenticated();
      } catch {
        setError("Connection error. Is the server running?");
        setLoading(false);
      }
    },
  });

  function doOAuthLogin() {
    const returnUrl = window.location.origin + "/admin";
    window.location.href = `/auth/oauth/start?returnUrl=${encodeURIComponent(returnUrl)}`;
  }

  return (
    <div className="fixed inset-0 flex items-center justify-center bg-[#0E1726]">
      <Panel className="min-w-[340px]">
        <div className="mb-6 text-center text-xl font-bold text-white">MiCraft Admin</div>

        {mode === "local" && (
          <form
            onSubmit={(e) => {
              e.preventDefault();
              e.stopPropagation();
              form.handleSubmit();
            }}
            className="space-y-5"
          >
            <FormField>
              <Label>Email</Label>
              <form.Field name="email">
                {(field) => (
                  <Input
                    ref={emailRef}
                    type="email"
                    placeholder="admin@example.com"
                    value={field.state.value}
                    onChange={(e) => field.handleChange(e.target.value)}
                    onBlur={field.handleBlur}
                  />
                )}
              </form.Field>
            </FormField>
            {requirePassword && (
              <FormField>
                <Label>Password</Label>
                <form.Field name="password">
                  {(field) => (
                    <Input
                      type="password"
                      placeholder="••••••••"
                      value={field.state.value}
                      onChange={(e) => field.handleChange(e.target.value)}
                      onBlur={field.handleBlur}
                    />
                  )}
                </form.Field>
              </FormField>
            )}
            {error && <div className="text-red-400 text-sm">{error}</div>}
            <Button variant="blue" size="lg" className="w-full" type="submit" disabled={loading}>
              {loading ? "Logging in…" : "Log in"}
            </Button>
          </form>
        )}

        {mode === "oauth" && (
          <div className="space-y-5">
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
