import type { Camera, Engine } from "@babylonjs/core";
import { CreateScreenshot } from "@babylonjs/core/Misc/screenshotTools";

export function registerScreenshot(): Pick<McBindings, "takeScreenshot"> {
  return {
    takeScreenshot: (scene: unknown, camera: unknown, playerName: string): void => {
      const engine = window.mcState.engine as Engine | null;
      if (!engine) return;
      const size = { width: engine.getRenderWidth(), height: engine.getRenderHeight() };
      window.mc.showNotification("📸 Saving screenshot…");
      CreateScreenshot(engine, camera as Camera, size, (data) => {
        fetch(`/api/player/${encodeURIComponent(playerName)}/screenshots`, {
          method: "POST",
          headers: { "Content-Type": "application/json" },
          body: JSON.stringify({ imageData: data }),
        })
          .then((r) => {
            if (r.ok) window.mc.showNotification("✅ Screenshot saved");
            else window.mc.showNotification("❌ Screenshot failed");
          })
          .catch(() => window.mc.showNotification("❌ Screenshot failed"));
      });
    },
  };
}
