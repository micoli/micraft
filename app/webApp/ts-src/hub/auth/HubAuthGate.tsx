import { useEffect, useState } from "react";
import { Navigate, useNavigate } from "react-router";
import {
  getStoredToken,
  getAccountEmail,
  storeToken,
  storeDisplayName,
  saveAccountEmail,
  clearStoredToken,
  clearAccountEmail,
} from "../../lib/authStorage";

type Status = "checking" | "authenticated" | "unauthenticated";

/**
 * Gates the hub SPA on a valid token — checked independently of the game (sessionStorage is
 * per-tab, so a companion opened in its own tab needs its own login even when the game is already
 * signed in in another tab). Also consumes the OAuth redirect's `#auth_token=` fragment, which
 * lands here regardless of which `/hub/*` path the server's `returnUrl` pointed at.
 */
export function HubAuthGate({ children }: { children: React.ReactNode }) {
  const navigate = useNavigate();
  const [status, setStatus] = useState<Status>("checking");

  useEffect(() => {
    const hash = window.location.hash;
    if (hash.includes("auth_token=")) {
      const params = new URLSearchParams(hash.replace(/^#/, ""));
      const token = params.get("auth_token") || "";
      const name = decodeURIComponent(params.get("auth_name") || "");
      const email = decodeURIComponent(params.get("auth_email") || "");
      if (token) {
        storeToken(token);
        storeDisplayName(name || email || "player");
        if (email) saveAccountEmail(email);
        window.history.replaceState(null, "", window.location.pathname);
        setStatus("authenticated");
        navigate("/hub/mail", { replace: true });
        return;
      }
    }

    const token = getStoredToken();
    // auth.provider=none never issues a token (server has no TokenStore at all) — the account
    // email saved by HubLoginRoute IS the login there. Only local/oauth need an actual token.
    if (!token) {
      if (getAccountEmail()) setStatus("authenticated");
      else setStatus("unauthenticated");
      return;
    }
    // /auth/me only exists when a token store is configured (local/oauth); with provider=none it
    // 404s and the stored token is trusted as-is — the WS Connect handshake is the real gate
    // either way. Only a real 401 (token store present, token rejected) clears the session.
    fetch("/auth/me", { headers: { Authorization: `Bearer ${token}` } })
      .then((r) => {
        if (r.status === 401) {
          clearStoredToken();
          setStatus("unauthenticated");
        } else {
          setStatus("authenticated");
        }
      })
      .catch(() => setStatus("authenticated"));
    // eslint-disable-next-line react-hooks/exhaustive-deps -- runs once on mount
  }, []);

  if (status === "checking") return null;
  if (status === "unauthenticated") return <Navigate to="/hub/login" replace />;
  return <>{children}</>;
}

/** Clears the stored session and sends the user back to the login screen. */
export function hubLogout(navigate: (to: string) => void) {
  clearStoredToken();
  clearAccountEmail();
  navigate("/hub/login");
}
