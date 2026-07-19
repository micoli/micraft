export function registerAutoUpdate(): void {
  try {
    const wsUrl = `ws://${location.hostname}:${location.port}/ws`;
    const ws = new WebSocket(wsUrl);

    let bootSig: string | null = null;

    ws.onmessage = (e) => {
      try {
        const msg = JSON.parse(e.data as string) as { type: string; data?: string };
        if (msg.type === "version") {
          if (bootSig === null) {
            bootSig = msg.data ?? null;
          } else if (msg.data !== bootSig) {
            window.location.reload();
          }
          return;
        }
        if (msg.type === "reload") {
          window.location.reload();
          return;
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
