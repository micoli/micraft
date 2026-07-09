const MC_SERVER_VERSION_KEY = "mc_server_version";

export function registerAutoUpdate(): void {
  async function checkServerVersion() {
    try {
      const r = await fetch("/api/version", { cache: "no-cache" });
      const { server } = (await r.json()) as { server: string };
      const stored = sessionStorage.getItem(MC_SERVER_VERSION_KEY);
      if (stored === null) {
        sessionStorage.setItem(MC_SERVER_VERSION_KEY, server);
      } else if (stored !== server) {
        sessionStorage.setItem(MC_SERVER_VERSION_KEY, server);
        if (window.mcState) {
          window.mcState.pendingVersionReload = true;
        } else {
          window.location.href = location.pathname + "?_v=" + server;
        }
      }
    } catch {
      /* server offline, skip */
    }
  }

  setInterval(checkServerVersion, 15000);
  checkServerVersion();

  // Listen to webpack HMR websocket: reload on client rebuild
  try {
    const wsUrl = `ws://${location.hostname}:${location.port}/ws`;
    const ws = new WebSocket(wsUrl);
    let initialHash: string | null = null;
    let pendingHash: string | null = null;

    ws.onmessage = (e) => {
      try {
        const msg = JSON.parse(e.data as string) as { type: string; data?: string };
        if (msg.type === "hash") {
          if (initialHash === null) {
            initialHash = msg.data ?? null;
          } else {
            pendingHash = msg.data ?? null;
          }
        } else if ((msg.type === "ok" || msg.type === "warnings") && pendingHash !== null) {
          window.location.reload();
        }
      } catch {
        /* ignore */
      }
    };
    ws.onerror = () => {
      /* webpack HMR not available in this environment */
    };
  } catch {
    /* ignore */
  }
}
