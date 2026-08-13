import { getApiAssetsManifest } from "../generated/api/requests";

export function registerAutoUpdate(): void {
  let bootSig: string | null = null;
  let hadSession = false;

  function connect(): void {
    try {
      const ws = new WebSocket(`ws://${location.hostname}:${location.port}/ws`);

      ws.onmessage = (e) => {
        try {
          const msg = JSON.parse(e.data as string) as { type: string; data?: string };
          if (msg.type === "version") {
            if (bootSig === null) {
              bootSig = msg.data ?? null;
              hadSession = true;
            } else if (msg.data !== bootSig) {
              window.location.reload();
            }
            return;
          }
          if (msg.type === "reload") {
            window.location.reload();
          }
        } catch {
          /* ignore */
        }
      };

      ws.onclose = () => {
        if (!hadSession) {
          // Never received a version — server may not support /ws; retry silently
          setTimeout(connect, 3000);
          return;
        }
        // Had an active session — server restarted; probe until it's back then reload
        const probe = (): void => {
          getApiAssetsManifest()
            .then(() => window.location.reload())
            .catch(() => setTimeout(probe, 2000));
        };
        setTimeout(probe, 1500);
      };

      ws.onerror = () => {
        /* /ws not available in this environment */
      };
    } catch {
      /* ignore */
    }
  }

  connect();
}
