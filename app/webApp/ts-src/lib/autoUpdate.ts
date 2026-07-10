export function registerAutoUpdate(): void {
  try {
    const wsUrl = `ws://${location.hostname}:${location.port}/ws`;
    const ws = new WebSocket(wsUrl);

    // HMR state (webpack dev server sends hash + ok in WATCH_MODE=1)
    let initialHash: string | null = null;
    let pendingHash: string | null = null;

    ws.onopen = () => {
      // Send our asset signature so the server can detect version drift
      fetch("/api/assets/manifest", { cache: "no-cache" })
        .then((r) => r.json())
        .then((manifest: Record<string, string>) => {
          const sig = Object.entries(manifest)
            .sort(([a], [b]) => a.localeCompare(b))
            .map(([k, v]) => `${k}:${v}`)
            .join(",");
          ws.send(JSON.stringify({ type: "client-version", data: sig }));
        })
        .catch(() => {
          /* ignore */
        });
    };

    ws.onmessage = (e) => {
      try {
        const msg = JSON.parse(e.data as string) as { type: string; data?: string };
        // Server-push reload (version mismatch or explicit /api/assets/reload)
        if (msg.type === "reload") {
          if (window.mcState) {
            window.mcState.pendingVersionReload = true;
          } else {
            window.location.reload();
          }
          return;
        }
        // Webpack HMR protocol (WATCH_MODE=1 via webpack dev server at :8081)
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
      /* /ws not available in this environment */
    };
  } catch {
    /* ignore */
  }
}
