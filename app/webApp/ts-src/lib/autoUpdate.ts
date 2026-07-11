export function registerAutoUpdate(): void {
  try {
    // The service worker is the comparator: it posts ASSETS_UPDATED once its cache holds the
    // new assets, and we then hard-reload to boot them.
    if ("serviceWorker" in navigator) {
      navigator.serviceWorker.addEventListener("message", (e) => {
        if ((e.data as { type?: string })?.type === "ASSETS_UPDATED") {
          window.location.reload();
        }
      });
    }

    const wsUrl = `ws://${location.hostname}:${location.port}/ws`;
    const ws = new WebSocket(wsUrl);

    // HMR state (webpack dev server sends hash + ok in WATCH_MODE=1)
    let initialHash: string | null = null;
    let pendingHash: string | null = null;
    // Signature the page booted with, used when no service worker controls this page.
    let bootSig: string | null = null;

    const sw = () => navigator.serviceWorker?.controller ?? null;

    ws.onmessage = (e) => {
      try {
        const msg = JSON.parse(e.data as string) as { type: string; data?: string };
        // Server-published asset signature — let the SW compare it to what it cached.
        if (msg.type === "version") {
          const controller = sw();
          if (controller) {
            controller.postMessage({ type: "CHECK_VERSION", sig: msg.data });
          } else {
            if (bootSig === null) {
              bootSig = msg.data ?? null;
            } else if (msg.data !== bootSig) {
              window.location.reload();
            }
          }
          return;
        }
        // Forced reload (POST /api/assets/reload, e.g. run-assets.lock).
        if (msg.type === "reload") {
          const controller = sw();
          if (controller) {
            controller.postMessage({ type: "FORCE_UPDATE" });
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
