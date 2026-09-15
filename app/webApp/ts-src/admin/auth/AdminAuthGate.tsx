import { useEffect, useState } from "react";
import { getAdminToken, storeAdminToken, clearAdminToken } from "./adminTokenStorage";
import { AdminLoginScreen } from "./AdminLoginScreen";

type Status = "checking" | "authenticated" | "unauthenticated";

/**
 * Gates the admin SPA on a valid bearer token — `/api/admin/*` routes require the `admin`
 * permission and no longer have an unauthenticated fallback (that only ever existed under the
 * now-removed `auth.provider: none`).
 */
export function AdminAuthGate({ children }: { children: React.ReactNode }) {
  const [status, setStatus] = useState<Status>("checking");

  useEffect(() => {
    const hash = window.location.hash;
    if (hash.includes("auth_token=")) {
      const params = new URLSearchParams(hash.replace(/^#/, ""));
      const oauthToken = params.get("auth_token") || "";
      if (oauthToken) {
        storeAdminToken(oauthToken);
        window.history.replaceState(null, "", window.location.pathname);
        setStatus("authenticated");
        return;
      }
    }
    const token = getAdminToken();
    if (!token) {
      setStatus("unauthenticated");
      return;
    }
    fetch("/auth/me", { headers: { Authorization: `Bearer ${token}` } })
      .then((r) => {
        if (r.status === 401) {
          clearAdminToken();
          setStatus("unauthenticated");
        } else {
          setStatus("authenticated");
        }
      })
      .catch(() => setStatus("authenticated"));
  }, []);

  if (status === "checking") return null;
  if (status === "unauthenticated") {
    return <AdminLoginScreen onAuthenticated={() => setStatus("authenticated")} />;
  }
  return <>{children}</>;
}
