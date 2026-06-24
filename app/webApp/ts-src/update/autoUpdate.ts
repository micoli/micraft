export function registerAutoUpdate(): void {
  let serverVersion: string | null = null;

  async function checkServerVersion() {
    try {
      const r = await fetch("/api/version", { cache: "no-cache" });
      const { server } = (await r.json()) as { server: string };
      if (serverVersion === null) {
        serverVersion = server;
      } else if (serverVersion !== server) {
        window.location.reload();
      }
    } catch {
      /* server offline, skip */
    }
  }

  setInterval(checkServerVersion, 5000);
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
